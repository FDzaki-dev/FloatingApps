package com.floatingapps.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnToggleService: Button

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshUi()
        }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, getString(R.string.notif_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnPermission = findViewById(R.id.btnPermission)
        btnToggleService = findViewById(R.id.btnToggleService)

        btnPermission.setOnClickListener { requestOverlayPermission() }
        btnToggleService.setOnClickListener { toggleService() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlaySettingsLauncher.launch(intent)
    }

    private fun toggleService() {
        if (FloatingBubbleService.isRunning) {
            stopService(Intent(this, FloatingBubbleService::class.java))
            refreshUi()
            return
        }
        if (!hasOverlayPermission()) {
            Toast.makeText(this, getString(R.string.need_overlay_permission), Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        startForegroundService(Intent(this, FloatingBubbleService::class.java))
        refreshUi()
    }

    private fun refreshUi() {
        val granted = hasOverlayPermission()
        btnPermission.isEnabled = !granted
        btnPermission.text = if (granted) getString(R.string.permission_granted)
            else getString(R.string.grant_permission)
        btnToggleService.isEnabled = granted
        btnToggleService.text = if (FloatingBubbleService.isRunning)
            getString(R.string.stop_bubble) else getString(R.string.start_bubble)
        tvStatus.text = if (FloatingBubbleService.isRunning)
            getString(R.string.status_active) else getString(R.string.status_inactive)
    }
}
