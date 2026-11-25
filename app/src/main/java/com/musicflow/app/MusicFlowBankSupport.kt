package com.musicflow.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Bank buttons never transfer funds or request credentials.
 * The recipient phone is copied and an official banking app is opened.
 * The user selects their bank and confirms the transfer inside that app.
 */
internal enum class MusicFlowBank(
    val title: String,
    val packageId: String,
    val officialAppPage: String
) {
    TBANK("Т-Банк", "com.idamob.tinkoff.android",
        "https://www.rustore.ru/catalog/app/com.idamob.tinkoff.android"),
    SBER("СберБанк", "ru.sberbankmobile",
        "https://www.rustore.ru/catalog/app/ru.sberbankmobile"),
    OZON("Ozon Банк", "ru.ozon.fintech.finance",
        "https://www.rustore.ru/catalog/app/ru.ozon.fintech.finance")
}

internal object MusicFlowBankSupport {
    fun copyPhone(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(
            "Номер для поддержки MusicFlow", MusicFlowUpdates.SUPPORT_NUMBER
        ))
    }

    /** Returns true when an installed bank was launched, otherwise opens its verified RuStore page. */
    fun open(context: Context, bank: MusicFlowBank): Boolean {
        copyPhone(context)
        val appIntent = context.packageManager.getLaunchIntentForPackage(bank.packageId)
        if (appIntent != null) {
            try {
                context.startActivity(appIntent)
                return true
            } catch (_: Exception) {
                // Banking apps may have changed their launcher component.
            }
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bank.officialAppPage)))
        return false
    }
}
