package com.v2ray.ang.ui.terminal
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.input.key.Key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.main.*
import com.v2ray.ang.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
class TerminalUiTest {
 @get:Rule val compose=createComposeRule()
 private fun label(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
 private val previous=ThemeManager.appearance.value
 private val mode=ThemeManager.themeMode.value
 private val dynamic=ThemeManager.dynamicColorEnabled.value
 @After fun restore(){runBlocking(Dispatchers.IO){ThemeManager.saveAppearance(previous);ThemeManager.setThemeMode(mode);ThemeManager.setDynamicColorEnabled(dynamic)}}
 @Test fun terminalPalettesModesAndLargeTextKeepNavigationReachable(){
   val dark=mutableStateOf(true)
   val wide=mutableStateOf(false)
   var opens=0
   compose.setContent{
    DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if(wide.value)DpSize(640.dp,360.dp)else DpSize(320.dp,568.dp)) then DeviceConfigurationOverride.FontScale(1.6f)){
     AppTheme(darkTheme=dark.value){
      FloatingNavigationLayout("home",{} ){padding,bottom->
       TerminalHome(MainUiState(),null,{}, {},{}, {opens++},padding,bottom)
      }
     }
    }
   }
   for(p in TerminalPaletteId.entries)for(d in listOf(false,true))for(dyn in listOf(false,true)){
    runBlocking(Dispatchers.IO){ThemeManager.saveAppearance(AppearanceSelection(ThemeId.Terminal,false,p));ThemeManager.setDynamicColorEnabled(dyn)}
    compose.runOnIdle{dark.value=d;wide.value=dyn}
    compose.onNodeWithText(label(R.string.terminal_disconnected)).performScrollTo().assertIsDisplayed()
    compose.onNodeWithText(label(R.string.terminal_console_title)).performScrollTo().performClick()
   }
   compose.runOnIdle{assertEquals(16,opens)}
 }
 @Test fun consoleAcceptsImeAndKeyboardWithoutEchoingUnknownInput(){
   val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
   val handler=TerminalCommandHandler(scope,ThemeManager,{MainUiState()},{emptyList()},{false},{label(it)},"Android test","test")
   val dispatch:(MainAction)->Unit={action->when(action){
    is MainAction.TerminalInput->handler.input(action.value)
    is MainAction.TerminalHistory->handler.history(action.delta)
    MainAction.TerminalComplete->handler.complete()
    MainAction.TerminalSubmit->handler.execute{}
    else->Unit
   }}
   try{
    runBlocking(Dispatchers.IO){ThemeManager.saveAppearance(AppearanceSelection(ThemeId.Terminal,true))}
    compose.setContent{AppTheme{
     val state by handler.state.collectAsStateWithLifecycle()
     TerminalToolsScreen(state,handler,dispatch,{},PaddingValues(),0.dp)
    }}
    val field=compose.onNodeWithText(label(R.string.terminal_command_hint))
    field.performScrollTo().performTextInput("help")
    field.performImeAction()
    compose.runOnIdle{assertEquals("help",handler.state.value.history.last())}
    field.performScrollTo().performKeyInput{pressKey(Key.DirectionUp)}
    compose.runOnIdle{assertEquals("help",handler.state.value.input)}
    field.performKeyInput{pressKey(Key.DirectionDown)}
    compose.runOnIdle{assertEquals("",handler.state.value.input)}
    field.performTextInput("theme set tok")
    field.performKeyInput{pressKey(Key.Tab)}
    compose.runOnIdle{assertEquals("theme set tokyo_night",handler.state.value.input)}
    field.performKeyInput{pressKey(Key.Enter)}
    compose.waitUntil(5000){!handler.state.value.saving}
    compose.runOnIdle{assertEquals(TerminalPaletteId.TOKYO_NIGHT,ThemeManager.appearance.value.terminalPalette)}
    field.performScrollTo().performTextInput("secret-token; status")
    field.performImeAction()
    compose.onNodeWithText("secret-token",substring=true).assertDoesNotExist()
    compose.onNodeWithText("[ERROR]",substring=true).assertExists()
    field.performScrollTo().performTextInput("status")
    compose.onNodeWithText(label(R.string.terminal_run_command)).performScrollTo().performClick()
    compose.runOnIdle{assertEquals("status",handler.state.value.history.last())}
   }finally{scope.cancel()}
 }
}
