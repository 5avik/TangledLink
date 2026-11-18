package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.TrafficSnapshot

/** Called under the daemon's native-traffic lock; timestamps use elapsedRealtime. */
internal class TrafficSampler(
    private val startedAtMillis: Long,
    private val sessionId: String,
) {
    private var lastReadMillis = startedAtMillis
    private var cached: TrafficSnapshot? = null

    fun resetCounters(nowMillis: Long) {
        lastReadMillis = nowMillis
        cached = null
    }

    fun sample(nowMillis: Long, readCounters: () -> List<OutboundTrafficStat>): TrafficSnapshot? {
        val interval = nowMillis - lastReadMillis
        if (interval < 1_000L) return cached
        val counters = readCounters()
        var proxyUp = 0.0
        var proxyDown = 0.0
        var directUp = 0.0
        var directDown = 0.0
        counters.forEach { stat ->
            val bytes = stat.value.coerceAtLeast(0).toDouble()
            when {
                stat.tag == AppConfig.TAG_BLOCKED -> Unit
                stat.tag == AppConfig.TAG_DIRECT -> when (stat.direction) {
                    AppConfig.UPLINK -> directUp += bytes
                    AppConfig.DOWNLINK -> directDown += bytes
                }
                else -> when (stat.direction) {
                    AppConfig.UPLINK -> proxyUp += bytes
                    AppConfig.DOWNLINK -> proxyDown += bytes
                }
            }
        }
        val seconds = interval / 1_000.0
        return TrafficSnapshot(
            sessionId, nowMillis, (nowMillis - startedAtMillis).coerceAtLeast(0),
            proxyUp / seconds, proxyDown / seconds, directUp / seconds, directDown / seconds,
        ).also {
            lastReadMillis = nowMillis
            cached = it
        }
    }
}
