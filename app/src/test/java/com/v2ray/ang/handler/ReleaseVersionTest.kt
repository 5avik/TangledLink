package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReleaseVersionTest {
    @Test fun comparesTestVersionsWithoutOfferingDowngrades() {
        assertTrue(compareReleaseVersions("0.8", "0.9-test") < 0)
        assertTrue(compareReleaseVersions("0.8", "0.9.1-test") < 0)
        assertTrue(compareReleaseVersions("0.9.1-test", "0.9-test") > 0)
        assertTrue(compareReleaseVersions("0.9.1", "0.9.1-test") > 0)
        assertTrue(compareReleaseVersions("0.10", "0.9.1-test") > 0)
        assertEquals(0, compareReleaseVersions("v0.9.1-test+build.1", "0.9.1-test"))
        assertEquals(0, compareReleaseVersions("0.9", "0.9.0"))
        assertTrue(compareReleaseVersions("0.9.1-test.10", "0.9.1-test.2") > 0)
    }
}
