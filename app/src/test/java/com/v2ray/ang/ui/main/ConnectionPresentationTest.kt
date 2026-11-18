package com.v2ray.ang.ui.main

import com.v2ray.ang.ui.terminal.ConnectionRequestGate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConnectionPresentationTest {
    @Test fun onlyActualServiceAndLaunchRequestControlThePortalAndTestsDoNotImplyConnecting() {
        val idle=MainUiState(selectedGuid="existing-guid")
        assertEquals(ConnectionStatus.Disconnected,idle.connectionPresentation())
        assertTrue(idle.connectionActionAvailable(false))
        for(status in listOf(MainStatus.Testing,MainStatus.TestProgress("5/10"),MainStatus.Connected)) {
            assertEquals(ConnectionStatus.Disconnected,idle.copy(status=status,isTesting=true).connectionPresentation())
            assertEquals(ConnectionStatus.Connected,idle.copy(status=status,isRunning=true).connectionPresentation())
        }
        assertEquals(ConnectionStatus.Connecting,idle.copy(isConnecting=true).connectionPresentation())
        assertFalse(idle.copy(isConnecting=true).connectionActionAvailable(false))
        assertEquals(ConnectionStatus.Connected,idle.copy(isConnecting=true,isRunning=true).connectionPresentation())
        assertFalse(MainUiState().connectionActionAvailable(false))
        assertTrue(MainUiState(isRunning=true).connectionActionAvailable(false))
        assertFalse(idle.connectionActionAvailable(true))
    }

    @Test fun theExistingGateExposesItsTargetAndPermissionCancelOrFailureReleasesIt() {
        val gate=ConnectionRequestGate()
        assertNull(gate.targetState.value)
        assertTrue(gate.acquire(false))
        assertEquals(true,gate.targetState.value)
        assertFalse(gate.acquire(false))
        assertFalse(gate.acquire(true))
        gate.observed(false)
        assertTrue(gate.pending)
        gate.finished() // Existing permission cancellation / launch failure path.
        assertNull(gate.targetState.value)
        assertTrue(gate.acquire(false))
        gate.observed(true)
        assertNull(gate.targetState.value)
        assertTrue(gate.acquire(true))
        assertEquals(false,gate.targetState.value)
        gate.observed(true)
        assertTrue(gate.pending)
        gate.observed(false)
        assertFalse(gate.pending)
    }
}
