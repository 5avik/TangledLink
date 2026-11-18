package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.TnglAuthException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

typealias EmailResult = com.v2ray.ang.handler.EmailAuthResult
data class EmailVerificationState(val visible:Boolean=false,val busy:Boolean=false,val error:String?=null,val resendAt:Long=0)
class EmailVerificationController(
    private val scope:CoroutineScope, private val io:CoroutineDispatcher,
    private val clock:()->Long=System::currentTimeMillis
) {
    private val state=MutableStateFlow(EmailVerificationState())
    val uiState:StateFlow<EmailVerificationState> = state.asStateFlow()
    private var work:((String?)->EmailResult)?=null
    private var success:((EmailResult)->Unit)?=null
    private var job:Job?=null
    private var generation=0
    fun start(operation:(String?)->EmailResult,onSuccess:(EmailResult)->Unit) {
        if(state.value.busy)return
        cancel()
        work=operation;success=onSuccess
        perform(null)
    }
    fun submit(code:String) {
        if(!Regex("[0-9]{6}").matches(code)) {state.update{it.copy(error="invalid_email_code")};return}
        perform(code)
    }
    fun resend() {if(clock()>=state.value.resendAt)perform(null)}
    fun cancel() {
        generation++;job?.cancel();work=null;success=null;state.value=EmailVerificationState()
    }
    private fun perform(code:String?) {
        if(state.value.busy)return
        val operation=work?:return
        val version=generation
        state.update{it.copy(busy=true,error=null)}
        job=scope.launch {
            try {
                val result=withContext(io){operation(code)}
                if(version!=generation)return@launch
                if(result.required) state.value=EmailVerificationState(visible=true,resendAt=clock()+result.retrySeconds.coerceAtLeast(0)*1000)
                else {
                    val callback=success
                    work=null;success=null;state.value=EmailVerificationState()
                    callback?.invoke(result)
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){
                if(version==generation)state.update{it.copy(
                    error=(e as? TnglAuthException)?.errorCode?:"network",
                    resendAt=(e as? TnglAuthException)?.retryAfterSeconds?.let{clock()+it*1000}?:it.resendAt)}
            }finally{if(version==generation)state.update{it.copy(busy=false)}}
        }
    }
}
