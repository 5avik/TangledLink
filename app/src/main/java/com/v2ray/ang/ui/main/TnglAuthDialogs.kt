package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
private fun AuthField(value: String, onValueChange: (String) -> Unit, label: Int, busy: Boolean, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(stringResource(label)) }, singleLine = true,
        enabled = !busy, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii)
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
    onConfirm: (String, String, String?) -> Unit
) {
    var id by rememberSaveable { mutableStateOf("") }
    var activation by rememberSaveable { mutableStateOf(false) }
    // Passwords and one-time codes deliberately stay out of saved instance state.
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.tngl_login_dialog_title)) },
        text = {
            Column(Modifier.imePadding().consumeWindowInsets(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (activation) R.string.auth_activation_help else R.string.tngl_login_dialog_message))
                AuthField(id, { id = it }, R.string.auth_id, busy)
                if (activation) AuthField(code, { code = it }, R.string.auth_activation_code, busy, secret = true)
                AuthField(password, { password = it }, if (activation) R.string.auth_new_password else R.string.auth_password, busy, secret = true)
                AuthError(error)
                TextButton(enabled = !busy, onClick = {
                    activation = !activation
                    password = ""
                    code = ""
                }) {
                    Text(stringResource(if (activation) R.string.auth_login_mode else R.string.auth_activate))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onConfirm(id, password, if (activation) code else null) }) {
                Text(stringResource(if (busy) R.string.auth_working else if (activation) R.string.auth_activate_submit else R.string.tngl_login_button))
            }
        }
    )
}

@Composable
fun TnglPasswordDialog(busy: Boolean, error: Int?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.auth_change_password)) },
        text = {
            Column(Modifier.imePadding().consumeWindowInsets(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthField(current, { current = it }, R.string.auth_current_password, busy, secret = true)
                AuthField(next, { next = it }, R.string.auth_new_password, busy, secret = true)
                AuthError(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onConfirm(current, next) }) {
                Text(stringResource(if (busy) R.string.auth_working else R.string.acc_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.auth_cancel)) }
        }
    )
}
