package com.v2ray.ang.ui.main

import java.net.URI

/** This link is a request to refresh the authenticated account, never proof of payment. */
internal object BillingReturnLink {
    fun accepts(action: String?, value: String?): Boolean {
        if (action != "android.intent.action.VIEW" || value == null || value.length > 2048) return false
        val uri = try { URI(value) } catch (_: Exception) { return false }
        if (uri.rawUserInfo != null || uri.rawFragment != null) return false
        return (uri.scheme.equals("https", true) && uri.host.equals("app.tngl.pro", true) &&
            uri.port in listOf(-1,443) && uri.rawPath == "/billing/return") ||
            (uri.scheme == "tangledlink" && uri.host == "billing" && uri.port == -1 && uri.rawPath == "/return")
    }
}
