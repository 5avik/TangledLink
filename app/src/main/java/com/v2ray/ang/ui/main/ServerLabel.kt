package com.v2ray.ang.ui.main

import java.util.Locale

/** Presentation only: the subscription name, profile and GUID remain unchanged. */
internal data class ServerLabel(
    val countryFlag: String?,
    val automatic: Boolean,
    val detail: String,
)

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
    val detail = if (numbered == null) name else {
        val number = "№" + numbered.groupValues[1]
        val suffix = numbered.groupValues[2].trim()
        val transport = when (suffix.lowercase(Locale.ROOT)) {
            "tcp" -> "TCP"
            "grpc" -> "gRPC"
            "xhttp" -> "XHTTP"
            else -> suffix
        }
        if (transport.isEmpty()) number else "$number · $transport"
    }
    return ServerLabel(flag, false, detail)
}
