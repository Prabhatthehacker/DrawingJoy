package com.mom.privatedrawing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat

class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DENSITY = "density"
        const val EXTRA_OUTPUT_PATH = "output_path"

        const val ACTION_STOP = "com.mom.privatedrawing.ACTION_STOP_RECORDING"

        var isRecording = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputPath: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        val width = intent?.getIntExtra(EXTRA_WIDTH, 1080) ?: 1080
        val height = intent?.getIntExtra(EXTRA_HEIGHT, 1920) ?: 1920
        val density = intent?.getIntExtra(EXTRA_DENSITY, DisplayMetrics.DENSITY_DEFAULT) ?: DisplayMetrics.DENSITY_DEFAULT
        outputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH)

        if (resultData == null || outputPath == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        startRecording(width, height, density)
        return START_STICKY
    }

    private fun startRecording(width: Int, height: Int, density: Int) {
        val recorder = MediaRecorder()
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setVideoSize(width, height)
        recorder.setVideoEncodingBitRate(6_000_000)
        recorder.setVideoFrameRate(30)
        recorder.setOutputFile(outputPath)
        recorder.prepare()

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PrivateDrawingRecording",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface, null, null
        )

        recorder.start()
        mediaRecorder = recorder
        isRecording = true
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (_: Exception) {
            // Recorder may already be stopped; ignore.
        }
        mediaRecorder?.release()
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        isRecording = false
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Drawing Recording", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording your drawing")
            .setContentText("Tap Stop in the app when you're done.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
