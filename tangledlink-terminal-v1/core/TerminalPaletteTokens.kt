package com.v2ray.ang.ui.terminal

/** sRGB ARGB values; Android/Compose independent. */
data class TerminalPaletteTokens(
    val id: TerminalPaletteId, val background: Long, val surface: Long, val raised: Long,
    val text: Long, val muted: Long, val accent: Long, val secondary: Long, val border: Long,
    val error: Long, val warning: Long, val success: Long, val info: Long,
)

object TerminalPalettes {
    val all: List<TerminalPaletteTokens> = listOf(
        TerminalPaletteTokens(TerminalPaletteId.MATRIX_GREEN, 0xFF080D0BL, 0xFF101A14L, 0xFF192B20L, 0xFFE9FFF0L, 0xFF9CB9A5L, 0xFF39FF72L, 0xFFBC96FFL, 0xFF548461L, 0xFFFF9DA4L, 0xFFF5D07FL, 0xFF39FF72L, 0xFF91DFF6L),
        TerminalPaletteTokens(TerminalPaletteId.CATPPUCCIN_MOCHA, 0xFF1E1E2EL, 0xFF313244L, 0xFF45475AL, 0xFFCDD6F4L, 0xFFBAC2DEL, 0xFFCBA6F7L, 0xFFA6E3A1L, 0xFF9399B2L, 0xFFF38BA8L, 0xFFF9E2AFL, 0xFFA6E3A1L, 0xFF89DCEBL),
        TerminalPaletteTokens(TerminalPaletteId.TOKYO_NIGHT, 0xFF1A1B26L, 0xFF24283BL, 0xFF292E42L, 0xFFC0CAF5L, 0xFFA9B1D6L, 0xFF7AA2F7L, 0xFFBB9AF7L, 0xFF737AA2L, 0xFFF7768EL, 0xFFE0AF68L, 0xFF9ECE6AL, 0xFF7DCFFFL),
        TerminalPaletteTokens(TerminalPaletteId.GRUVBOX_DARK, 0xFF282828L, 0xFF3C3836L, 0xFF504945L, 0xFFEBDBB2L, 0xFFD5C4A1L, 0xFFB8BB26L, 0xFFFABD2FL, 0xFFA89984L, 0xFFFB4934L, 0xFFFABD2FL, 0xFFB8BB26L, 0xFF8EC07CL),
    )
    fun resolve(id: String?): TerminalPaletteTokens = all.first { it.id == TerminalPaletteId.restore(id) }
}
