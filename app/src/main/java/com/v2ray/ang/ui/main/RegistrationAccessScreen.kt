package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
internal fun RegistrationAccessScreen(state: MainUiState, action:(MainAction)->Unit, insets:PaddingValues, bottom:Dp) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(floatingContentPadding(insets,bottom,20.dp,20.dp)),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.premium_servers),style=MaterialTheme.typography.headlineMedium)
        Card {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.registration_no_subscription),style=MaterialTheme.typography.titleLarge)
                Text(stringResource(if(state.accountAccess.trialAvailable)
                    R.string.registration_trial_help else R.string.registration_trial_unavailable))
                if(state.accountAccess.trialAvailable) Button(
                    onClick={action(MainAction.StartTrial)},enabled=!state.trialBusy,
                    modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(if(state.trialBusy) R.string.auth_working else R.string.registration_trial_start))
                }
                Button(onClick={action(MainAction.BeginBilling(1))},enabled=!state.trialBusy,
                    modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.registration_buy))}
                TextButton(onClick={action(MainAction.RefreshAccount)},enabled=!state.trialBusy) {
                    Text(stringResource(R.string.premium_refresh))
                }
                state.trialError?.let {Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
            }
        }
    }
}
