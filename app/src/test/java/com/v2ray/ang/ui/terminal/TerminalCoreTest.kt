package com.v2ray.ang.ui.terminal

/** Framework-free JVM checks. Adapt to the project's existing JUnit setup during integration. */
class TerminalCoreTest {
    @org.junit.jupiter.api.Test fun coreChecks() {
    var checks = 0
    fun expect(actual: Any?, expected: Any?) {
        check(actual == expected) { "Expected $expected but received $actual" }
        checks++
    }
    val valid = mapOf(
        "help" to TerminalCommand.Help,
        "  CONNECT\t" to TerminalCommand.Connect,
        "disconnect" to TerminalCommand.Disconnect,
        "status" to TerminalCommand.Status,
        "servers" to TerminalCommand.Servers,
        "server select AbC-123_foo:bar.baz" to TerminalCommand.SelectServer("AbC-123_foo:bar.baz"),
        "stats" to TerminalCommand.Stats,
        "ping" to TerminalCommand.Ping,
        "clear" to TerminalCommand.Clear,
        "theme" to TerminalCommand.ShowPalette,
        "theme list" to TerminalCommand.ListPalettes,
        "theme set matrix_green" to TerminalCommand.SetPalette(TerminalPaletteId.MATRIX_GREEN),
        "theme set catppuccin_mocha" to TerminalCommand.SetPalette(TerminalPaletteId.CATPPUCCIN_MOCHA),
        "theme set TOKYO_NIGHT" to TerminalCommand.SetPalette(TerminalPaletteId.TOKYO_NIGHT),
        "theme set gruvbox_dark" to TerminalCommand.SetPalette(TerminalPaletteId.GRUVBOX_DARK),
        "neofetch" to TerminalCommand.Neofetch,
        "fastfetch" to TerminalCommand.Neofetch,
        "about" to TerminalCommand.About,
        "sudo tangledlink connect" to TerminalCommand.Connect,
        "sudo tangledlink disconnect" to TerminalCommand.Disconnect,
        "sudo pacman -S freedom" to TerminalCommand.Freedom,
    )
    valid.forEach { (input, command) -> expect(TerminalCommandParser.parse(input), TerminalParseResult.Parsed(command)) }
    listOf("", " \t  ").forEach { expect(TerminalCommandParser.parse(it), TerminalParseResult.Empty) }
    val syntax = listOf("connect;disconnect", "connect | sh", "$(id)", "`id`", "connect\ndisconnect",
        "connect\r", "status\u0000", "server select /etc/passwd", "theme set 'matrix_green'", "connect > file",
        "connect && disconnect", "connect &", "\\", "[status]", "theme set {matrix_green}")
    syntax.filterNot { it.contains("/etc") }.forEach {
        expect(TerminalCommandParser.parse(it), TerminalParseResult.Invalid(TerminalInputError.UNSUPPORTED_SYNTAX))
    }
    listOf("connect now", "disconnect x", "help x", "status x", "servers x", "stats x", "ping x", "clear x",
        "neofetch x", "about x", "server", "server select", "server select a b", "server select /etc/passwd",
        "server select -bad", "server select ${"a".repeat(129)}", "theme set", "theme list x", "theme set matrix_green x",
        "sudo id", "sudo tangledlink connect x", "sudo pacman -S anything").forEach {
        expect(TerminalCommandParser.parse(it), TerminalParseResult.Invalid(TerminalInputError.INVALID_ARGUMENTS))
    }
    expect(TerminalCommandParser.parse("theme set unknown"), TerminalParseResult.Invalid(TerminalInputError.UNKNOWN_PALETTE))
    expect(TerminalCommandParser.parse("rm -rf files"), TerminalParseResult.Invalid(TerminalInputError.UNKNOWN_COMMAND))
    expect(TerminalCommandParser.parse("x".repeat(257)), TerminalParseResult.Invalid(TerminalInputError.TOO_LONG))
    expect(TerminalCommandParser.parse("x".repeat(256)), TerminalParseResult.Invalid(TerminalInputError.UNKNOWN_COMMAND))
    expect(TerminalPaletteId.restore(null), TerminalPaletteId.MATRIX_GREEN)
    expect(TerminalPaletteId.restore("unknown"), TerminalPaletteId.MATRIX_GREEN)
    expect(TerminalPaletteId.restore("tokyo_night"), TerminalPaletteId.TOKYO_NIGHT)
    expect(TerminalPaletteId.find("unknown"), null)
    expect(TerminalPalettes.resolve(null).id, TerminalPaletteId.MATRIX_GREEN)
    expect(TerminalPalettes.resolve("unknown").id, TerminalPaletteId.MATRIX_GREEN)
    expect(TerminalPalettes.resolve("tokyo_night").id, TerminalPaletteId.TOKYO_NIGHT)
    expect(TerminalPalettes.all.map { it.id }.toSet(), TerminalPaletteId.entries.toSet())
    for (running in listOf(false, true)) {
        expect(terminalConnectionIntent(TerminalCommand.Help, running, false), TerminalConnectionIntent.NONE)
        expect(terminalConnectionIntent(TerminalCommand.Help, running, true), TerminalConnectionIntent.NONE)
        expect(terminalConnectionIntent(TerminalCommand.Connect, running, true), TerminalConnectionIntent.BUSY)
        expect(terminalConnectionIntent(TerminalCommand.Disconnect, running, true), TerminalConnectionIntent.BUSY)
        expect(terminalConnectionIntent(TerminalCommand.Connect, running, false), if (running) TerminalConnectionIntent.ALREADY_IN_STATE else TerminalConnectionIntent.REQUEST_CONNECT)
        expect(terminalConnectionIntent(TerminalCommand.Disconnect, running, false), if (running) TerminalConnectionIntent.REQUEST_DISCONNECT else TerminalConnectionIntent.ALREADY_IN_STATE)
    }
    org.junit.jupiter.api.Assertions.assertTrue(checks > 80)
}

}
