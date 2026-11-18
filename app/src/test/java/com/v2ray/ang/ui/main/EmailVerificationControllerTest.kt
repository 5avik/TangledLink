package com.v2ray.ang.ui.main
import com.v2ray.ang.handler.TnglAuthException
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class EmailVerificationControllerTest {
 @Test fun challengeValidationResendAndSuccess() {
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
  var now=1000L;var calls=0;var accepted=0
  val c=EmailVerificationController(scope,Dispatchers.Unconfined){now}
  try{
   assertFalse(c.uiState.value.visible)
   c.start({code->calls++;if(code==null)EmailResult(required=true) else {
    if(code!="123456")throw TnglAuthException("invalid_email_code")
    EmailResult(token="test-only",expiresAt=42)
   }}){accepted++}
   assertTrue(c.uiState.value.visible);assertEquals(61000L,c.uiState.value.resendAt)
   assertEquals(0,accepted)
   c.submit("");assertEquals(1,calls);assertEquals("invalid_email_code",c.uiState.value.error)
   c.submit("654321");assertEquals(0,accepted);assertTrue(c.uiState.value.visible)
   c.resend();assertEquals(2,calls)
   now=62000;c.resend();assertEquals(3,calls)
   c.submit("123456");assertEquals(1,accepted);assertFalse(c.uiState.value.visible)
   c.submit("123456");assertEquals(1,accepted)
  }finally{scope.cancel()}
 }
 @Test fun busyGateAndCancellationIgnoreLateToken() = runBlocking {
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
  val entered=java.util.concurrent.CountDownLatch(1)
  val release=java.util.concurrent.CountDownLatch(1)
  var accepted=0
  val c=EmailVerificationController(scope,Dispatchers.Default)
  try{
   c.start({entered.countDown();release.await();EmailResult(token="must-not-save")}){accepted++}
   assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS))
   assertTrue(c.uiState.value.busy)
   c.start({throw AssertionError("A duplicate operation must be gated")}){}
   val jobs=scope.coroutineContext[Job]!!.children.toList()
   c.cancel();release.countDown();jobs.forEach{it.join()}
   assertEquals(0,accepted);assertFalse(c.uiState.value.visible);assertFalse(c.uiState.value.busy)
  }finally{release.countDown();scope.cancel()}
 }
 @Test fun expiredNetworkWaitCancelAndDirectLegacyLogin() {
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
  val c=EmailVerificationController(scope,Dispatchers.Unconfined){1000L}
  try{
   c.start({EmailResult(token="test-only")}){}
   assertFalse(c.uiState.value.visible)
   c.start({code->if(code==null)EmailResult(required=true) else throw TnglAuthException("email_code_expired")}){}
   c.submit("123456");assertEquals("email_code_expired",c.uiState.value.error)
   c.cancel();assertNull(c.uiState.value.error);assertFalse(c.uiState.value.visible)
   c.start({throw java.io.IOException("not logged")}){}
   assertEquals("network",c.uiState.value.error)
   c.start({throw TnglAuthException("email_wait",120)}){}
   assertEquals(121000,c.uiState.value.resendAt)
   c.cancel()
  }finally{scope.cancel()}
 }
}
