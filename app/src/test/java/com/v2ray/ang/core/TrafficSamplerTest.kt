package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.OutboundTrafficStat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TrafficSamplerTest {
    @Test fun nativeCountersAreReadOnceAndSharedBetweenConsumers() {
        val sampler = TrafficSampler(1_000, "session")
        var reads = 0
        val read = {
            reads++
            listOf(
                OutboundTrafficStat("custom-proxy", AppConfig.DOWNLINK, 4096),
                OutboundTrafficStat("custom-proxy", AppConfig.UPLINK, 1024),
                OutboundTrafficStat(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK, 2048),
                OutboundTrafficStat(AppConfig.TAG_BLOCKED, AppConfig.DOWNLINK, 99999),
                OutboundTrafficStat("bad", AppConfig.UPLINK, -1),
            )
        }
        assertNull(sampler.sample(1_999, read))
        val first = sampler.sample(3_000, read)!!
        assertEquals(2048.0, first.proxyDownload)
        assertEquals(1024.0, first.directDownload)
        assertEquals(512.0, first.proxyUpload)
        assertEquals(2000, first.connectedMillis)
        assertSame(first, sampler.sample(3_200, read))
        assertEquals(1, reads)
        val idle = sampler.sample(4_000) { emptyList() }!!
        assertEquals(0.0, idle.download)
        assertEquals(3000, idle.connectedMillis)
    }

    @Test fun reloadResetsCounterIntervalButPreservesConnectionDuration() {
        val sampler = TrafficSampler(0, "same-session")
        sampler.sample(1000) { emptyList() }
        sampler.resetCounters(5000)
        assertNull(sampler.sample(5500) { error("Too early") })
        val result = sampler.sample(6000) { listOf(OutboundTrafficStat("proxy",AppConfig.UPLINK,1000)) }!!
        assertEquals("same-session",result.sessionId)
        assertEquals(6000,result.connectedMillis)
        assertEquals(1000.0,result.upload)
    }

    @Test fun failedReadCanBeRetriedWithoutLosingInterval() {
        val sampler = TrafficSampler(0,"session")
        assertThrows(IllegalStateException::class.java) { sampler.sample(1000) { error("Read failed") } }
        val result = sampler.sample(2000) { listOf(OutboundTrafficStat("proxy",AppConfig.DOWNLINK,4000)) }!!
        assertEquals(2000.0,result.download)
    }
}
