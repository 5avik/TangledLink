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
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import kotlinx.coroutines.delay

internal data class RecoveryState(val step:String="login",val busy:Boolean=false,
    val error:String?=null,val resendAt:Long=0)

internal fun recoveryResponseStep(step: String, sent: Boolean, ok: Boolean, submittingCode: Boolean): String? =
    when {
        submittingCode && ok -> "done"
        submittingCode -> null
        step == "code" && sent -> "code"
        step == "activation" || step == "support" -> step
        else -> null
    }

@Composable
internal fun RecoveryDialog(state:RecoveryState,action:(MainAction)->Unit) {
    var id by rememberSaveable {mutableStateOf("")}
    var email by remember {mutableStateOf("")}
    var invite by remember {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf("")}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.resendAt) {
        now=System.currentTimeMillis()
        while(now<state.resendAt){delay(1000);now=System.currentTimeMillis()}
    }
    val wait=((state.resendAt-now+999)/1000).coerceAtLeast(0)
    AlertDialog(onDismissRequest={action(MainAction.CloseRecovery)},
        title={Text(stringResource(R.string.recovery_title))},
        text={
            Column(Modifier.imePadding().consumeWindowInsets(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                when(state.step){
                    "login" -> RecoveryField(id,{id=it},R.string.auth_id,!state.busy)
                    "activation" -> {
                        Text(stringResource(R.string.recovery_activation_help))
                        RecoveryField(invite,{invite=it},R.string.auth_activation_code,!state.busy,KeyboardType.Password)
                        RecoveryField(email,{email=it},R.string.email_address,!state.busy,KeyboardType.Email)
                    }
                    "code" -> {
                        Text(stringResource(R.string.email_code_help))
                        RecoveryField(code,{code=it.filter{c->c in '0'..'9'}.take(6)},R.string.email_code,!state.busy,KeyboardType.Number)
                        RecoveryField(password,{password=it},R.string.auth_new_password,!state.busy,KeyboardType.Password)
                        RecoveryField(confirmation,{confirmation=it},R.string.recovery_confirmation,!state.busy,KeyboardType.Password)
                        TextButton(onClick={action(MainAction.RequestRecovery(id))},enabled=!state.busy&&wait==0L){
                            Text(if(wait==0L)stringResource(R.string.email_resend) else stringResource(R.string.email_resend_wait,wait))
                        }
                    }
                    "support" -> Text(stringResource(R.string.recovery_support))
                    "done" -> Text(stringResource(R.string.recovery_done))
                }
                val error=when(state.error){
                    "recovery_unavailable" -> R.string.recovery_unavailable
                    "recovery_unconfirmed" -> R.string.recovery_unconfirmed
                    "password_mismatch"->R.string.recovery_mismatch
                    "invalid_id","invalid_credentials","recovery_changed"->R.string.recovery_identity_error
                    else->emailError(state.error)
                }
                error?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
                if (state.step != "code" && wait > 0)
                    Text(stringResource(R.string.email_resend_wait, wait))
                if (state.step == "support" || state.error == "recovery_unavailable") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val address = stringResource(R.string.recovery_support_address)
                    var unavailable by remember { mutableStateOf(false) }
                    TextButton(onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO,
                                android.net.Uri.fromParts("mailto", address, null)))
                        } catch (_: android.content.ActivityNotFoundException) { unavailable = true }
                    }) { Text(address) }
                    if (unavailable) Text(stringResource(R.string.recovery_mail_app_missing))
                }
            }
        },
        confirmButton={
            val enabled=when(state.step){
                "login"->id.isNotBlank()
                "activation"->invite.isNotBlank()&&email.isNotBlank()
                "code"->code.length==6&&password.isNotEmpty()&&confirmation.isNotEmpty()
                else->true
            }
            TextButton(enabled=enabled&&!state.busy&&(state.step !in listOf("login","activation") || wait==0L),onClick={
                when(state.step){
                    "login"->action(MainAction.RequestRecovery(id))
                    "activation"->action(MainAction.RequestRecovery(id,email.trim(),invite.trim()))
                    "code"->action(MainAction.FinishRecovery(code,password,confirmation))
                    else->action(MainAction.CloseRecovery)
                }
            }){Text(stringResource(when{
                state.busy->R.string.auth_working
                state.step=="done"||state.step=="support"->R.string.tngl_login_button
                state.step=="code"->R.string.email_verify
                else->R.string.recovery_continue
            }))}
        },
        dismissButton={TextButton(onClick={action(MainAction.CloseRecovery)}){Text(stringResource(R.string.auth_cancel))}})
}

@Composable
private fun RecoveryField(value:String,change:(String)->Unit,label:Int,enabled:Boolean,type:KeyboardType=KeyboardType.Ascii){
    OutlinedTextField(value,change,Modifier.fillMaxWidth(),enabled=enabled,singleLine=true,
        label={Text(stringResource(label))},keyboardOptions=KeyboardOptions(keyboardType=type),
        visualTransformation=if(type==KeyboardType.Password)PasswordVisualTransformation() else VisualTransformation.None)
}
