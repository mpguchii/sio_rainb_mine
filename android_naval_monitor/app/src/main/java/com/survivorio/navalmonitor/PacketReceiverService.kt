package com.survivorio.navalmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class PacketReceiverService : Service() {

    interface LogListener {
        fun onLog(message: String)
    }

    companion object {
        var listener: LogListener? = null

        fun log(msg: String) {
            val formatted = "[${DxxDecoder.getCurrentTime()}] $msg"
            Handler(Looper.getMainLooper()).post {
                listener?.onLog(formatted)
            }
        }
    }

    private var socket: DatagramSocket? = null
    private var isListening = false
    private val boardState = NavalBoardState()
    private var overlayService: OverlayService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as OverlayService.LocalBinder
            overlayService = binder.getService()
            isBound = true
            log("Overlay service connected.")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            overlayService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundNotification()
            bindOverlayService()
        } catch (e: Exception) {
            log("Error onCreate: ${e.localizedMessage}")
        }
    }

    private fun bindOverlayService() {
        try {
            val intent = Intent(this, OverlayService::class.java)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            log("Error bindOverlay: ${e.localizedMessage}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("UDP_PORT", 8086) ?: 8086
        if (!isListening) {
            isListening = true
            startUdpReceiver(port)
        }
        return START_STICKY
    }

    private fun startUdpReceiver(port: Int) {
        thread {
            try {
                socket = DatagramSocket(port)
                log("UDP socket active! Listening on port $port...")
                val buffer = ByteArray(8192)

                while (isListening) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val packetLength = packet.length
                    val rawData = packet.data.copyOfRange(0, packetLength)

                    val updated = DxxDecoder.processIncomingPacket(rawData, boardState)

                    if (updated) {
                        overlayService?.updateBoardUI(boardState)
                    }
                }
            } catch (e: Exception) {
                // Socket closed
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "naval_monitor_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rainbow Mine Board",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rainbow Mine Board Active")
            .setContentText("Listening for board data on port 8086")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        isListening = false
        log("Stopping UDP server...")
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isBound) {
            try {
                unbindService(connection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBound = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
