package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar

@Composable
internal fun MainSettingsHub(
    onBack: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onAction: (MainAction) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(title = stringResource(R.string.title_settings), onBackClick = onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsSection(stringResource(R.string.hub_account)) {
                HubRow(R.string.title_sub_update, R.drawable.ic_refresh_24dp) { onAction(MainAction.UpdateSubscriptions) }
                HubRow(R.string.auth_change_password, R.drawable.ic_lock_24dp) { onAction(MainAction.ShowPasswordDialog) }
                HubRow(R.string.tngl_change_account, R.drawable.ic_restore_24dp) { onAction(MainAction.ChangeAccount) }
            }
            SettingsSection(stringResource(R.string.hub_app)) {
                listOf(MainDestination.Settings, MainDestination.PerAppProxy, MainDestination.Routing,
                    MainDestination.UserAssets).forEach { item ->
                    HubRow(item.labelRes, item.iconRes) { onNavigate(item) }
                }
            }
            SettingsSection(stringResource(R.string.hub_tools)) {
                listOf(MainDestination.Logcat, MainDestination.BackupRestore, MainDestination.About).forEach { item ->
                    HubRow(item.labelRes, item.iconRes) { onNavigate(item) }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(8.dp), content = content)
        }
    }
}

@Composable
private fun HubRow(label: Int, icon: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun MainSettingsButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 52.dp),
        shape = RoundedCornerShape(50)) {
        Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.title_settings))
    }
}
