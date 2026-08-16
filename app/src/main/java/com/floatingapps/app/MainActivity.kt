package com.floatingapps.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatingapps.app.core.capability.CapabilityManager
import com.floatingapps.app.core.capability.SystemReadiness
import com.floatingapps.app.core.ipc.BubbleStateBus
import com.floatingapps.app.core.overlay.OverlayPermissionHelper
import com.floatingapps.app.core.power.BatteryOptimizationHelper
import com.floatingapps.app.core.session.FloatingLaunchCoordinator
import com.floatingapps.app.core.session.FloatingSessionManager
import com.floatingapps.app.core.session.FloatingSessionState
import com.floatingapps.app.core.window.FloatingWindowController
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    companion object {
        private const val SHIZUKU_PERMISSION_CODE = 300
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnOverlayAction: Button
    private lateinit var tvShizukuStatus: TextView
    private lateinit var btnShizukuAction: Button
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnBatteryAction: Button
    private lateinit var btnToggleBubble: Button
    private lateinit var etSearch: EditText
    private lateinit var rvApps: RecyclerView
    private lateinit var favoritesContainer: LinearLayout
    private var allAppsCache: List<FloatableApp> = emptyList()
    private val notifiedFailedSessionKeys = mutableSetOf<String>()

    private val adapter = AppListAdapter(
        onClick = { app -> launchFloating(app) },
        onLongClick = { app -> togglePin(app) }
    )

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshUi()
        }

    private val batterySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshUi()
        }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        btnOverlayAction = findViewById(R.id.btnOverlayAction)
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
        btnShizukuAction = findViewById(R.id.btnShizukuAction)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        btnBatteryAction = findViewById(R.id.btnBatteryAction)
        btnToggleBubble = findViewById(R.id.btnToggleBubble)
        etSearch = findViewById(R.id.etSearch)
        rvApps = findViewById(R.id.rvApps)
        favoritesContainer = findViewById(R.id.favoritesContainer)

        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter
        // RecyclerView now lives inside a NestedScrollView (wrap_content) so
        // the whole home screen scrolls as one - see activity_main.xml.
        // Nested scrolling stays enabled (default) so it cooperates with the
        // parent scroll instead of fighting it.

        btnOverlayAction.setOnClickListener { requestOverlayPermission() }
        btnShizukuAction.setOnClickListener { onShizukuActionClick() }
        btnBatteryAction.setOnClickListener { onBatteryActionClick() }
        btnToggleBubble.setOnClickListener { toggleBubble() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        Shizuku.addRequestPermissionResultListener(this)
        AppListLoader.load(this) { apps ->
            allAppsCache = apps
            adapter.submitList(apps)
            bindFavorites()
        }
        bindFavorites()
        observeBubbleState()
        observeSessionState()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        ShizukuShellManager.bindIfNeeded()
        CapabilityManager.refresh(this)
        refreshUi()
    }

    /**
     * Lifecycle-safe collection of the service's running-state.
     * repeatOnLifecycle(STARTED) auto-cancels the collector in onStop() and
     * restarts it in onStart(), so this can never fire on a dead/backgrounded
     * Activity and never leaks it - the standard pattern for observing a
     * StateFlow from an Activity.
     */
    private fun observeBubbleState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BubbleStateBus.isBubbleRunning.collect {
                    btnToggleBubble.text = if (it) getString(R.string.stop_bubble)
                        else getString(R.string.start_bubble)
                }
            }
        }
    }

    /**
     * Surfaces the async verdict of [com.floatingapps.app.core.session.
     * LaunchVerification]: the launch toast at tap-time is only "the shell
     * command ran" - this warns the user, once per session, when a launch
     * that seemed to succeed turned out NOT to actually be floating (the
     * "Launched vs Actually Floating" gap from the v2.2.0 audit).
     */
    private fun observeSessionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                FloatingSessionManager.sessions.collect { sessions ->
                    sessions.values
                        .filter { it.state == FloatingSessionState.FAILED_NOT_FLOATING }
                        .forEach { session ->
                            val key = "${session.packageName}/${session.activityName}"
                            if (notifiedFailedSessionKeys.add(key)) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.launch_not_floating_warning, session.label),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
            }
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == SHIZUKU_PERMISSION_CODE) {
            runOnUiThread {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    ShizukuShellManager.bindIfNeeded()
                    Toast.makeText(this, getString(R.string.shizuku_ready), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.shizuku_denied), Toast.LENGTH_SHORT).show()
                }
                refreshUi()
            }
        }
    }

    private fun hasOverlayPermission(): Boolean = OverlayPermissionHelper.isGranted(this)

    private fun requestOverlayPermission() {
        overlaySettingsLauncher.launch(OverlayPermissionHelper.settingsIntent(this))
    }

    private fun isShizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun onShizukuActionClick() {
        when {
            !isShizukuInstalled() -> openShizukuInstallPage()
            !ShizukuShellManager.isShizukuAvailable() -> openShizukuApp()
            !ShizukuShellManager.hasPermission() -> {
                try {
                    ShizukuShellManager.requestPermission(SHIZUKU_PERMISSION_CODE)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.shizuku_not_ready), Toast.LENGTH_SHORT).show()
                }
            }
            else -> ShizukuShellManager.bindIfNeeded()
        }
    }

    private fun openShizukuInstallPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
    }

    private fun openShizukuApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            openShizukuInstallPage()
        }
    }

    /**
     * Step 3 of setup: standard Doze exemption first (guaranteed API); once
     * granted, the same button becomes an optional shortcut into the OEM
     * autostart screen for devices that need it (MIUI/ColorOS/etc). See
     * BatteryOptimizationHelper for why this is best-effort past step one.
     */
    private fun onBatteryActionClick() {
        if (!BatteryOptimizationHelper.isIgnoringOptimizations(this)) {
            try {
                batterySettingsLauncher.launch(BatteryOptimizationHelper.requestExemptionIntent(this))
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.oem_not_found), Toast.LENGTH_SHORT).show()
            }
        } else {
            val opened = BatteryOptimizationHelper.tryOpenOemAutostartSettings(this)
            if (!opened) {
                Toast.makeText(this, getString(R.string.oem_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleBubble() {
        if (FloatingBubbleService.isRunning) {
            stopService(Intent(this, FloatingBubbleService::class.java))
        } else {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, getString(R.string.need_overlay_permission), Toast.LENGTH_SHORT).show()
                requestOverlayPermission()
                return
            }
            startForegroundService(Intent(this, FloatingBubbleService::class.java))
        }
        refreshUi()
    }

    private fun launchFloating(app: FloatableApp) {
        if (CapabilityManager.snapshot.value.readiness == SystemReadiness.UNSUPPORTED) {
            Toast.makeText(this, getString(R.string.freeform_unsupported_warning), Toast.LENGTH_LONG).show()
        }
        when (val outcome = FloatingLaunchCoordinator.launch(app, lifecycleScope)) {
            is FloatingLaunchCoordinator.LaunchOutcome.NotReady ->
                Toast.makeText(this, getString(R.string.shizuku_not_ready), Toast.LENGTH_SHORT).show()
            is FloatingLaunchCoordinator.LaunchOutcome.CommandFailed ->
                Toast.makeText(this, getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
            is FloatingLaunchCoordinator.LaunchOutcome.CommandSucceeded ->
                Toast.makeText(this, getString(R.string.launch_success, app.label), Toast.LENGTH_SHORT).show()
            is FloatingLaunchCoordinator.LaunchOutcome.BringingToFront ->
                Toast.makeText(this, getString(R.string.bringing_to_front, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    /** Long-press on an already-floating Favorite slot: close it via
     *  core.window.FloatingWindowController (P0 #3 close slice). No-op
     *  (silent) for a favorite that isn't currently a live session, since
     *  FavoritesRowBinder can't know session state per-slot yet - see
     *  PROJECT_STATE.md for why that visual distinction is deferred P1
     *  polish rather than bundled into this batch. */
    private fun closeFloatingFavorite(app: FloatableApp) {
        if (FloatingSessionManager.sessionForApp(app) == null) return
        val closed = FloatingWindowController.close(app)
        Toast.makeText(
            this,
            if (closed) getString(R.string.closed_success, app.label) else getString(R.string.closed_failed, app.label),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun bindFavorites() {
        FavoritesRowBinder.bind(
            container = favoritesContainer,
            allApps = allAppsCache,
            onSlotTap = { app -> launchFloating(app) },
            onEmptySlotTap = {
                Toast.makeText(this, getString(R.string.favorites_hint), Toast.LENGTH_SHORT).show()
            },
            onSlotLongClick = { app -> closeFloatingFavorite(app) }
        )
    }

    private fun togglePin(app: FloatableApp) {
        val added = FavoritesManager.toggleFavorite(this, app)
        Toast.makeText(
            this,
            if (added) getString(R.string.pinned_added, app.label) else getString(R.string.pinned_removed, app.label),
            Toast.LENGTH_SHORT
        ).show()
        bindFavorites()
    }

    private fun refreshUi() {
        val overlayGranted = hasOverlayPermission()
        btnOverlayAction.isEnabled = !overlayGranted
        btnOverlayAction.text = if (overlayGranted) getString(R.string.permission_granted)
            else getString(R.string.grant_permission)
        tvOverlayStatus.text = if (overlayGranted) getString(R.string.overlay_status_ok)
            else getString(R.string.overlay_status_pending)

        val shizukuReady = ShizukuShellManager.isReady()
        val shizukuPermitted = ShizukuShellManager.hasPermission()
        val shizukuRunning = ShizukuShellManager.isShizukuAvailable()
        val shizukuInstalled = isShizukuInstalled()

        tvShizukuStatus.text = when {
            !shizukuInstalled -> getString(R.string.shizuku_status_not_installed)
            !shizukuRunning -> getString(R.string.shizuku_status_not_running)
            !shizukuPermitted -> getString(R.string.shizuku_status_no_permission)
            shizukuReady -> getString(R.string.shizuku_status_ready)
            else -> getString(R.string.shizuku_status_connecting)
        }
        btnShizukuAction.text = when {
            !shizukuInstalled -> getString(R.string.shizuku_action_install)
            !shizukuRunning -> getString(R.string.shizuku_action_open)
            !shizukuPermitted -> getString(R.string.shizuku_action_permission)
            else -> getString(R.string.shizuku_action_ready)
        }
        btnShizukuAction.isEnabled = !shizukuReady

        val batteryExempt = BatteryOptimizationHelper.isIgnoringOptimizations(this)
        tvBatteryStatus.text = if (batteryExempt) getString(R.string.battery_status_ok)
            else getString(R.string.battery_status_pending)
        btnBatteryAction.text = if (batteryExempt) getString(R.string.battery_action_oem)
            else getString(R.string.battery_action_request)

        btnToggleBubble.text = if (FloatingBubbleService.isRunning)
            getString(R.string.stop_bubble) else getString(R.string.start_bubble)

        CapabilityManager.refresh(this)
        val readyToFloat = CapabilityManager.snapshot.value.readiness != SystemReadiness.ACTION_REQUIRED
        rvApps.alpha = if (readyToFloat) 1f else 0.4f
        etSearch.isEnabled = true
    }
}
