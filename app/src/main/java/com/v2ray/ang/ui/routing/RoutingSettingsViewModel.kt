package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val REMARKS_LAN = "Исключить локальную сеть (LAN)"
private const val REMARKS_RU = "Исключить российские ресурсы (RU)"
private const val REMARKS_CUSTOM_DIRECT = "Свои исключения (Direct)"
private const val REMARKS_CUSTOM_PROXY = "Свои правила (Proxy)"

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    private val _bypassLanFlow = MutableStateFlow(false)
    val bypassLanFlow: StateFlow<Boolean> = _bypassLanFlow.asStateFlow()

    private val _bypassRussiaFlow = MutableStateFlow(false)
    val bypassRussiaFlow: StateFlow<Boolean> = _bypassRussiaFlow.asStateFlow()

    private val _customDirectFlow = MutableStateFlow("")
    val customDirectFlow: StateFlow<String> = _customDirectFlow.asStateFlow()

    private val _customProxyFlow = MutableStateFlow("")
    val customProxyFlow: StateFlow<String> = _customProxyFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        val loaded = MmkvManager.decodeRoutingRulesets()?.toMutableList() ?: mutableListOf()
        var modified = false
        loaded.forEach { item ->
            if (item.id.isEmpty()) {
                item.id = UUID.randomUUID().toString()
                modified = true
            }
            if (item.domain?.contains("geosite:ru") == true) {
                item.domain = item.domain?.map { if (it == "geosite:ru") "geosite:category-ru" else it }?.distinct()
                modified = true
            }
        }
        if (modified) {
            MmkvManager.encodeRoutingRulesets(loaded)
        }
        rulesets.clear()
        rulesets.addAll(loaded)
        _rulesetsFlow.value = rulesets.toList()

        updateQuickStates()
    }

    private fun updateQuickStates() {
        val lanRules = rulesets.filter {
            it.outboundTag == TAG_DIRECT &&
                    (it.ip?.contains("geoip:private") == true || it.domain?.contains("geosite:private") == true || it.remarks?.contains("LAN") == true || it.remarks == REMARKS_LAN)
        }
        _bypassLanFlow.value = lanRules.isNotEmpty() && lanRules.any { it.enabled }

        val ruRules = rulesets.filter {
            it.outboundTag == TAG_DIRECT &&
                    (it.ip?.contains("geoip:ru") == true || it.domain?.contains("geosite:category-ru") == true || it.domain?.contains("domain:ru") == true || it.remarks?.contains("RU") == true || it.remarks == REMARKS_RU)
        }
        _bypassRussiaFlow.value = ruRules.isNotEmpty() && ruRules.any { it.enabled }

        val directRule = rulesets.find { it.remarks == REMARKS_CUSTOM_DIRECT }
        val directEntries = ((directRule?.domain.orEmpty()) + (directRule?.ip.orEmpty())).distinct()
        _customDirectFlow.value = directEntries.joinToString(", ")

        val proxyRule = rulesets.find { it.remarks == REMARKS_CUSTOM_PROXY }
        val proxyEntries = ((proxyRule?.domain.orEmpty()) + (proxyRule?.ip.orEmpty())).distinct()
        _customProxyFlow.value = proxyEntries.joinToString(", ")
    }

    fun toggleBypassLan(enabled: Boolean) {
        val lanRules = rulesets.filter {
            it.outboundTag == TAG_DIRECT &&
                    (it.ip?.contains("geoip:private") == true || it.domain?.contains("geosite:private") == true || it.remarks?.contains("LAN") == true || it.remarks == REMARKS_LAN)
        }
        if (lanRules.isNotEmpty()) {
            lanRules.forEach { rule ->
                val index = rulesets.indexOf(rule)
                if (index >= 0) {
                    rulesets[index] = rule.copy(enabled = enabled)
                }
            }
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        } else {
            val newRule = RulesetItem(
                id = UUID.randomUUID().toString(),
                remarks = REMARKS_LAN,
                outboundTag = TAG_DIRECT,
                ip = listOf("geoip:private"),
                domain = listOf("geosite:private"),
                enabled = enabled
            )
            rulesets.add(0, newRule)
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
        _bypassLanFlow.value = enabled
    }

    fun toggleBypassRussia(enabled: Boolean) {
        val ruRules = rulesets.filter {
            it.outboundTag == TAG_DIRECT &&
                    (it.ip?.contains("geoip:ru") == true || it.domain?.contains("geosite:category-ru") == true || it.domain?.contains("domain:ru") == true || it.remarks?.contains("RU") == true || it.remarks == REMARKS_RU)
        }
        if (ruRules.isNotEmpty()) {
            ruRules.forEach { rule ->
                val index = rulesets.indexOf(rule)
                if (index >= 0) {
                    rulesets[index] = rule.copy(enabled = enabled)
                }
            }
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        } else {
            val domainRule = RulesetItem(
                id = UUID.randomUUID().toString(),
                remarks = "Исключить российские сайты (RU)",
                outboundTag = TAG_DIRECT,
                domain = listOf("domain:ru", "geosite:category-ru"),
                enabled = enabled
            )
            val ipRule = RulesetItem(
                id = UUID.randomUUID().toString(),
                remarks = "Исключить российские IP (RU)",
                outboundTag = TAG_DIRECT,
                ip = listOf("geoip:ru"),
                enabled = enabled
            )
            rulesets.add(0, ipRule)
            rulesets.add(0, domainRule)
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
        _bypassRussiaFlow.value = enabled
    }

    fun saveCustomDirect(text: String) {
        val items = text.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val domains = items.filter { !it.matches(Regex("^[0-9./]+$")) && !it.startsWith("geoip:") }
        val ips = items.filter { it.matches(Regex("^[0-9./]+$")) || it.startsWith("geoip:") }

        val index = rulesets.indexOfFirst { it.remarks == REMARKS_CUSTOM_DIRECT }
        if (items.isEmpty()) {
            if (index >= 0) {
                rulesets.removeAt(index)
                MmkvManager.encodeRoutingRulesets(rulesets)
                _rulesetsFlow.value = rulesets.toList()
            }
        } else if (index >= 0) {
            val updated = rulesets[index].copy(
                domain = domains.ifEmpty { null },
                ip = ips.ifEmpty { null },
                enabled = true
            )
            update(index, updated)
        } else {
            val newRule = RulesetItem(
                id = UUID.randomUUID().toString(),
                remarks = REMARKS_CUSTOM_DIRECT,
                outboundTag = TAG_DIRECT,
                domain = domains.ifEmpty { null },
                ip = ips.ifEmpty { null },
                enabled = true
            )
            rulesets.add(0, newRule)
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
        _customDirectFlow.value = text
    }

    fun saveCustomProxy(text: String) {
        val items = text.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val domains = items.filter { !it.matches(Regex("^[0-9./]+$")) && !it.startsWith("geoip:") }
        val ips = items.filter { it.matches(Regex("^[0-9./]+$")) || it.startsWith("geoip:") }

        val index = rulesets.indexOfFirst { it.remarks == REMARKS_CUSTOM_PROXY }
        if (items.isEmpty()) {
            if (index >= 0) {
                rulesets.removeAt(index)
                MmkvManager.encodeRoutingRulesets(rulesets)
                _rulesetsFlow.value = rulesets.toList()
            }
        } else if (index >= 0) {
            val updated = rulesets[index].copy(
                domain = domains.ifEmpty { null },
                ip = ips.ifEmpty { null },
                enabled = true
            )
            update(index, updated)
        } else {
            val newRule = RulesetItem(
                id = UUID.randomUUID().toString(),
                remarks = REMARKS_CUSTOM_PROXY,
                outboundTag = TAG_PROXY,
                domain = domains.ifEmpty { null },
                ip = ips.ifEmpty { null },
                enabled = true
            )
            rulesets.add(0, newRule)
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
        _customProxyFlow.value = text
    }

    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            rulesets[position] = item
            SettingsManager.saveRoutingRuleset(position, item)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    fun move(fromPosition: Int, toPosition: Int) {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}
