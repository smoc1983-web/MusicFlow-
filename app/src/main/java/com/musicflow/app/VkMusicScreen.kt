package com.musicflow.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Official VK Music website hosted in a local Android WebView.
 *
 * The official VK Audio API does not offer a general third-party streaming
 * entitlement. Do not scrape credentials, impersonate VK clients, expose a JS
 * bridge, intercept audio network requests, or pretend that VK web playback
 * is a MusicFlow Media3 audio source.
 */
private const val VK_MUSIC_HOME = "https://music.vk.com/"
private const val VK_MUSIC_PACKAGE = "com.uma.musicvk"

/**
 * VK's historical public web search route. VK controls what it displays;
 * authentication, localization and URL changes may alter its behavior.
 * No private API or direct stream extraction takes place.
 */
internal fun vkMusicSearchUrl(query: String): String {
    val clean = query.trim().take(180)
    if (clean.isEmpty()) return VK_MUSIC_HOME
    return Uri.Builder().scheme("https").authority("vk.com").appendPath("search")
        .appendQueryParameter("c[q]", clean)
        .appendQueryParameter("c[section]", "audio")
        .build().toString()
}

private fun approvedVkHost(host: String?): Boolean {
    val normalized = host?.lowercase() ?: return false
    return normalized == "vk.com" || normalized.endsWith(".vk.com") ||
        normalized == "vk.ru" || normalized.endsWith(".vk.ru") ||
        normalized == "vkid.ru" || normalized.endsWith(".vkid.ru")
}

@Composable
internal fun VkMusicScreen(
    initialQuery: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageProblem by remember { mutableStateOf<String?>(null) }
    val requestedSearch = remember(initialQuery) { vkMusicSearchUrl(initialQuery) }

    fun openOfficialSite() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VK_MUSIC_HOME)))
        }.onFailure {
            pageProblem = "Не удалось открыть браузер: " + (it.message ?: "ошибка")
        }
    }

    fun openOfficialApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(VK_MUSIC_PACKAGE)
        if (launch != null) {
            runCatching { context.startActivity(launch) }
                .onFailure { openOfficialSite() }
        } else {
            // Official public store listing, not an APK mirror.
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.rustore.ru/catalog/app/$VK_MUSIC_PACKAGE")
                    )
                )
            }.onFailure { openOfficialSite() }
        }
    }

    BackHandler(enabled = canGoBack) { web?.goBack() }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (initialQuery.isBlank()) "VK Музыка"
            else "Поиск VK: " + initialQuery.take(80),
            color = palette.foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "VK открывается в общих результатах MusicFlow. " +
                "Доступ к каталогу, авторизацию и воспроизведение контролирует VK.",
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OutlinedButton(
                onClick = { web?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.weight(0.7f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            OutlinedButton(
                onClick = { web?.reload() },
                modifier = Modifier.weight(0.7f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить VK") }
            OutlinedButton(
                onClick = { openOfficialApp() },
                modifier = Modifier.weight(1.4f),
                contentPadding = PaddingValues(horizontal = 5.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Приложение", maxLines = 1) }
            OutlinedButton(
                onClick = { openOfficialSite() },
                modifier = Modifier.weight(1.3f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null,
                    modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("Браузер", maxLines = 1)
            }
        }

        if (loading) LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(), color = palette.accent
        )
        if (pageProblem != null) {
            Text(
                pageProblem.orEmpty(), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    // VK authentication may require cookies across official VK subdomains.
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loading = newProgress < 100
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            loading = true
                            pageProblem = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onReceivedError(
                            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                loading = false
                                pageProblem = "VK не открылся. Можно перейти в браузер или приложение VK."
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url ?: return true
                            if (target.scheme == "https" && approvedVkHost(target.host)) {
                                return false
                            }
                            if (target.scheme == "https") {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, target))
                                }
                            }
                            return true
                        }
                    }
                    web = this
                    loadUrl(requestedSearch)
                }
            },
            onRelease = { released ->
                released.stopLoading()
                released.webChromeClient = null
                released.webViewClient = WebViewClient()
                released.destroy()
                if (web === released) web = null
            }
        )
    }
}
