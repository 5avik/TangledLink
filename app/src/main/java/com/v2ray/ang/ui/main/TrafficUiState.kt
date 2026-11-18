package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.TrafficSnapshot

data class TrafficUiState(
    val latest: TrafficSnapshot? = null,
    val samples: List<Float> = emptyList(),
) {
    fun receive(snapshot: TrafficSnapshot): TrafficUiState {
        if (!snapshot.isValid || (latest != null && snapshot.capturedAtMillis <= latest.capturedAtMillis)) return this
        val history = if (latest?.sessionId == snapshot.sessionId) samples else emptyList()
        val activity = ((snapshot.download + snapshot.upload) / 1024.0).toFloat()
        return TrafficUiState(snapshot, (history + activity.coerceAtMost(Float.MAX_VALUE)).takeLast(60))
    }
}
