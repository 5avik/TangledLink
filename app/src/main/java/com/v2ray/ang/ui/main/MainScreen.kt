package com.v2ray.ang.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()
    PremiumTheme {
        if (state.showTnglLoginDialog) {
            TnglLoginDialog(busy=state.authBusy,error=state.authError,
                onRegister={id,password->onAction(MainAction.SubmitTnglCredentials(id,password,null,registration=true))},
                onConfirm={id,password,code->onAction(MainAction.SubmitTnglCredentials(id,password,code))})
        } else {
            PremiumShell(mainViewModel, onAction, onNavigate)
        }
        if (state.showPasswordDialog) {
            TnglPasswordDialog(busy=state.authBusy,error=state.authError,
                onDismiss={onAction(MainAction.DismissPasswordDialog)},
                onConfirm={current,next->onAction(MainAction.ChangePassword(current,next))})
        }
        state.updateResult?.takeIf {state.showTnglUpdateDialog}?.let { result ->
            TnglUpdateDialog(result,onConfirmUpdate={onAction(MainAction.DownloadUpdate(it))},
                onDismiss={onAction(MainAction.DismissUpdateDialog)})
        }
    }
}
