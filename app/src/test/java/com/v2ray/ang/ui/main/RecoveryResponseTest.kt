package com.v2ray.ang.ui.main
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecoveryResponseTest {
    @Test fun onlyExplicitSendOpensCodeEntry() {
        assertNull(recoveryResponseStep("", false, true, false))
        assertNull(recoveryResponseStep("code", false, true, false))
        assertEquals("code", recoveryResponseStep("code", true, false, false))
    }
    @Test fun alternateStepsAndCompletionStayDistinct() {
        assertEquals("support", recoveryResponseStep("support", false, false, false))
        assertEquals("activation", recoveryResponseStep("activation", false, false, false))
        assertNull(recoveryResponseStep("code", true, false, true))
        assertEquals("done", recoveryResponseStep("", false, true, true))
    }
}
