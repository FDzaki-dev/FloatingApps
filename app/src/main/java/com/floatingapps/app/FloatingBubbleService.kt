package com.floatingapps.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatingapps.app.core.ipc.BubbleStateBus
import com.floatingapps.app.core.overlay.OverlayWindowController
import com.floatingapps.app.core.session.FloatingLaunchCoordinator
import com.floatingapps.app.core.touch.FloatingDragTouchListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground overlay service: draggable access bubble + app-picker panel.
 *
 * Anti-crash / production-hardening notes (see PROJECT_STATE.md for the
 * full write-up per requirement):
 * - All WindowManager add/update/remove calls go through
 *   [OverlayWindowController] - never called directly - so a revoked overlay
 *   permission or OEM BadTokenException never crashes the process.
 * - Drag, boundary-clamping and snap-to-edge are delegated to
 *   [FloatingDragTouchListener] (reusable, unit-testable in isolation).
 * - [onConfigurationChanged] re-clamps the bubble into the new screen
 *   bounds on every rotation/foldable-state change instead of ignoring it.
 * - A dedicated [serviceScope] (Main + SupervisorJob) is cancelled in
 *   [onDestroy] so no coroutine can ever outlive the service and leak it.
 * - [onTaskRemoved] is overridden as a deliberate no-op: this service is
 *   independent of MainActivity's task, and swiping the app away from
 *   Recents must NOT stop the bubble. Combined with the battery-optimization
 *   exemption flow in MainActivity, this is the app's "process survival"
 *   strategy on stock Android; aggressive OEM task killers (MIUI/ColorOS/
 *   FuntouchOS) are a platform limitation no app-level code can fully
 *   defeat without root - see PROJECT_STATE.md "Keterbatasan Jujur".
 */
class FloatingBubbleService : Service() {

    companion object {
        var isRunning: Boolean = false
            private set
        private const val CHANNEL_ID = "floating_apps_channel"
        private const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.floatingapps.app.ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var overlayController: OverlayWindowController
    private lateinit var windowManager: WindowManager

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var dragListener: FloatingDragTouchListener? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var pickerAdapter: AppListAdapter? = null
    private var allAppsCache: List<FloatableApp> = emptyList()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        BubbleStateBus.setBubbleRunning(true)
        overlayController = OverlayWindowController(this)
        windowManager = overlayController.manager()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        ShizukuShellManager.bindIfNeeded()
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY: if the process is killed under memory pressure, the
        // system recreates it with a null intent and onCreate() re-adds the
        // bubble - this is the service's process-survival recovery path.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Deliberate no-op - see class doc. Do NOT call stopSelf() here.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / foldable-state / display-size change: re-clamp the
        // bubble so it never ends up parked off the new visible bounds.
        bubbleView?.let { view -> dragListener?.onScreenBoundsChanged(view) }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        BubbleStateBus.setBubbleRunning(false)
        removePanel()
        removeBubble()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = getString(R.string.notif_channel_desc)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_running_text))
            .setSmallIcon(R.drawable.ic_bubble)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, getString(R.string.stop_bubble), stopPending)
            .build()
    }

    private fun addBubble() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingApps)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.layout_bubble, null)
        bubbleView = view

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.x = 0
        bubbleParams.y = 200

        if (!overlayController.add(view, bubbleParams)) {
            // Overlay permission missing/revoked - nothing to show, stop cleanly.
            stopSelf()
            return
        }

        val listener = FloatingDragTouchListener(
            context = this,
            windowManager = windowManager,
            params = bubbleParams,
            onLayoutChanged = {},
            onTap = { togglePanel() }
        )
        dragListener = listener
        view.setOnTouchListener(listener)
    }

    private fun removeBubble() {
        dragListener?.release()
        dragListener = null
        overlayController.remove(bubbleView)
        bubbleView = null
    }

    private fun togglePanel() {
        if (panelView != null) {
            removePanel()
        } else {
            addPanel()
        }
    }

    private fun addPanel() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingApps)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.layout_bubble_expanded, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bubbleParams.x
        params.y = bubbleParams.y + 120

        val favoritesContainer = view.findViewById<LinearLayout>(R.id.favoritesContainerPanel)
        val rv = view.findViewById<RecyclerView>(R.id.rvPickerApps)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = AppListAdapter(
            onClick = { app -> launchFloating(app) },
            onLongClick = { app -> togglePin(app, favoritesContainer) }
        )
        pickerAdapter = adapter
        rv.adapter = adapter
        AppListLoader.load(this) { apps ->
            allAppsCache = apps
            adapter.submitList(apps)
            bindFavorites(favoritesContainer)
        }
        bindFavorites(favoritesContainer)

        val etSearch = view.findViewById<EditText>(R.id.etPickerSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<ImageView>(R.id.btnClosePanel).setOnClickListener {
            removePanel()
        }

        if (overlayController.add(view, params)) {
            panelView = view
        }
        // If add() failed (permission revoked concurrently), we simply don't
        // show the panel - no crash, no dangling reference since panelView
        // stays null.
    }

    private fun bindFavorites(container: LinearLayout) {
        FavoritesRowBinder.bind(
            container = container,
            allApps = allAppsCache,
            onSlotTap = { app -> launchFloating(app) },
            onEmptySlotTap = {
                Toast.makeText(this, getString(R.string.favorites_hint), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun togglePin(app: FloatableApp, favoritesContainer: LinearLayout) {
        val added = FavoritesManager.toggleFavorite(this, app)
        Toast.makeText(
            this,
            if (added) getString(R.string.pinned_added, app.label) else getString(R.string.pinned_removed, app.label),
            Toast.LENGTH_SHORT
        ).show()
        bindFavorites(favoritesContainer)
    }

    private fun launchFloating(app: FloatableApp) {
        when (val outcome = FloatingLaunchCoordinator.launch(app, serviceScope)) {
            is FloatingLaunchCoordinator.LaunchOutcome.NotReady ->
                Toast.makeText(this, getString(R.string.shizuku_not_ready), Toast.LENGTH_SHORT).show()
            is FloatingLaunchCoordinator.LaunchOutcome.CommandFailed ->
                Toast.makeText(this, getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
            is FloatingLaunchCoordinator.LaunchOutcome.CommandSucceeded ->
                Toast.makeText(this, getString(R.string.launch_success, app.label), Toast.LENGTH_SHORT).show()
        }
        removePanel()
    }

    private fun removePanel() {
        overlayController.remove(panelView)
        panelView = null
        pickerAdapter = null
    }
}
