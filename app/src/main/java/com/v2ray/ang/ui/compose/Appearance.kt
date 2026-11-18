package com.v2ray.ang.ui.compose

import kotlinx.coroutines.flow.StateFlow

/** Stable storage IDs; declaration order is never persisted. */
enum class ThemeId(val storageId: String) {
    Original("original"), Sakura("sakura"), Aurora("aurora"), Cyberpunk("cyberpunk"), Terminal("terminal");

    companion object {
        fun fromStorage(value: String?): ThemeId =
            entries.firstOrNull { it.storageId == value } ?: Original
    }
}

data class AppearanceSelection(
    val themeId: ThemeId = ThemeId.Original,
    val reduceEffects: Boolean = false,
    val terminalPalette: com.v2ray.ang.ui.terminal.TerminalPaletteId = com.v2ray.ang.ui.terminal.TerminalPaletteId.MATRIX_GREEN,
)

interface AppearanceStore {
    val appearance: StateFlow<AppearanceSelection>
    /** False/exception means the caller must remain on the editor and offer retry. */
    fun saveAppearance(selection: AppearanceSelection): Boolean
}
