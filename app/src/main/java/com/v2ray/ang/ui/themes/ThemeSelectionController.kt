package com.v2ray.ang.ui.themes

import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.ui.compose.AppearanceSelection
import com.v2ray.ang.ui.compose.AppearanceStore
import com.v2ray.ang.ui.compose.ThemeId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ThemePickerState(
    val draft: AppearanceSelection,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val applied: Boolean = false,
    val cancelled: Boolean = false,
)

sealed interface ThemePickerAction {
    data class Preview(val id: ThemeId) : ThemePickerAction
    data class ReduceEffects(val enabled: Boolean) : ThemePickerAction
    data class Palette(val id: com.v2ray.ang.ui.terminal.TerminalPaletteId) : ThemePickerAction
    data object Apply : ThemePickerAction
    data object Cancel : ThemePickerAction
}

/** Owns a local draft. The global store is touched only by Apply. */
class ThemeSelectionController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val store: AppearanceStore,
    private val savedState: SavedStateHandle,
) {
    private val initial = store.appearance.value
    private val _state = MutableStateFlow(ThemePickerState(AppearanceSelection(
        themeId = if (savedState.contains("theme_draft_id"))
            ThemeId.fromStorage(savedState["theme_draft_id"]) else initial.themeId,
        reduceEffects = savedState["theme_draft_reduce"] ?: initial.reduceEffects,
        terminalPalette = if(savedState.contains("terminal_draft")) com.v2ray.ang.ui.terminal.TerminalPaletteId.restore(savedState["terminal_draft"]) else initial.terminalPalette,
    )))
    val state: StateFlow<ThemePickerState> = _state.asStateFlow()

    fun onAction(action: ThemePickerAction) {
        val current = _state.value
        if (current.saving || current.applied || current.cancelled) return
        when (action) {
            is ThemePickerAction.Palette -> updateDraft(current.draft.copy(terminalPalette = action.id))
            is ThemePickerAction.Preview -> updateDraft(current.draft.copy(themeId = action.id))
            is ThemePickerAction.ReduceEffects -> updateDraft(current.draft.copy(reduceEffects = action.enabled))
            ThemePickerAction.Cancel -> {
                clearDraft()
                _state.value = ThemePickerState(store.appearance.value, cancelled = true)
            }
            ThemePickerAction.Apply -> {
                _state.value = current.copy(saving = true, saveFailed = false)
                scope.launch {
                    val success = try {
                        withContext(dispatcher) { store.saveAppearance(current.draft) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (success) clearDraft()
                    _state.value = current.copy(saving = false, saveFailed = !success, applied = success)
                }
            }
        }
    }

    private fun updateDraft(draft: AppearanceSelection) {
        savedState["terminal_draft"] = draft.terminalPalette.stableId
        savedState["theme_draft_id"] = draft.themeId.storageId
        savedState["theme_draft_reduce"] = draft.reduceEffects
        _state.value = ThemePickerState(draft)
    }

    private fun clearDraft() {
        savedState.remove<String>("terminal_draft")
        savedState.remove<String>("theme_draft_id")
        savedState.remove<Boolean>("theme_draft_reduce")
    }
}
