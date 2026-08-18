package com.survivorio.navalmonitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import com.survivorio.navalmonitor.databinding.OverlayBoardBinding

class OverlayService : Service() {

    private val binder = LocalBinder()
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var binding: OverlayBoardBinding? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMinimized = false

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlayView()
    }

    private fun setupOverlayView() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                showToast("Floating Window Permission not granted!")
                stopSelf()
                return
            }

            binding = OverlayBoardBinding.inflate(LayoutInflater.from(this))
            overlayView = binding?.root

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 150
            }

            // Drag floating window
            binding?.tvBoardTitle?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(overlayView, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    }
                    else -> false
                }
            }

            // Minimize / Expand
            binding?.btnMinimize?.setOnClickListener {
                isMinimized = !isMinimized
                binding?.layoutBoardContainer?.visibility = if (isMinimized) View.GONE else View.VISIBLE
                binding?.btnMinimize?.text = if (isMinimized) "+" else "—"
            }

            // Close
            binding?.btnClose?.setOnClickListener {
                stopService(Intent(this@OverlayService, PacketReceiverService::class.java))
                stopSelf()
            }

            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error creating floating window: ${e.localizedMessage}")
            stopSelf()
        }
    }

    fun updateBoardUI(state: NavalBoardState) {
        val binding = binding ?: return
        binding.root.post {
            try {
                binding.tvBoardTitle.text = "Board"

                val grid = binding.gridNavalBoard
                grid.removeAllViews()
                if (state.rows == 0 || state.cols == 0) return@post

                grid.rowCount = state.rows
                grid.columnCount = state.cols

                val selectedSet = state.selected.toSet()

                for (r in 0 until state.rows) {
                    for (c in 0 until state.cols) {
                        val cellId = r * state.cols + c
                        val cellValue = state.seed.getOrNull(cellId)?.toInt() ?: 0
                        val isSelected = selectedSet.contains(cellId)

                        val cellView = TextView(this).apply {
                            text = ""
                            textSize = 14f
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            minWidth = 52
                            minHeight = 44
                            setPadding(36, 12, 36, 12)

                            val bgColor = when {
                                cellValue > 0 && isSelected -> Color.parseColor("#35A853") // Green (Hit)
                                cellValue > 0 -> Color.parseColor("#F39C12") // Orange (Piece)
                                isSelected -> Color.parseColor("#5DADE2") // Blue (Selected empty)
                                else -> Color.parseColor("#27313A") // Dark Gray (Hidden)
                            }
                            setBackgroundColor(bgColor)
                        }

                        val param = GridLayout.LayoutParams().apply {
                            rowSpec = GridLayout.spec(r)
                            columnSpec = GridLayout.spec(c)
                            setMargins(6, 6, 6, 6)
                        }
                        cellView.layoutParams = param
                        grid.addView(cellView)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }
}
