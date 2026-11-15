package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.v2ray.ang.dto.V2rayConfig

/** Dashboard telemetry is independent of whether speed is shown in the notification. */
internal fun enableTrafficStatistics(json: JsonObject) {
    if (!json.get("stats").let { it != null && it.isJsonObject }) json.add("stats", JsonObject())
    val policy = json.get("policy")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: JsonObject().also { json.add("policy", it) }
    val system = policy.get("system")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: JsonObject().also { policy.add("system", it) }
    system.addProperty("statsOutboundUplink", true)
    system.addProperty("statsOutboundDownlink", true)
}

internal fun enableTrafficStatistics(config: V2rayConfig) {
    config.stats = emptyMap<String, Any>()
    val policy = config.policy ?: V2rayConfig.PolicyBean(emptyMap()).also { config.policy = it }
    val system = (policy.system as? Map<*, *>)?.entries
        ?.associate { it.key.toString() to it.value }?.toMutableMap() ?: mutableMapOf()
    system["statsOutboundUplink"] = true
    system["statsOutboundDownlink"] = true
    policy.system = system
}
