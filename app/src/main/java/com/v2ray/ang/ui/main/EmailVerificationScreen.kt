package com.v2ray.ang.ui.main
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import com.v2ray.ang.R
import kotlinx.coroutines.delay

internal fun emailError(code:String?):Int?=when(code){
 null->null
 "invalid_email_code"->R.string.email_invalid_code
 "email_code_expired"->R.string.email_expired_code
 "email_attempts_exceeded"->R.string.email_attempts
 "email_wait"->R.string.email_wait
 "invalid_email"->R.string.billing_invalid_email
 "rate_limited"->R.string.auth_rate_limited
 "session_expired"->R.string.auth_session_expired
 "registration_pending"->R.string.registration_retry
 "invalid_credentials"->R.string.auth_invalid_credentials
 "account_exists"->R.string.registration_exists
 "password_policy"->R.string.auth_password_policy
 "email_already_bound"->R.string.email_already_bound
 "registration_invalid_id"->R.string.registration_id_rule
 else->R.string.email_network_error
}
@Composable internal fun EmailVerificationScreen(state:EmailVerificationState,action:(MainAction)->Unit){
 var code by remember {mutableStateOf("")}
 var now by remember {mutableLongStateOf(System.currentTimeMillis())}
 val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
 LaunchedEffect(lifecycle,state.resendAt){
  while(lifecycle==Lifecycle.State.RESUMED){now=System.currentTimeMillis();delay(1000)}
 }
 val seconds=((state.resendAt-now+999)/1000).coerceAtLeast(0)
 Dialog(onDismissRequest={action(MainAction.CancelEmailCode)},properties=DialogProperties(usePlatformDefaultWidth=false)){
  Scaffold {padding->
   Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
    .verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text(stringResource(R.string.email_code_title),style=MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.email_code_help))
    OutlinedTextField(code,{code=it.filter{c->c in '0'..'9'}.take(6)},label={Text(stringResource(R.string.email_code))},
     singleLine=true,enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
     modifier=Modifier.fillMaxWidth())
    emailError(state.error)?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
    Button(onClick={action(MainAction.SubmitEmailCode(code));code=""},enabled=!state.busy&&code.length==6,
     modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.email_verify))}
    OutlinedButton(onClick={action(MainAction.ResendEmailCode)},enabled=!state.busy&&seconds==0L,
     modifier=Modifier.fillMaxWidth()){
     Text(if(seconds>0)stringResource(R.string.email_resend_wait,seconds) else stringResource(R.string.email_resend))
    }
    TextButton(onClick={action(MainAction.CancelEmailCode)}){Text(stringResource(R.string.auth_cancel))}
   }
  }
 }
}
@Composable internal fun BindEmailDialog(busy:Boolean,error:String?,action:(MainAction)->Unit){
 var address by remember {mutableStateOf("")}
 Dialog(onDismissRequest={action(MainAction.DismissBindEmail)}){
  Surface {
   Column(Modifier.imePadding().consumeWindowInsets(WindowInsets.navigationBars)
    .verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text(stringResource(R.string.email_bind),style=MaterialTheme.typography.titleLarge)
    OutlinedTextField(address,{address=it},label={Text(stringResource(R.string.email_address))},
      singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
    emailError(error)?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
    Button(onClick={action(MainAction.BindEmail(address))},enabled=!busy){Text(stringResource(R.string.email_send))}
    TextButton(onClick={action(MainAction.DismissBindEmail)}){Text(stringResource(R.string.auth_cancel))}
   }
  }
 }
}
