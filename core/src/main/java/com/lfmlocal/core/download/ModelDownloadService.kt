package com.lfmlocal.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lfmlocal.core.model.ModelCatalog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveDownload(
    val modelId: String = "",
    val modelLabel: String = "",
    val state: DownloadState = DownloadState(),
    val isRunning: Boolean = false
)

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "ModelDownloadService"
        const val CHANNEL_ID = "model_downloads"
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.lfmlocal.app.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.lfmlocal.app.CANCEL_DOWNLOAD"
        const val EXTRA_MODEL_ID = "extra_model_id"

        private val _downloadStatus = MutableStateFlow(ActiveDownload())
        val downloadStatus = _downloadStatus.asStateFlow()

        fun updateProgress(download: ActiveDownload) {
            _downloadStatus.value = download
        }

        fun startDownload(context: Context, modelId: String): Boolean {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Could not start ForegroundService (OEM or permission restriction): ${e.message}")
                false
            }
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not send cancel intent: ${e.message}")
            }
            _downloadStatus.value = ActiveDownload(isRunning = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LfmLocal:DownloadWakeLock")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock instance: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val model = ModelCatalog.byId(modelId)
                try {
                    val initialNotification = buildNotification(model.label, 0, "Starting download…")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIFICATION_ID, initialNotification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed: ${e.message}")
                }
                runDownload(model.id)
            }
            ACTION_CANCEL -> {
                cancelCurrentDownload()
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (_: Exception) {}
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun runDownload(modelId: String) {
        downloadJob?.cancel()
        val model = ModelCatalog.byId(modelId)

        try {
            wakeLock?.acquire(30 * 60 * 1000L) // 30 min max safety timeout
        } catch (_: Exception) {}

        _downloadStatus.value = ActiveDownload(model.id, model.label, DownloadState(), isRunning = true)

        downloadJob = serviceScope.launch {
            val result = ModelDownloader.download(applicationContext, model) { st ->
                _downloadStatus.value = ActiveDownload(model.id, model.label, st, isRunning = !st.done && st.error == null)
                val pct = (st.fraction * 100).toInt()
                val text = if (st.totalBytes > 0) {
                    val dlMb = st.downloadedBytes / (1024 * 1024)
                    val totMb = st.totalBytes / (1024 * 1024)
                    "$dlMb / $totMb MB ($pct%)"
                } else "Downloading…"
                updateNotification(model.label, pct, text)
            }

            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (_: Exception) {}

            result.onSuccess {
                _downloadStatus.value = ActiveDownload(model.id, model.label, DownloadState(done = true), isRunning = false)
            }.onFailure { err ->
                _downloadStatus.value = ActiveDownload(model.id, model.label, DownloadState(error = err.message), isRunning = false)
            }

            delay(1500)
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
        }
    }

    private fun cancelCurrentDownload() {
        downloadJob?.cancel()
        downloadJob = null
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        _downloadStatus.value = ActiveDownload(isRunning = false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows on-device AI model download progress"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, progress: Int, contentText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FireLM: $title")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(
                packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                    PendingIntent.getActivity(
                        this, 0, launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, ModelDownloadService::class.java).apply { action = ACTION_CANCEL },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(title: String, progress: Int, contentText: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(title, progress, contentText))
        } catch (e: Exception) {
            Log.w(TAG, "Could not update notification (permissions denied): ${e.message}")
        }
    }

    override fun onDestroy() {
        cancelCurrentDownload()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

