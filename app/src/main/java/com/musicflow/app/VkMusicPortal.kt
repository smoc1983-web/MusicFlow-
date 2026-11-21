package com.musicflow.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Account sign-in and playback run only on the FIRST-PARTY VK web site,
 * not in a fake login form or a credential-scraping WebView.
 *
 * This is separate from VkAudioSource: signing into VK in the browser does NOT
 * authorize the native Media3 player to access VK's restricted audio API.
 */
internal object VkMusicPortal {
    private const val MUSIC_PAGE = "https://vk.com/audio"
    private const val SEARCH_PAGE = "https://vk.com/search"

    fun openMyMusic(context: Context): Result<Unit> =
        open(context, Uri.parse(MUSIC_PAGE))

    fun openSearch(context: Context, query: String): Result<Unit> {
        val requested = query.trim().take(160)
        if (requested.isBlank()) return openMyMusic(context)
        val url = Uri.parse(SEARCH_PAGE).buildUpon()
            .appendQueryParameter("c[q]", requested)
            .appendQueryParameter("c[section]", "audio")
            .build()
        return open(context, url)
    }

    fun openExternal(context: Context): Result<Unit> = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(MUSIC_PAGE))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
    }

    private fun open(context: Context, uri: Uri): Result<Unit> = runCatching {
        require(uri.scheme == "https" && uri.host == "vk.com") {
            "Разрешены только официальные страницы VK"
        }
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            )
        }
    }
}
