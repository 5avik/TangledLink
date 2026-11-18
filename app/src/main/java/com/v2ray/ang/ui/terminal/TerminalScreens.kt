package com.v2ray.ang.ui.terminal
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.main.*

@Composable internal fun TerminalWindow(title:String, content:@Composable ColumnScope.()->Unit) {
 val c=LocalThemeConfig.current.scene
 Surface(color=c.portalFill,contentColor=c.onScene,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
   border=BorderStroke(1.dp,c.portalPrimary.copy(alpha=.7f)),modifier=Modifier.fillMaxWidth()){
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text(title,color=c.portalSecondary,fontFamily=TerminalFont,style=MaterialTheme.typography.labelLarge)
     content()
   }
 }
}
@Composable internal fun AsciiPenguin(running:Boolean, compact:Boolean=false, neutral:Boolean=false) {
 val idle="___       \n      /   \\      \n     | - - |     \n     |  v  |     \n    /|     |\\    \n   / |  _  | \\   \n  /__| (_) |__\\  \n     /     \\     \n    /__| |__\\\n"
 Text("      "+if(neutral)idle.replace("- -","o o") else if(running)idle.replace("- -","^ ^")else idle,softWrap=false,fontFamily=TerminalFont,
 fontSize=if(compact)10.sp else 14.sp,lineHeight=if(compact)12.sp else 16.sp,
 color=LocalThemeConfig.current.scene.portalPrimary,modifier=Modifier.clearAndSetSemantics{})
}
@Composable internal fun TerminalPrompt(){Text("tangled@android:~$",fontFamily=TerminalFont,color=LocalThemeConfig.current.scene.portalPrimary)}
@Composable internal fun TerminalConnectionPanel(state:MainUiState,enabled:Boolean,action:(MainAction)->Unit) {
 val accessibleLabel=stringResource(if(state.isRunning) R.string.terminal_disconnect else R.string.terminal_connect)
 TerminalWindow("vpn.service"){
  Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){AsciiPenguin(state.isRunning)}
  Text(stringResource(if(state.isRunning)R.string.terminal_connected else R.string.terminal_disconnected),fontFamily=TerminalFont)
  Button({action(MainAction.ToggleService)},Modifier.fillMaxWidth().heightIn(min=48.dp).semantics { contentDescription=accessibleLabel },
   enabled=enabled&&!state.isConnecting,
   colors=ButtonDefaults.buttonColors(containerColor=LocalThemeConfig.current.scene.portalPrimary,contentColor=LocalThemeConfig.current.scene.background),
   contentPadding=PaddingValues(12.dp)) {
   Text("> sudo tangledlink "+if(state.isRunning)"disconnect" else "connect",fontFamily=TerminalFont,fontSize=12.sp,
    modifier=Modifier.clearAndSetSemantics{})
  }
 }
}
@Composable internal fun TerminalHome(state:MainUiState,server:ServerRowUiModel?,action:(MainAction)->Unit,
 servers:()->Unit,settings:()->Unit,padding:PaddingValues,bottom:Dp) {
 BoxWithConstraints(Modifier.fillMaxSize()) {
 val wide=maxWidth>=600.dp || (state.doubleColumnDisplay && maxWidth>=480.dp)
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(floatingContentPadding(padding,bottom,16.dp,12.dp)),
 verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically){
   coil3.compose.AsyncImage(R.drawable.tngl_brand_mark,null,Modifier.width(32.dp).aspectRatio(256f/240f))
   Text(stringResource(R.string.app_name),Modifier.weight(1f).padding(8.dp),color=LocalThemeConfig.current.scene.onScene,fontFamily=TerminalFont)
   IconButton(settings){Icon(painterResource(R.drawable.ic_settings_24dp),stringResource(R.string.premium_settings),tint=LocalThemeConfig.current.scene.onScene)}
  }
  TerminalPrompt()
  state.accountLogin?.let { Text(it, fontFamily=TerminalFont, color=LocalThemeConfig.current.scene.onScene) }
  if(wide) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Column(Modifier.weight(1f)){TerminalConnectionPanel(state,server!=null,action)}
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
    TerminalServerCard(server,servers)
    TerminalTraffic(state.traffic)
   }
  } else {
   TerminalConnectionPanel(state,server!=null,action)
   TerminalServerCard(server,servers)
   TerminalTraffic(state.traffic)
  }
 }
 }
}
@Composable internal fun TerminalToolsScreen(model:TerminalState,handler:TerminalCommandHandler,action:(MainAction)->Unit,
 padding:PaddingValues,bottom:Dp) {
 val clipboard=LocalClipboardManager.current
 val appearance by com.v2ray.ang.ui.compose.ThemeManager.appearance.collectAsStateWithLifecycle()
 val config=TerminalThemes.config(appearance.terminalPalette)
 // Paired terminal colors are independent of light/dynamic Material surfaces.
 val dark=config.dark
 CompositionLocalProvider(LocalThemeConfig provides config) {
 MaterialTheme(colorScheme=dark,typography=MaterialTheme.typography) {
 Column(Modifier.fillMaxSize().background(config.scene.background).verticalScroll(rememberScrollState())
   .padding(padding).consumeWindowInsets(padding).imePadding().padding(start=16.dp,end=16.dp,bottom=bottom),
   verticalArrangement=Arrangement.spacedBy(12.dp)){
  TerminalPrompt()
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   listOf("console" to R.string.terminal_console_title,"fetch" to R.string.terminal_neofetch_title,"events" to R.string.terminal_logs_title).forEach{(page,id)->
    FilterChip(selected=model.page==page,onClick={action(MainAction.OpenTerminal(page))},
     label={Text(stringResource(id))},modifier=Modifier.heightIn(min=48.dp).testTag("terminal_page_$page"))
   }
  }
  TerminalWindow(stringResource(when(model.page){"fetch"->R.string.terminal_neofetch_title;"events"->R.string.terminal_logs_title;else->R.string.terminal_console_title})){
    if(model.page=="fetch"){AsciiPenguin(false,true,neutral=true);Text(handler.diagnostics(),fontFamily=TerminalFont)}
    else (if(model.page=="events")model.events else model.lines).forEach{ line ->
      Row(Modifier.semantics(mergeDescendants=true){}) {
       if(line.startsWith("[ERROR]")) Text("[ERROR] ",color=dark.error,fontFamily=TerminalFont)
       Text(line.removePrefix("[ERROR] "),fontFamily=TerminalFont,color=config.scene.onScene)
      }
    }
  }
  if(model.page=="console"){
   OutlinedTextField(model.input,{action(MainAction.TerminalInput(it))},Modifier.fillMaxWidth().onPreviewKeyEvent{
     if(it.type!=KeyEventType.KeyDown)false else when(it.key){
      Key.DirectionUp->{action(MainAction.TerminalHistory(-1));true}
      Key.DirectionDown->{action(MainAction.TerminalHistory(1));true}
      Key.Tab->{action(MainAction.TerminalComplete);true}
      Key.Enter->{action(MainAction.TerminalSubmit);true}
      else->false
     }},label={Text(stringResource(R.string.terminal_command_hint))},singleLine=true,
     keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),
     keyboardActions=KeyboardActions(onSend={action(MainAction.TerminalSubmit)}))
   Button({action(MainAction.TerminalSubmit)},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.terminal_run_command))}
   FlowRow {
    TextButton({action(MainAction.TerminalHistory(-1))}){Text(stringResource(R.string.terminal_history_up))}
    TextButton({action(MainAction.TerminalHistory(1))}){Text(stringResource(R.string.terminal_history_down))}
    TextButton({action(MainAction.TerminalComplete)}){Text(stringResource(R.string.terminal_complete))}
   }
   handler.suggestions().forEach { suggestion->TextButton({action(MainAction.TerminalInput(suggestion))}){Text(suggestion,fontFamily=TerminalFont)} }
   Text(stringResource(R.string.terminal_help_note))
  }
  TextButton({clipboard.setText(AnnotatedString(handler.diagnostics()))}){Text(stringResource(R.string.terminal_copy_diagnostics))}
 }
 }
 }
}
