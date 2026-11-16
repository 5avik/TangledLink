package com.v2ray.ang.ui.terminal

/** Pure Kotlin. No shell, Android API, persistence, network or VPN side effects. */
enum class TerminalPaletteId(val stableId: String) {
    MATRIX_GREEN("matrix_green"),
    CATPPUCCIN_MOCHA("catppuccin_mocha"),
    TOKYO_NIGHT("tokyo_night"),
    GRUVBOX_DARK("gruvbox_dark");

    companion object {
        fun find(id: String): TerminalPaletteId? = entries.firstOrNull { it.stableId == id }
        fun restore(id: String?): TerminalPaletteId = id?.let(::find) ?: MATRIX_GREEN
    }
}

sealed interface TerminalCommand {
    data object Help : TerminalCommand
    data object Connect : TerminalCommand
    data object Disconnect : TerminalCommand
    data object Status : TerminalCommand
    data object Servers : TerminalCommand
    data class SelectServer(val guid: String) : TerminalCommand
    data object Stats : TerminalCommand
    data object Ping : TerminalCommand
    data object Clear : TerminalCommand
    data object ShowPalette : TerminalCommand
    data object ListPalettes : TerminalCommand
    data class SetPalette(val palette: TerminalPaletteId) : TerminalCommand
    data object Neofetch : TerminalCommand
    data object About : TerminalCommand
    data object Freedom : TerminalCommand
}

enum class TerminalInputError { TOO_LONG, UNSUPPORTED_SYNTAX, UNKNOWN_COMMAND, INVALID_ARGUMENTS, UNKNOWN_PALETTE }

sealed interface TerminalParseResult {
    data object Empty : TerminalParseResult
    data class Parsed(val command: TerminalCommand) : TerminalParseResult
    data class Invalid(val error: TerminalInputError) : TerminalParseResult
}

object TerminalCommandParser {
    const val MAX_INPUT_LENGTH = 256
    private val serverId = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    private val forbidden = ";|&<>`$\\\"'(){}[]"
    private val whitespace = Regex("[ \\t]+")

    fun parse(input: String): TerminalParseResult {
        if (input.length > MAX_INPUT_LENGTH) return invalid(TerminalInputError.TOO_LONG)
        // A newline is never interpreted as a second command. Errors do not echo raw input.
        if (input.any { (it.isISOControl() && it != '\t') || it in forbidden }) {
            return invalid(TerminalInputError.UNSUPPORTED_SYNTAX)
        }
        val clean = input.trim(' ', '\t')
        if (clean.isEmpty()) return TerminalParseResult.Empty
        val parts = clean.split(whitespace)
        val verb = parts.first().lowercase(java.util.Locale.ROOT)
        val command = when (verb) {
            "help" -> noArgs(parts, TerminalCommand.Help)
            "connect" -> noArgs(parts, TerminalCommand.Connect)
            "disconnect" -> noArgs(parts, TerminalCommand.Disconnect)
            "status" -> noArgs(parts, TerminalCommand.Status)
            "servers" -> noArgs(parts, TerminalCommand.Servers)
            "stats" -> noArgs(parts, TerminalCommand.Stats)
            "ping" -> noArgs(parts, TerminalCommand.Ping)
            "clear" -> noArgs(parts, TerminalCommand.Clear)
            "neofetch", "fastfetch" -> noArgs(parts, TerminalCommand.Neofetch)
            "about" -> noArgs(parts, TerminalCommand.About)
            "server" -> {
                if (parts.size == 3 && parts[1].equals("select", ignoreCase = true) && serverId.matches(parts[2])) {
                    TerminalCommand.SelectServer(parts[2])
                } else null
            }
            "theme" -> when {
                parts.size == 1 -> TerminalCommand.ShowPalette
                parts.size == 2 && parts[1].equals("list", true) -> TerminalCommand.ListPalettes
                parts.size == 3 && parts[1].equals("set", true) -> {
                    val palette = TerminalPaletteId.find(parts[2].lowercase(java.util.Locale.ROOT))
                        ?: return invalid(TerminalInputError.UNKNOWN_PALETTE)
                    TerminalCommand.SetPalette(palette)
                }
                else -> null
            }
            "sudo" -> when {
                // These are literal visual aliases, never a request for root or a shell.
                parts.size == 3 && parts[1] == "tangledlink" && parts[2] == "connect" -> TerminalCommand.Connect
                parts.size == 3 && parts[1] == "tangledlink" && parts[2] == "disconnect" -> TerminalCommand.Disconnect
                parts == listOf("sudo", "pacman", "-S", "freedom") -> TerminalCommand.Freedom
                else -> null
            }
            else -> return invalid(TerminalInputError.UNKNOWN_COMMAND)
        }
        return command?.let(TerminalParseResult::Parsed) ?: invalid(TerminalInputError.INVALID_ARGUMENTS)
    }

    private fun noArgs(parts: List<String>, value: TerminalCommand): TerminalCommand? = value.takeIf { parts.size == 1 }
    private fun invalid(error: TerminalInputError) = TerminalParseResult.Invalid(error)
}

enum class TerminalConnectionIntent { NONE, ALREADY_IN_STATE, REQUEST_CONNECT, REQUEST_DISCONNECT, BUSY }

/** A plan only. The owning ViewModel must recheck current state and its pending gate when executing. */
fun terminalConnectionIntent(
    command: TerminalCommand,
    isRunning: Boolean,
    requestPending: Boolean,
): TerminalConnectionIntent {
    if (command != TerminalCommand.Connect && command != TerminalCommand.Disconnect) return TerminalConnectionIntent.NONE
    if (requestPending) return TerminalConnectionIntent.BUSY
    return when (command) {
        TerminalCommand.Connect -> if (isRunning) TerminalConnectionIntent.ALREADY_IN_STATE else TerminalConnectionIntent.REQUEST_CONNECT
        TerminalCommand.Disconnect -> if (isRunning) TerminalConnectionIntent.REQUEST_DISCONNECT else TerminalConnectionIntent.ALREADY_IN_STATE
    }
}
