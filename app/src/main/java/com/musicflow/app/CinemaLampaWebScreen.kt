package com.musicflow.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URI

/**
 * Isolated official Lampa Web application runtime, not "JavaScript in Kotlin".
 *
 * Third-party Lampa JS is executed only inside remote Lampa WebView origin.
 * There is NO addJavascriptInterface, file access, native plugin API or
 * JS-to-Android bridge. A plugin can still access Lampa's web data and network,
 * so the user explicitly chooses whether to install it.
 */
internal object CinemaLampaBridge {
    const val LAMPA_HTTPS = "https://yumata.github.io/lampa/"
    const val LAMPA_HTTP = "http://lampa.mx/"
    const val DEFAULT_PLUGIN = "http://bwa.ad/rc"

    fun isAllowedNavigation(value: String, allowHttp: Boolean): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.userInfo != null) return false
        return (uri.scheme == "https" && host == "yumata.github.io" &&
            (uri.path == "/lampa" || uri.path.startsWith("/lampa/"))) ||
            (allowHttp && uri.scheme == "http" && host == "lampa.mx")
    }

    fun allowedPluginUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.scheme !in listOf("http", "https") ||
            uri.userInfo != null || uri.fragment != null ||
            host.isBlank() || !host.contains('.') ||
            host == "localhost" || host.endsWith(".local") ||
            host.endsWith(".internal") || host.contains(':') ||
            host.all { it == '.' || it.isDigit() } ||
            host.startsWith("127.") || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.startsWith("169.254.") ||
            value.length > 2048) return false
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return false
        }
        return true
    }

    /**
     * Uses upstream Lampa.Plugins.add API, not arbitrary script injection from user:
     * the URL is JSON-string quoted, inserted only as a string parameter.
     */
    fun installScript(url: String): String {
        val quoted = JSONObject.quote(url)
        return """
            (function() {
              try {
                if (!window.Lampa || !Lampa.Plugins ||
                    typeof Lampa.Plugins.add !== 'function') return 'not-ready';
                var address = $quoted;
                var exists = (Lampa.Plugins.get ? Lampa.Plugins.get() : []).some(function(p) {
                  return p && p.url === address;
                });
                if (exists) return 'already-installed';
                Lampa.Plugins.add({url:address,status:1});
                return 'registered';
              } catch (e) {
                return 'error:' + String((e && e.message) || e).slice(0,180);
              }
            })();
        """.trimIndent()
    }
}

@Composable
internal fun CinemaLampaWebScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var site by remember { mutableStateOf(CinemaLampaBridge.LAMPA_HTTPS) }
    var pluginUrl by remember { mutableStateOf(CinemaLampaBridge.DEFAULT_PLUGIN) }
    var showInstaller by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Открывается веб-интерфейс Lampa…") }
    var confirmedUrl by remember { mutableStateOf<String?>(null) }
    val allowHttp = site == CinemaLampaBridge.LAMPA_HTTP

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onExit()
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "К кино")
            }
            Text("Lampa · веб-плагины", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Перезагрузить Lampa")
            }
            TextButton(onClick = { showInstaller = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("JS")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = !allowHttp,
                onClick = {
                    site = CinemaLampaBridge.LAMPA_HTTPS
                    webView?.loadUrl(site)
                },
                label = { Text("HTTPS Lampa") }
            )
            FilterChip(
                selected = allowHttp,
                onClick = {
                    site = CinemaLampaBridge.LAMPA_HTTP
                    webView?.loadUrl(site)
                },
                label = { Text("Lampa MX · HTTP") }
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.setGeolocationEnabled(false)
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.safeBrowsingEnabled = true
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (CinemaLampaBridge.isAllowedNavigation(request.url.toString(),
                                    allowHttp = site == CinemaLampaBridge.LAMPA_HTTP)) {
                                return false
                            }
                            val uri = request.url
                            if (uri.scheme == "https" || uri.scheme == "http") {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: ActivityNotFoundException) {
                                    status = "Не найден браузер для внешней ссылки"
                                }
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            loading = true
                            status = "Загружается Lampa…"
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            status = "Lampa загружена. JS-плагины работают в её веб-окружении."
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                status = "Ошибка открытия Lampa: " + error.description
                                loading = false
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: SslError
                        ) {
                            handler.cancel()
                            status = "SSL-ошибка. Небезопасное соединение отклонено."
                            loading = false
                        }
                    }
                    webView = this
                    loadUrl(site)
                }
            },
            update = { current -> if (current !== webView) webView = current },
            onRelease = { current ->
                current.stopLoading()
                current.loadUrl("about:blank")
                current.destroy()
                if (webView === current) webView = null
            }
        )
    }

    if (showInstaller) {
        AlertDialog(
            onDismissRequest = { showInstaller = false },
            title = { Text("Добавить Lampa JS-плагин") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Плагин запускается в веб-интерфейсе Lampa, не в Android-коде MusicFlow. " +
                        "Он может читать данные Lampa и выполнять сетевые запросы. " +
                        "Подключайте только проверенные источники.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = pluginUrl,
                        onValueChange = { pluginUrl = it.take(2048) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL JavaScript-плагина") },
                        maxLines = 3
                    )
                    if (pluginUrl.startsWith("http://")) {
                        Text("HTTP не шифруется. На HTTPS-странице браузер может " +
                            "заблокировать такой скрипт. Попробуйте HTTPS-адрес источника.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Также можно установить расширения штатно: " +
                        "Настройки Lampa → Расширения → Добавить плагин.",
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (CinemaLampaBridge.allowedPluginUrl(pluginUrl)) {
                            confirmedUrl = pluginUrl.trim()
                            showInstaller = false
                        } else {
                            status = "Укажите допустимую публичную HTTP(S)-ссылку без логина."
                            showInstaller = false
                        }
                    },
                    enabled = pluginUrl.isNotBlank()
                ) {
                    Text("Подключить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstaller = false }) { Text("Отмена") }
            }
        )
    }

    val pendingUrl = confirmedUrl
    if (pendingUrl != null) {
        AlertDialog(
            onDismissRequest = { confirmedUrl = null },
            title = { Text("Запустить сторонний JavaScript?") },
            text = {
                Text("Lampa загрузит плагин из сети:\n$pendingUrl\n\n" +
                    "Это сторонний код с доступом к веб-среде Lampa. " +
                    "Не вводите в него пароли и платёжные данные. " +
                    "Продолжить только если доверяете его автору.")
            },
            confirmButton = {
                Button(onClick = {
                    confirmedUrl = null
                    val browser = webView
                    if (browser == null) {
                        status = "Веб-интерфейс ещё не запущен"
                    } else {
                        browser.evaluateJavascript(
                            CinemaLampaBridge.installScript(pendingUrl)
                        ) { answer ->
                            status = when {
                                answer.contains("registered") ->
                                    "Lampa зарегистрировала плагин. Перезапустите её экран, если не появился."
                                answer.contains("already-installed") ->
                                    "Плагин уже добавлен в Lampa."
                                answer.contains("not-ready") ->
                                    "Lampa ещё не загрузилась. Откройте сайт и повторите."
                                else -> "Lampa вернула ошибку установки: " + answer.take(200)
                            }
                        }
                    }
                }) { Text("Разрешить и добавить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmedUrl = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
