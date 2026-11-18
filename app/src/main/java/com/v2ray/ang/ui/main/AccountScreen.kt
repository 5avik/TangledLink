package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun AccountScreen(
    login:String?, info:SubscriptionUsageInfo?, action:(MainAction)->Unit, back:()->Unit,
    contentInsets:PaddingValues=PaddingValues(0.dp), bottomPadding:Dp=0.dp,
    verifiedEmail:String?=null, emailLoaded:Boolean=false,
) {
    androidx.compose.runtime.LaunchedEffect(login){if(login!=null)action(MainAction.RefreshEmail)}
    val colors=MaterialTheme.colorScheme
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding=floatingContentPadding(contentInsets,bottomPadding,24.dp,24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=back) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp),
                        stringResource(R.string.premium_account_back))
                }
                Text(stringResource(R.string.premium_manage_account),
                    style=MaterialTheme.typography.headlineSmall,
                    modifier=Modifier.weight(1f).semantics {heading()})
            }
        }
        item {
            Surface(shape=RoundedCornerShape(26.dp),color=colors.surface,
                border=BorderStroke(1.dp,colors.primary.copy(alpha=.25f))) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(
                    listOf(colors.primary.copy(alpha=.12f),colors.surface,colors.surface)))
                    .padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Surface(shape=CircleShape,color=colors.primaryContainer,
                            modifier=Modifier.size(56.dp).clearAndSetSemantics {}) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(login?.firstOrNull()?.uppercaseChar()?.toString() ?: "—",
                                    style=MaterialTheme.typography.headlineSmall,color=colors.primary)
                            }
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.premium_subscription_id),
                                style=MaterialTheme.typography.labelLarge,color=colors.onSurfaceVariant)
                            Text(login ?: stringResource(R.string.premium_signed_out),
                                style=MaterialTheme.typography.headlineSmall)
                        }
                    }
                    if(login!=null) {
                        Surface(shape=RoundedCornerShape(12.dp),color=colors.primary.copy(alpha=.10f)) {
                            Text(stringResource(R.string.premium_signed_in),
                                Modifier.padding(horizontal=12.dp,vertical=8.dp),
                                style=MaterialTheme.typography.labelLarge,color=colors.primary)
                        }
                        HorizontalDivider(color=colors.outlineVariant)
                        if(info==null) Text(stringResource(R.string.premium_unknown_subscription),
                            style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        else {
                            val expiry=info.expire*1000L
                            val status=when {
                                expiry>0 && expiry<=System.currentTimeMillis()->R.string.premium_expired
                                info.total>0 && info.used>=info.total->R.string.premium_depleted
                                else->R.string.premium_active
                            }
                            Text(stringResource(status),style=MaterialTheme.typography.bodyMedium,
                                color=if(status==R.string.premium_active) colors.primary else colors.error)
                            if(expiry>0) Text(stringResource(R.string.premium_valid_until,
                                SimpleDateFormat("dd.MM.yyyy",locale).format(Date(expiry))),
                                style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            if(verifiedEmail!=null) Text(stringResource(R.string.email_confirmed,verifiedEmail),color=colors.onSurface)
            else SettingsItem(stringResource(if(emailLoaded)R.string.email_bind else R.string.email_refresh),null,
                {action(if(emailLoaded)MainAction.ShowBindEmail else MainAction.RefreshEmail)},enabled=login!=null)
        }
        item { SettingsItem(stringResource(R.string.premium_password),null,
            {action(MainAction.ShowPasswordDialog)},enabled=login!=null,icon=R.drawable.ic_lock_24dp) }
        item { SettingsItem(stringResource(R.string.premium_account),null,
            {action(MainAction.ChangeAccount)}) }
    }
}
