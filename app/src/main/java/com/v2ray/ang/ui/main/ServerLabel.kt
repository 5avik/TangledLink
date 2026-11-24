package com.v2ray.ang.ui.main

import java.util.Locale

/** Presentation only: the subscription name, profile and GUID remain unchanged. */
internal data class ServerLabel(
    val countryFlag: String?,
    val automatic: Boolean,
    val detail: String,
)

private val isoCountryCodes = Locale.getISOCountries().toSet()

internal fun countryNameFromFlag(flag: String?, locale: Locale): String? {
    if (flag == null || !flagPattern.matches(flag)) return null
    val code = String(CharArray(2) { index ->
        ('A'.code + flag.codePointAt(index * 2) - 0x1F1E6).toChar()
    })
    if (code !in isoCountryCodes) return null
    return Locale.Builder().setRegion(code).build().getDisplayCountry(locale).takeIf { it.isNotBlank() && it != code }
}

private val flagPattern = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")
private val automaticPattern = Regex(
    """(?iu)(?:^|[^\p{L}\p{N}])(?:авто[\s_-]*выбор|auto[\s_-]*select(?:ion)?)(?=$|[^\p{L}\p{N}])"""
)
private val whitespacePattern = Regex("\\s+")
private val numberedPattern = Regex("""^[№#]\s*(\d+)\s*[-·|:–—]?\s*(.*)$""")

internal fun serverLabel(remarks: String): ServerLabel {
    if (automaticPattern.containsMatchIn(remarks)) return ServerLabel(null, true, "")
    val flag = flagPattern.find(remarks)?.value
    val name = (if (flag == null) remarks else remarks.replace(flag, ""))
        .replace(whitespacePattern, " ").trim().trim('-', '·', '|', ':', '–', '—').trim()
    val numbered = numberedPattern.matchEntire(name)
    val suffix = numbered?.groupValues?.get(2)?.trim() ?: name
    val detail = when (suffix.lowercase(Locale.ROOT)) {
        "tcp" -> "TCP"
        "grpc" -> "gRPC"
        "xhttp" -> "XHTTP"
        else -> suffix
    }
    return ServerLabel(flag, false, detail)
}
