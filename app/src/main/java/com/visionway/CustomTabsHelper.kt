package com.project.visionway

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

object CustomTabsHelper {
    private const val CHROME_PACKAGE = "com.android.chrome"

    private var session: CustomTabsSession? = null
    private var connection: CustomTabsServiceConnection? = null

    fun warmup(context: Context) {
        if (connection != null) return
        connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: android.content.ComponentName, client: CustomTabsClient) {
                client.warmup(0L)
                session = client.newSession(null)
            }
            override fun onServiceDisconnected(name: android.content.ComponentName) {
                session = null
                connection = null
            }
        }
        try {
            CustomTabsClient.bindCustomTabsService(context, CHROME_PACKAGE, connection!!)
        } catch (_: Exception) { /* sem chrome, sem problemas */ }
    }

    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            val intent = CustomTabsIntent.Builder(session)
                .setShowTitle(true)
                .build()
            intent.intent.`package` = CHROME_PACKAGE
            intent.launchUrl(context, uri)
        } catch (_: Exception) {
            // Fallback: navegador padrão
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    fun unbind(context: Context) {
        try { if (connection != null) context.unbindService(connection!!) } catch (_: Exception) {}
        connection = null
        session = null
    }
}
