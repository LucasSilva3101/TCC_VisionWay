package com.project.visionway

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech

object TtsUtils {
    const val GOOGLE_TTS_PKG = "com.google.android.tts"

    fun isGoogleTtsInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(GOOGLE_TTS_PKG, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openPlayStoreGoogleTts(ctx: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_TTS_PKG"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_TTS_PKG"))
        try {
            ctx.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            try { ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }

    /** Abre a tela do Google TTS para baixar/atualizar dados (inclui pt-BR) */
    fun requestInstallGoogleTtsData(ctx: Context) {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            setPackage(GOOGLE_TTS_PKG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(intent) } catch (_: Exception) {}
    }

    /** Configurações do TTS do Android (útil para checar engine padrão/vozes baixadas) */
    fun openTtsSettings(ctx: Context) {
        val ttsSettings = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(ttsSettings); return } catch (_: Exception) {}
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}