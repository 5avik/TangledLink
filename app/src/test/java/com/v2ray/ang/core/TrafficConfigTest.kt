package com.v2ray.ang.core

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TrafficConfigTest {
    @Test fun countersAreEnabledWithoutDiscardingCustomPolicies() {
        val json=JsonParser.parseString("""{"policy":{"levels":{"0":{"connIdle":30}},"system":{"statsInboundUplink":true,"statsOutboundUplink":false}}}""").asJsonObject
        enableTrafficStatistics(json)
        val policy=json.getAsJsonObject("policy")
        assertEquals(30,policy.getAsJsonObject("levels").getAsJsonObject("0").get("connIdle").asInt)
        val system=policy.getAsJsonObject("system")
        assertTrue(system.get("statsInboundUplink").asBoolean)
        assertTrue(system.get("statsOutboundUplink").asBoolean)
        assertTrue(system.get("statsOutboundDownlink").asBoolean)
        assertTrue(json.get("stats").isJsonObject)
        enableTrafficStatistics(json)
        assertEquals(3,system.size())
    }
}
