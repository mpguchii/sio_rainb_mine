package com.survivorio.navalmonitor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.survivorio.navalmonitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), PacketReceiverService.LogListener {

    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            throwable.printStackTrace()
            runOnUiThread {
                try {
                    AlertDialog.Builder(this)
                        .setTitle("Unexpected Error")
                        .setMessage("An error occurred in the app:\n\n${throwable.localizedMessage}\n\n${throwable.stackTraceToString().take(500)}")
                        .setPositiveButton("OK", null)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "Error: ${throwable.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnGrantOverlay.setOnClickListener {
            checkOverlayPermission()
        }

        binding.btnToggleMonitor.setOnClickListener {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, "Please grant Floating Window Permission first!", Toast.LENGTH_LONG).show()
                checkOverlayPermission()
                return@setOnClickListener
            }

            if (isServiceRunning) {
                stopMonitorServices()
            } else {
                startMonitorServices()
            }
        }
    }

    override fun onLog(message: String) {
        // Log listener
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun checkOverlayPermission() {
        if (!hasOverlayPermission()) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening permission settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Floating Window Permission already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startMonitorServices() {
        val portStr = binding.etUdpPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 8086

        try {
            PacketReceiverService.listener = this

            val overlayIntent = Intent(this, OverlayService::class.java)
            startService(overlayIntent)

            val receiverIntent = Intent(this, PacketReceiverService::class.java).apply {
                putExtra("UDP_PORT", port)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(receiverIntent)
            } else {
                startService(receiverIntent)
            }

            isServiceRunning = true
            binding.btnToggleMonitor.text = "STOP MONITOR"
            binding.tvStatus.text = "Status: Listening on UDP port $port"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            Toast.makeText(this, "Monitor started! Floating window active.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting service: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            stopMonitorServices()
        }
    }

    private fun stopMonitorServices() {
        try {
            stopService(Intent(this, OverlayService::class.java))
            stopService(Intent(this, PacketReceiverService::class.java))
            PacketReceiverService.listener = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isServiceRunning = false
        binding.btnToggleMonitor.text = "START MONITOR"
        binding.tvStatus.text = "Status: Stopped"
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
    }

    override fun onResume() {
        super.onResume()
        if (hasOverlayPermission()) {
            binding.btnGrantOverlay.isEnabled = false
            binding.btnGrantOverlay.text = "Permission OK"
        }
        if (isServiceRunning) {
            PacketReceiverService.listener = this
        }
    }

    override fun onPause() {
        super.onPause()
        PacketReceiverService.listener = null
    }
}
