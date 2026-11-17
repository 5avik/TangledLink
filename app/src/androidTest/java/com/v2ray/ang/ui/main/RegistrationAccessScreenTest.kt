package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.handler.AccountAccess
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegistrationAccessScreenTest {
    @get:Rule val compose=createComposeRule()
    @Test fun trialAndPurchaseUseTheirOwnActionsAndBusyDisablesTrial() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val state=mutableStateOf(MainUiState(accountAccess=AccountAccess(true,false,true)))
        val actions=mutableListOf<MainAction>()
        lateinit var inputMode: androidx.compose.ui.input.InputModeManager
        compose.setContent {
            MaterialTheme {
                inputMode=androidx.compose.ui.platform.LocalInputModeManager.current
                RegistrationAccessScreen(state.value,{actions.add(it)},PaddingValues(),0.dp)
            }
        }
        val trial=compose.onNodeWithText(context.getString(R.string.registration_trial_start))
        trial.performScrollTo().performClick()
        compose.runOnIdle {inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)}
        trial.performSemanticsAction(SemanticsActions.RequestFocus){it()}
        trial.assertIsFocused()
        trial.performKeyInput {pressKey(Key.Enter)}
        trial.performKeyInput {pressKey(Key.DirectionCenter)}
        compose.onNodeWithText(context.getString(R.string.registration_buy)).performScrollTo().performClick()
        compose.runOnIdle {assertEquals(listOf(MainAction.StartTrial,MainAction.StartTrial,MainAction.StartTrial,MainAction.BeginBilling(1)),actions)}
        compose.runOnIdle {state.value=state.value.copy(trialBusy=true)}
        compose.onNodeWithText(context.getString(R.string.auth_working)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {state.value=state.value.copy(trialBusy=false,accountAccess=AccountAccess(true,false,false),trialError=R.string.auth_server_error)}
        compose.onNodeWithText(context.getString(R.string.registration_trial_start)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.auth_server_error)).performScrollTo().assertIsDisplayed()
        compose.onRoot().printToLog("RegistrationAccess")
    }
}
