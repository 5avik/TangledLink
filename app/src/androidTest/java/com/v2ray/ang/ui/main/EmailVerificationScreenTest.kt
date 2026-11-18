package com.v2ray.ang.ui.main
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
class EmailVerificationScreenTest {
 @get:Rule val compose=createComposeRule()
 @Test fun sixDigitsPasteEquivalentSubmissionErrorsAndResendGate(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val state=mutableStateOf(EmailVerificationState(visible=true,resendAt=System.currentTimeMillis()+60000))
  val actions=mutableListOf<MainAction>()
  lateinit var clipboard:androidx.compose.ui.platform.ClipboardManager
  compose.setContent {MaterialTheme{
   clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
   EmailVerificationScreen(state.value){actions.add(it)}
  }}
  val input=compose.onNodeWithText(context.getString(R.string.email_code))
  input.performClick()
  compose.runOnIdle {clipboard.setText(androidx.compose.ui.text.AnnotatedString("123456"))}
  input.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.PasteText){it()}
  compose.onNodeWithText(context.getString(R.string.email_verify)).performScrollTo().performClick()
  compose.runOnIdle{assertEquals(listOf(MainAction.SubmitEmailCode("123456")),actions)}
  compose.runOnIdle{state.value=state.value.copy(error="email_code_expired",resendAt=0)}
  compose.onNodeWithText(context.getString(R.string.email_expired_code)).assertExists()
  compose.onNodeWithText(context.getString(R.string.email_resend)).performScrollTo().performClick()
  compose.runOnIdle{assertEquals(MainAction.ResendEmailCode,actions.last())}
 }
}
