package com.wawrapper.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME_URL = "https://web.whatsapp.com/"
        const val PREFS_NAME = "wa_wrapper_prefs"
        const val KEY_BLOCK_TRACKERS = "block_trackers"
        const val KEY_LITE_MODE = "lite_mode"
        const val KEY_BG_ALERTS = "bg_alerts"
        const val KEY_POLL_MINUTES = "poll_minutes"
        const val KEY_PHONE_FIT = "phone_fit"
        const val KEY_MOBILE_LAYOUT = "mobile_layout"

        private const val PHONE_FIT_JS =
            """(function(){
            var side=document.getElementById('side');
            if(!side||!document.body){return}
            if(!document.getElementById('wawrap-style')){
                var st=document.createElement('style');
                st.id='wawrap-style';
                st.textContent='html,body{overflow-x:hidden!important;width:100%!important}'+
                    '#app{max-width:100vw!important;width:100%!important}'+
                    '#app div{min-width:0!important}'+
                    '#main{min-width:0!important;width:auto!important;flex:1 1 auto!important}'+
                    'img,video{max-width:100%!important;height:auto!important}'+
                    '#side{zoom:.44!important;width:auto!important;min-width:0!important;'+
                    'max-width:none!important;background:#0b141a}'+
                    '#side ::-webkit-scrollbar{display:none}';
                document.head.appendChild(st);
            }
            var p=side.parentElement;
            if(p){
                p.style.width='82px';
                p.style.minWidth='82px';
                p.style.maxWidth='82px';
                p.style.overflow='hidden';
                p.style.flexShrink='0';
                p.style.background='#0b141a';
            }
            if(!window.__wawrapRailHidden){
                var names=['Chats','Status','Channels','Communities','Settings','Profile'];
                var anchor=null;
                for(var i=0;i<names.length&&!anchor;i++){
                    anchor=document.querySelector('button[aria-label="'+names[i]+'"]');
                }
                if(anchor){
                    var rail=anchor;
                    while(rail.parentElement&&rail.parentElement!==document.body){
                        var rw=rail.parentElement.getBoundingClientRect().width;
                        if(rw>0&&rw<130){
                            rail=rail.parentElement;
                        }else{
                            break;
                        }
                    }
                    var rr=rail.getBoundingClientRect();
                    if(rr.width>0&&rr.width<130&&rr.height>window.innerHeight*0.5){
                        rail.style.display='none';
                        window.__wawrapRailHidden=true;
                    }else{
                        window.__wawrapRailHidden=true;
                    }
                }
            }
        })();"""
        const val CHANNEL_UNREAD = "unread_messages"
        const val NOTIF_ID_UNREAD = 1001

        private fun realWebViewVersion(): String {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WebView.getCurrentWebViewPackage()?.versionName
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }?.takeIf { Regex("^\\d+(\\.\\d+){2,3}$").matches(it) } ?: "126.0.0.0"
        }

        fun desktopUa(): String {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/${realWebViewVersion()} Safari/537.36"
        }

        fun mobileUa(context: Context): String {
            return WebSettings.getDefaultUserAgent(context)
                .replace("; wv)", ")")
                .replace("; wv )", ")")
        }

        fun waHeaders(isMobile: Boolean): Map<String, String> {
            val v = realWebViewVersion()
            val brands = "\"Not.A/Brand\";v=\"24\", \"Chromium\";v=\"$v\", \"Google Chrome\";v=\"$v\""
            return if (isMobile) {
                mapOf(
                    "sec-ch-ua" to brands,
                    "sec-ch-ua-mobile" to "?1",
                    "sec-ch-ua-platform" to "\"Android\""
                )
            } else {
                mapOf(
                    "sec-ch-ua" to brands,
                    "sec-ch-ua-mobile" to "?0",
                    "sec-ch-ua-platform" to "\"Windows\""
                )
            }
        }

        fun stealthJs(isMobile: Boolean): String {
            val ver = realWebViewVersion()
            val platform = if (isMobile) "Android" else "Windows"
            val platformVersion = if (isMobile) "\"${Build.VERSION.RELEASE}.0.0\"" else "\"13.0.0\""
            val model = if (isMobile) "\"${Build.MODEL}\"" else "\"\""
            val arch = if (isMobile) "arm" else "x86"
            val mobile = if (isMobile) "true" else "false"
            return """(function(){
            if(window.__wawrapStealth){return}
            window.__wawrapStealth=1;
            try{Object.defineProperty(navigator,'webdriver',{get:function(){return false}})}catch(e){}
            try{
                var V='$ver';
                var B=[{brand:'Not.A/Brand',version:'24'},{brand:'Chromium',version:V},{brand:'Google Chrome',version:V}];
                var uad={brands:B,mobile:$mobile,platform:'$platform'};
                uad.toJSON=function(){return{brands:B,mobile:$mobile,platform:'$platform'}};
                uad.getHighEntropyValues=function(h){
                    var o={architecture:'$arch',bitness:'64',model:$model,platform:'$platform',platformVersion:$platformVersion,uaFullVersion:V};
                    var r={};
                    for(var k in o){if(h.indexOf(k)>=0){r[k]=o[k]}}
                    return Promise.resolve(r);
                };
                Object.defineProperty(Object.getPrototypeOf(navigator),'userAgentData',{get:function(){return uad},configurable:true});
            }catch(e){}
        })();"""
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var unreadCount = 0
    private var inForeground = true

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileUploadCallback
            fileUploadCallback = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
        }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val unreadPoller = object : Runnable {
        override fun run() {
            if (isDestroyed || isFinishing) return
            webView.evaluateJavascript(stealthJs(mobileLayout()), null)
            if (phoneFit() && !mobileLayout()) {
                webView.evaluateJavascript(PHONE_FIT_JS, null)
            }
            webView.evaluateJavascript("(document.title || '')") { value ->
                val count = Regex("\\((\\d+)\\)").find(value)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (count != unreadCount) {
                    unreadCount = count
                    refreshUnreadUi()
                }
            }
            webView.postDelayed(this, 4000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        progressBar = findViewById(R.id.progress_bar)
        webView = findViewById(R.id.web_view)
        webView.setBackgroundColor(Color.parseColor("#0B141A"))

        setupWebView()
        setupBackHandler()
        createNotificationChannel()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadHome()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = if (mobileLayout()) mobileUa(this@MainActivity) else desktopUa()
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = handleUri(request.url)

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? =
                if (AdBlocker.shouldBlock(request.url.toString(), blockTrackers(), liteMode())) {
                    AdBlocker.emptyResponse()
                } else {
                    null
                }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress in 1..99) ProgressBar.VISIBLE else ProgressBar.GONE
            }

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = callback
                return try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    fileUploadCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            download(url, contentDisposition, mimeType)
        }

        try {
            val workerController = ServiceWorkerController.getInstance()
            workerController.setServiceWorkerClient(object : ServiceWorkerClient() {
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
                    })
        } catch (_: Exception) {
        }
    }

    private fun handleUri(uri: Uri): Boolean {
        val url = uri.toString()
        val host = uri.host?.lowercase()
        return when {
            url.startsWith("mailto:") -> {
                safeStart(Intent(Intent.ACTION_SENDTO, uri))
                true
            }
            url.startsWith("tel:") -> {
                safeStart(Intent(Intent.ACTION_DIAL, uri))
                true
            }
            host != null &&
                (host == "whatsapp.com" || host.endsWith(".whatsapp.com") ||
                    host == "whatsapp.net" || host.endsWith(".whatsapp.net")) -> false
            url.startsWith("http") -> {
                safeStart(Intent(Intent.ACTION_VIEW, uri))
                true
            }
            else -> false
        }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun download(url: String, contentDisposition: String?, mimeType: String?) {
        try {
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, getString(R.string.download_started, name), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    private fun blockTrackers() = prefs.getBoolean(KEY_BLOCK_TRACKERS, true)

    private fun liteMode() = prefs.getBoolean(KEY_LITE_MODE, false)

    private fun phoneFit() = prefs.getBoolean(KEY_PHONE_FIT, true)

    private fun mobileLayout() = prefs.getBoolean(KEY_MOBILE_LAYOUT, true)

    private fun refreshUnreadUi() {
        supportActionBar?.subtitle =
            if (unreadCount > 0) {
                resources.getQuantityString(R.plurals.unread_messages, unreadCount, unreadCount)
            } else {
                ""
            }

        if (inForeground || unreadCount <= 0 || PollerService.isRunning) {
            NotificationManagerCompat.from(this).cancel(NOTIF_ID_UNREAD)
        } else {
            postUnreadNotification()
        }
    }

    private fun postUnreadNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val text = resources.getQuantityString(R.plurals.unread_messages, unreadCount, unreadCount)
        val notification = NotificationCompat.Builder(this, CHANNEL_UNREAD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID_UNREAD, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_UNREAD,
                getString(R.string.channel_unread),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.channel_unread_desc)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun loadHome() {
        webView.loadUrl(webView.url ?: HOME_URL, waHeaders(mobileLayout()))
    }

    private fun startPoller() {
        ContextCompat.startForegroundService(this, Intent(this, PollerService::class.java))
    }

    private fun showFrequencyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "5"
            setText(prefs.getInt(KEY_POLL_MINUTES, 5).toString())
            setSelectAllOnFocus(true)
        }
        val container = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.alert_frequency)
            .setMessage(R.string.alert_frequency_desc)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                    ?.coerceIn(1, 120) ?: 5
                prefs.edit().putInt(KEY_POLL_MINUTES, minutes).apply()
                Toast.makeText(
                    this,
                    getString(R.string.frequency_set, minutes),
                    Toast.LENGTH_SHORT
                ).show()
                if (prefs.getBoolean(KEY_BG_ALERTS, false)) {
                    stopService(Intent(this, PollerService::class.java))
                    startPoller()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearSession() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView.apply {
            clearCache(true)
            clearFormData()
            clearHistory()
        }
        WebStorage.getInstance().deleteAllData()
        unreadCount = 0
        refreshUnreadUi()
        loadHome()
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        webView.onResume()
        NotificationManagerCompat.from(this).cancel(NOTIF_ID_UNREAD)
        webView.removeCallbacks(unreadPoller)
        webView.post(unreadPoller)
    }

    override fun onPause() {
        inForeground = false
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_block_trackers).isChecked = blockTrackers()
        menu.findItem(R.id.action_lite_mode).isChecked = liteMode()
        menu.findItem(R.id.action_bg_alerts).isChecked = prefs.getBoolean(KEY_BG_ALERTS, false)
        menu.findItem(R.id.action_phone_fit).isChecked = phoneFit()
        menu.findItem(R.id.action_mobile_layout).isChecked = mobileLayout()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                loadHome()
                true
            }
            R.id.action_block_trackers -> {
                item.isChecked = !item.isChecked
                prefs.edit().putBoolean(KEY_BLOCK_TRACKERS, item.isChecked).apply()
                true
            }
            R.id.action_lite_mode -> {
                item.isChecked = !item.isChecked
                prefs.edit().putBoolean(KEY_LITE_MODE, item.isChecked).apply()
                loadHome()
                true
            }
            R.id.action_clear_session -> {
                clearSession()
                true
            }
            R.id.action_bg_alerts -> {
                item.isChecked = !item.isChecked
                prefs.edit().putBoolean(KEY_BG_ALERTS, item.isChecked).apply()
                if (item.isChecked) {
                    startPoller()
                    Toast.makeText(this, R.string.bg_alerts_on, Toast.LENGTH_SHORT).show()
                } else {
                    stopService(Intent(this, PollerService::class.java))
                    Toast.makeText(this, R.string.bg_alerts_off, Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_poll_frequency -> {
                showFrequencyDialog()
                true
            }
            R.id.action_phone_fit -> {
                item.isChecked = !item.isChecked
                prefs.edit().putBoolean(KEY_PHONE_FIT, item.isChecked).apply()
                loadHome()
                true
            }
            R.id.action_mobile_layout -> {
                item.isChecked = !item.isChecked
                prefs.edit().putBoolean(KEY_MOBILE_LAYOUT, item.isChecked).apply()
                loadHome()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
