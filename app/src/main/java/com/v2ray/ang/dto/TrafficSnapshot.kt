package com.v2ray.ang.dto

import java.io.Serializable

/** Rates from one destructive native-counter read, shared by notification and UI. */
data class TrafficSnapshot(
    val sessionId: String,
    val capturedAtMillis: Long,
    val connectedMillis: Long,
    val proxyUpload: Double,
    val proxyDownload: Double,
    val directUpload: Double,
    val directDownload: Double,
) : Serializable {
    val upload: Double get() = proxyUpload + directUpload
    val download: Double get() = proxyDownload + directDownload
    val isValid: Boolean get() = sessionId.isNotBlank() && capturedAtMillis >= 0 &&
        connectedMillis >= 0 && listOf(proxyUpload, proxyDownload, directUpload, directDownload)
            .all { it.isFinite() && it >= 0.0 } && upload.isFinite() && download.isFinite()
}
