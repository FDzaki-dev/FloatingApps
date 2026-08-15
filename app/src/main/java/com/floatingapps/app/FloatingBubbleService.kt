package com.floatingapps.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingBubbleService : Service() {

    companion object {
        var isRunning: Boolean = false
            private set
        private const val CHANNEL_ID = "floating_apps_channel"
        private const val NOTIF_ID = 1001
        private const val PREFS_NAME = "floating_notes"
        private const val PREF_NOTE_KEY = "note_text"
        const val ACTION_STOP = "com.floatingapps.app.ACTION_STOP"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removePanel()
        removeBubble()
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
        val inflater = LayoutInflater.from(this)
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

        try {
            windowManager.addView(view, bubbleParams)
        } catch (e: Exception) {
            stopSelf()
            return
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        isDragging = true
                    }
                    bubbleParams.x = initialX + dx.toInt()
                    bubbleParams.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(view, bubbleParams)
                    } catch (e: Exception) {
                        // window may have been removed concurrently; ignore safely
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        togglePanel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeBubble() {
        try {
            bubbleView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // view already detached, ignore safely
        }
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
        val inflater = LayoutInflater.from(this)
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

        val editText = view.findViewById<EditText>(R.id.etNote)
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        editText.setText(prefs.getString(PREF_NOTE_KEY, ""))

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            prefs.edit().putString(PREF_NOTE_KEY, editText.text.toString()).apply()
            Toast.makeText(this, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
        }

        view.findViewById<ImageView>(R.id.btnClosePanel).setOnClickListener {
            removePanel()
        }

        try {
            windowManager.addView(view, params)
            panelView = view
        } catch (e: Exception) {
            // overlay permission may have been revoked; skip silently
        }
    }

    private fun removePanel() {
        try {
            panelView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // panel already detached, ignore safely
        }
        panelView = null
    }
}
