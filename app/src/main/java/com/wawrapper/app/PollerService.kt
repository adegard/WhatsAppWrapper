package com.wawrapper.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PollerService : Service() {

    companion object {
        const val ACTION_STOP = "com.wawrapper.app.STOP_POLLER"
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_MESSAGES_BG = "unread_messages_bg"
        const val NOTIF_ID_MONITOR = 1002
        const val NOTIF_ID_MESSAGES_BG = 1003

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var lastCount = -1

    private val pollTask = object : Runnable {
        override fun run() {
            val view = webView ?: return
            view.evaluateJavascript(MainActivity.stealthJs(false), null)
            view.evaluateJavascript("(document.title || '')") { value ->
                val count = Regex("\\((\\d+)\\)").find(value)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (count != lastCount) {
                    lastCount = count
                    showMessageNotification(count)
                    refreshMonitorText()
                }
            }
            handler.postDelayed(this, pollIntervalMs())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        isRunning = true
        createChannels()
        setupWebView()
        startForeground(NOTIF_ID_MONITOR, buildMonitorNotification())
        handler.postDelayed(pollTask, 30_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        webView = null
        NotificationManagerCompat.from(this).cancel(NOTIF_ID_MESSAGES_BG)
        NotificationManagerCompat.from(this).cancel(NOTIF_ID_MONITOR)
        isRunning = false
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val view = WebView(applicationContext)

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = MainActivity.desktopUa()
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
        }
        view.settings.blockNetworkImage = liteMode()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(view, false)

        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? =
                if (AdBlocker.shouldBlock(request.url.toString(), blockTrackers(), liteMode())) {
                    AdBlocker.emptyResponse()
                } else {
                    null
                }
        }

        try {
            ServiceWorkerController.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(
                        request: WebResourceRequest
                    ): WebResourceResponse? =
                        if (AdBlocker.shouldBlock(
                                request.url.toString(),
                                blockTrackers(),
                                liteMode()
                            )
                        ) {
                            AdBlocker.emptyResponse()
                        } else {
                            null
                        }
                }
            )
        } catch (_: Exception) {
        }

        view.loadUrl(MainActivity.HOME_URL, MainActivity.waHeaders(false))
        webView = view
    }

    private fun pollIntervalMs(): Long =
        prefs.getInt(MainActivity.KEY_POLL_MINUTES, 5) * 60_000L

    private fun blockTrackers() = prefs.getBoolean(MainActivity.KEY_BLOCK_TRACKERS, true)

    private fun liteMode() = prefs.getBoolean(MainActivity.KEY_LITE_MODE, false)

    private fun showMessageNotification(count: Int) {
        val manager = NotificationManagerCompat.from(this)
        if (count <= 0) {
            manager.cancel(NOTIF_ID_MESSAGES_BG)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val text = resources.getQuantityString(R.plurals.unread_messages, count, count)
        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES_BG)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(activityPendingIntent())
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(NOTIF_ID_MESSAGES_BG, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun buildMonitorNotification(): Notification {
        val minutes = prefs.getInt(MainActivity.KEY_POLL_MINUTES, 5)
        return NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.monitoring_every, minutes))
            .setContentIntent(activityPendingIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun refreshMonitorText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID_MONITOR, buildMonitorNotification())
        } catch (_: SecurityException) {
        }
    }

    private fun activityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MONITOR,
                    getString(R.string.channel_monitor),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MESSAGES_BG,
                    getString(R.string.channel_unread),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
