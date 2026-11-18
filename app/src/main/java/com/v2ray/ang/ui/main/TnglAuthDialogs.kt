package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.BrandButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
private fun AuthField(value: String, onValueChange: (String) -> Unit, label: Int, busy: Boolean, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(stringResource(label)) }, singleLine = true,
        enabled = !busy, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else if (label == R.string.email_address) KeyboardType.Email else KeyboardType.Ascii)
    )
}

@Composable
private fun AuthError(error: Int?) {
    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}

@Composable
fun TnglLoginDialog(
    busy: Boolean = false,
    error: Int? = null,
    onRegister: ((String, String, String) -> Unit)? = null,
    portalLoading: Boolean = false,
    onForgot: (() -> Unit)? = null,
    onConfirm: (String, String, String?) -> Unit
) {
    var id by rememberSaveable { mutableStateOf("") }
    var registration by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords and one-time codes deliberately stay out of saved instance state.
    var password by remember { mutableStateOf("") }
    val motion = com.v2ray.ang.ui.compose.sceneMotionAllowed()
    val dim = animateFloatAsState(if (portalLoading) .50f else 1f,
        tween(if (motion) 300 else 0), label = "authorization dim")
    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF65FF85),
        secondary = androidx.compose.ui.graphics.Color(0xFF00DDB5),
        tertiary = androidx.compose.ui.graphics.Color(0xFF00DDB5),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF020B09),
        background = androidx.compose.ui.graphics.Color(0xFF020B09),
        surface = androidx.compose.ui.graphics.Color(0xFF0B1713),
        onSurface = androidx.compose.ui.graphics.Color(0xFFF5F7F6),
        onBackground = androidx.compose.ui.graphics.Color(0xFFF5F7F6),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB5C7BE)
    )) {
    Box(Modifier.fillMaxSize()) {
    com.v2ray.ang.ui.compose.TangledAnimatedBackground(Modifier.matchParentSize())
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)
            .consumeWindowInsets(innerPadding).imePadding()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    AuthPortalLogo(portalLoading, error != null, motion)
                    if (portalLoading) Text(stringResource(R.string.auth_portal_checking),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = dim.value },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(stringResource(if (registration) R.string.auth_registration else R.string.tngl_login_dialog_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.White)
                    Text(stringResource(if (registration) R.string.registration_id_rule else R.string.tngl_login_dialog_message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AuthField(id, { id = it }, R.string.auth_id, busy)
                    AuthField(password, { password = it }, R.string.auth_password, busy, secret = true)
                    if(registration) AuthField(email,{email=it},R.string.email_address,busy)
                    AuthError(error)
                    }
                    BrandButton(modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        onClick = { if (registration) onRegister?.invoke(id,password,email) else onConfirm(id, password, null) }) {
                        Text(stringResource(if (busy) R.string.auth_working else if (registration) R.string.registration_submit else R.string.tngl_login_button))
                    }
                    if (!registration && onForgot != null) TextButton(onClick=onForgot,enabled=!busy) {
                        Text(stringResource(R.string.recovery_title))
                    }
                    OutlinedButton(onClick = {
                        registration = !registration; password = ""
                    }, enabled = !busy && onRegister != null, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (registration) R.string.auth_login_mode else R.string.auth_registration))
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
fun TnglPasswordDialog(busy: Boolean, error: Int?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.auth_change_password)) },
        text = {
            Column(Modifier.animateContentSize(tween(250)).imePadding().consumeWindowInsets(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthField(current, { current = it }, R.string.auth_current_password, busy, secret = true)
                AuthField(next, { next = it }, R.string.auth_new_password, busy, secret = true)
                AuthError(error)
            }
        },
        confirmButton = {
            BrandButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { onConfirm(current, next) }) {
                Text(stringResource(if (busy) R.string.auth_working else R.string.acc_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.auth_cancel)) }
        }
    )
}
