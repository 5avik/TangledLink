package com.v2ray.ang.ui.themes

import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.ThemeManager
import kotlinx.coroutines.Dispatchers

class ThemePickerViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val controller = ThemeSelectionController(viewModelScope, Dispatchers.IO, ThemeManager, savedStateHandle)
    val state = controller.state
    fun onAction(action: ThemePickerAction) = controller.onAction(action)
}

class ThemePickerActivity : BaseComponentActivity() {
    private val model: ThemePickerViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by model.state.collectAsStateWithLifecycle()
        BackHandler { model.onAction(ThemePickerAction.Cancel) }
        LaunchedEffect(state.applied, state.cancelled) {
            if (state.applied || state.cancelled) finish()
        }
        ThemePickerScreen(state, model::onAction)
    }
}
