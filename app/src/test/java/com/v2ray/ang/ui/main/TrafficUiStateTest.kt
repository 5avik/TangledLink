package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.TrafficSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TrafficUiStateTest {
    private fun sample(time:Long, session:String="one") = TrafficSnapshot(session,time,time,1024.0,2048.0,0.0,0.0)

    @Test fun boundedHistoryUsesActualValuesAndResetsForNewConnection() {
        var state=TrafficUiState()
        assertNull(state.latest)
        assertTrue(state.samples.isEmpty())
        repeat(80) { state=state.receive(sample((it+1)*1000L)) }
        assertEquals(60,state.samples.size)
        assertEquals(3f,state.samples.last())
        assertSame(state,state.receive(sample(1000)))
        assertSame(state,state.receive(sample(80000)))
        assertSame(state,state.receive(sample(81000).copy(proxyDownload=Double.NaN)))
        assertSame(state,state.receive(sample(81000).copy(proxyUpload=-1.0)))
        val next=state.receive(sample(81000,"two"))
        assertEquals(1,next.samples.size)
        assertEquals("two",next.latest!!.sessionId)
        assertNull(MainUiState().traffic.latest)
    }
}
