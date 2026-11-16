package com.v2ray.ang.ui.themes

import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.ui.compose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

class ThemeSelectionControllerTest {
    private class Store(id: ThemeId = ThemeId.Original) : AppearanceStore {
        override val appearance = MutableStateFlow(AppearanceSelection(id))
        var writes = 0
        var accepted = true
        var throws = false
        override fun saveAppearance(selection: AppearanceSelection): Boolean {
            writes++
            if (throws) error("storage unavailable")
            if (accepted) appearance.value = selection
            return accepted
        }
    }

    private fun exercise(test: (CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { test(scope) } finally { scope.cancel() }
    }

    @Test fun firstLaunchAndUnknownOrDamagedStoredIdsFallBackToOriginal() = exercise { scope ->
        for (value in listOf(null, "", "unknown", "2", "SAKURA", "\u0000aurora")) {
            val store = Store(ThemeId.fromStorage(value))
            val editor = ThemeSelectionController(scope, Dispatchers.Unconfined, store, SavedStateHandle())
            assertEquals(AppearanceSelection(), editor.state.value.draft)
            assertEquals(0, store.writes)
        }
    }

    @Test fun previewAndCancelNeverChangeTheSavedWorldAndReopeningReadsSavedChoice() = exercise { scope ->
        val store = Store(ThemeId.Aurora)
        val handle = SavedStateHandle()
        val editor = ThemeSelectionController(scope, Dispatchers.Unconfined, store, handle)
        editor.onAction(ThemePickerAction.Preview(ThemeId.Sakura))
        editor.onAction(ThemePickerAction.ReduceEffects(true))
        assertEquals(AppearanceSelection(ThemeId.Sakura, true), editor.state.value.draft)
        assertEquals(ThemeId.Aurora, store.appearance.value.themeId)
        assertEquals(0, store.writes)
        editor.onAction(ThemePickerAction.Cancel)
        assertTrue(editor.state.value.cancelled)
        assertEquals(ThemeId.Aurora, editor.state.value.draft.themeId)
        assertEquals(0, store.writes)
        val reopened = ThemeSelectionController(scope, Dispatchers.Unconfined, store, handle)
        assertEquals(store.appearance.value, reopened.state.value.draft)
        assertFalse(reopened.state.value.cancelled)
    }

    @Test fun applyPersistsStableIdOnceAndNewEditorRestoresIt() = exercise { scope ->
        val store = Store()
        val editor = ThemeSelectionController(scope, Dispatchers.Unconfined, store, SavedStateHandle())
        editor.onAction(ThemePickerAction.Preview(ThemeId.Cyberpunk))
        editor.onAction(ThemePickerAction.ReduceEffects(true))
        editor.onAction(ThemePickerAction.Apply)
        editor.onAction(ThemePickerAction.Apply)
        assertEquals(1, store.writes)
        assertTrue(editor.state.value.applied)
        val restored = ThemeId.fromStorage(store.appearance.value.themeId.storageId)
        assertEquals(ThemeId.Cyberpunk, restored)
        val reopened = ThemeSelectionController(scope, Dispatchers.Unconfined, store, SavedStateHandle())
        assertEquals(AppearanceSelection(restored, true), reopened.state.value.draft)
    }

    @Test fun recreationRestoresDraftByIdWithoutSavingItGlobally() = exercise { scope ->
        val store = Store()
        val handle = SavedStateHandle()
        val editor = ThemeSelectionController(scope, Dispatchers.Unconfined, store, handle)
        editor.onAction(ThemePickerAction.Preview(ThemeId.Sakura))
        editor.onAction(ThemePickerAction.ReduceEffects(true))
        val restoredHandle = SavedStateHandle(mapOf(
            "theme_draft_id" to handle.get<String>("theme_draft_id"),
            "theme_draft_reduce" to handle.get<Boolean>("theme_draft_reduce"),
        ))
        val recreated = ThemeSelectionController(scope, Dispatchers.Unconfined, store, restoredHandle)
        assertEquals(AppearanceSelection(ThemeId.Sakura, true), recreated.state.value.draft)
        assertEquals(AppearanceSelection(), store.appearance.value)
        assertEquals(0, store.writes)
        val unknown = ThemeSelectionController(scope, Dispatchers.Unconfined, store,
            SavedStateHandle(mapOf("theme_draft_id" to "missing-world")))
        assertEquals(ThemeId.Original, unknown.state.value.draft.themeId)
    }

    @Test fun rejectedWriteAndExceptionKeepDraftAndAllowRetry() = exercise { scope ->
        for (exception in listOf(false, true)) {
            val store = Store().apply { accepted = false; throws = exception }
            val editor = ThemeSelectionController(scope, Dispatchers.Unconfined, store, SavedStateHandle())
            editor.onAction(ThemePickerAction.Preview(ThemeId.Aurora))
            editor.onAction(ThemePickerAction.Apply)
            assertTrue(editor.state.value.saveFailed)
            assertFalse(editor.state.value.applied)
            assertFalse(editor.state.value.saving)
            assertEquals(ThemeId.Aurora, editor.state.value.draft.themeId)
            assertEquals(ThemeId.Original, store.appearance.value.themeId)
            store.accepted = true; store.throws = false
            editor.onAction(ThemePickerAction.Apply)
            assertEquals(2, store.writes)
            assertTrue(editor.state.value.applied)
            assertFalse(editor.state.value.saveFailed)
        }
    }

    @Test fun applyBlocksDuplicateWritesAndDraftChangesWhileSaving() = exercise { scope ->
        val queue = ArrayDeque<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        }
        val store = Store()
        val editor = ThemeSelectionController(scope, dispatcher, store, SavedStateHandle())
        editor.onAction(ThemePickerAction.Preview(ThemeId.Cyberpunk))
        editor.onAction(ThemePickerAction.Apply)
        assertTrue(editor.state.value.saving)
        editor.onAction(ThemePickerAction.Preview(ThemeId.Sakura))
        editor.onAction(ThemePickerAction.Apply)
        editor.onAction(ThemePickerAction.Cancel)
        assertEquals(0, store.writes)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertEquals(1, store.writes)
        assertEquals(ThemeId.Cyberpunk, store.appearance.value.themeId)
        assertTrue(editor.state.value.applied)
    }
}
