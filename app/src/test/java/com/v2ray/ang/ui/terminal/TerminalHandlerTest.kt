package com.v2ray.ang.ui.terminal
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.themes.*
import com.v2ray.ang.ui.main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalHandlerTest {
 private class Store:AppearanceStore {
  override val appearance=MutableStateFlow(AppearanceSelection())
  var accepted=true
  var fail=false
  override fun saveAppearance(selection:AppearanceSelection):Boolean {if(fail)throw IllegalStateException("storage");if(accepted)appearance.value=selection;return accepted}
 }
 @Test fun gateNeverTogglesTwiceAndOnlyConfirmedResultsReleaseIt() {
  val g=ConnectionRequestGate()
  assertTrue(g.acquire(false));assertFalse(g.acquire(false))
  g.observed(false);assertTrue(g.pending)
  g.finished();assertFalse(g.pending)
  assertTrue(g.acquire(false));g.observed(true);assertFalse(g.pending)
  assertTrue(g.acquire(true));assertFalse(g.acquire(true));g.observed(false);assertFalse(g.pending)
 }
 @Test fun handlerIsIdempotentRejectsSecretsBoundsBuffersAndPreservesWorld()=runBlocking {
  val store=Store();var current=MainUiState();val g=ConnectionRequestGate()
  val h=TerminalCommandHandler(this,store,{current},{emptyList()},{g.pending},{it.toString()},"test Android","test")
  var toggles=0
  val dispatch:(MainAction)->Unit={if(it==MainAction.ToggleService){assertTrue(g.acquire(current.isRunning));toggles++}}
  fun run(s:String){h.input(s);h.execute(dispatch)}
  run("disconnect");assertEquals(0,toggles)
  run("connect");run("connect");run("disconnect");assertEquals(1,toggles)
  current=current.copy(isRunning=true);g.observed(true)
  run("connect");assertEquals(1,toggles)
  run("disconnect");assertEquals(2,toggles);g.finished()
  for(secret in listOf("https://token@example.com", "password=private-secret","connect;rm secret","server select private-secret")){
   run(secret)
   assertFalse(h.state.value.lines.any{it.contains("private-secret")||it.contains("token@")})
   assertFalse(h.state.value.history.any{it.contains("private-secret")||it.contains("token@")})
  }
  repeat(500){run("status")}
  assertEquals(50,h.state.value.history.size);assertEquals(400,h.state.value.lines.size)
  h.history(-1);assertEquals("status",h.state.value.input)
  h.input("theme set tok");h.complete();assertEquals("theme set tokyo_night",h.state.value.input)
  run("theme set tokyo_night")
  withTimeout(5000){h.state.first{!it.saving}}
  assertEquals(ThemeId.Original,store.appearance.value.themeId)
  assertEquals(TerminalPaletteId.TOKYO_NIGHT,store.appearance.value.terminalPalette)
  store.accepted=false;run("theme set gruvbox_dark");withTimeout(5000){h.state.first{!it.saving}}
  assertEquals(TerminalPaletteId.TOKYO_NIGHT,store.appearance.value.terminalPalette)
  run("stats");assertTrue(h.state.value.lines.last().all{it.isDigit()})
  run("clear");assertTrue(h.state.value.lines.isEmpty())
  h.open("events");repeat(600){h.observe(it%2==0)}
  assertEquals(100,h.state.value.events.size)
 }
 @Test fun selectionRechecksCurrentRowsAndDiagnosticsUseOnlySafeFields()=runBlocking {
  val store=Store()
  val profile=com.v2ray.ang.dto.entities.ProfileItem(configType=com.v2ray.ang.enums.EConfigType.VLESS)
  var rows=listOf(ServerRowUiModel("server-guid",profile,"private-label","private-statistics","private-type",42,""))
  val current=MainUiState(selectedGuid="server-guid")
  val h=TerminalCommandHandler(this,store,{current},{rows},{false},{it.toString()},"Android model","test")
  val dispatched=mutableListOf<MainAction>()
  fun run(command:String){h.input(command);h.execute{dispatched.add(it)}}
  profile.password="private-password";profile.server="private-host";profile.username="private-user"
  run("server select server-guid")
  assertEquals(listOf(MainAction.SelectServer("server-guid")),dispatched)
  rows=emptyList()
  run("server select server-guid")
  assertEquals(1,dispatched.size)
  val diagnostic=h.diagnostics()
  assertFalse(diagnostic.contains("private"))
  assertTrue(diagnostic.contains("Android"))
  store.fail=true
  run("theme set tokyo_night")
  withTimeout(5000){h.state.first{!it.saving}}
  assertEquals(TerminalPaletteId.MATRIX_GREEN,store.appearance.value.terminalPalette)
  assertTrue(h.state.value.lines.last().startsWith("[ERROR]"))
 }
 @Test fun terminalDraftSurvivesRecreationAndFailedApplyCanRetry() {
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
  try {
   val store=Store();val saved=SavedStateHandle()
   val editor=ThemeSelectionController(scope,Dispatchers.Unconfined,store,saved)
   editor.onAction(ThemePickerAction.Preview(ThemeId.Terminal))
   editor.onAction(ThemePickerAction.Palette(TerminalPaletteId.TOKYO_NIGHT))
   val recreated=ThemeSelectionController(scope,Dispatchers.Unconfined,store,saved)
   assertEquals(ThemeId.Terminal,recreated.state.value.draft.themeId)
   assertEquals(TerminalPaletteId.TOKYO_NIGHT,recreated.state.value.draft.terminalPalette)
   assertEquals(AppearanceSelection(),store.appearance.value)
   store.fail=true
   recreated.onAction(ThemePickerAction.Apply)
   assertTrue(recreated.state.value.saveFailed)
   assertFalse(recreated.state.value.applied)
   store.fail=false
   recreated.onAction(ThemePickerAction.Apply)
   assertTrue(recreated.state.value.applied)
   assertEquals(TerminalPaletteId.TOKYO_NIGHT,store.appearance.value.terminalPalette)
  } finally{scope.cancel()}
 }
 @Test fun previewCancelAndApplyRestoreBothIds() {
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
  try {
   val store=Store()
   var editor=ThemeSelectionController(scope,Dispatchers.Unconfined,store,SavedStateHandle())
   editor.onAction(ThemePickerAction.Preview(ThemeId.Terminal))
   editor.onAction(ThemePickerAction.Palette(TerminalPaletteId.GRUVBOX_DARK))
   editor.onAction(ThemePickerAction.Cancel)
   assertEquals(AppearanceSelection(),store.appearance.value)
   editor=ThemeSelectionController(scope,Dispatchers.Unconfined,store,SavedStateHandle())
   editor.onAction(ThemePickerAction.Preview(ThemeId.Terminal))
   editor.onAction(ThemePickerAction.Palette(TerminalPaletteId.CATPPUCCIN_MOCHA))
   editor.onAction(ThemePickerAction.Apply)
   assertEquals(ThemeId.Terminal,store.appearance.value.themeId)
   assertEquals(TerminalPaletteId.CATPPUCCIN_MOCHA,store.appearance.value.terminalPalette)
   val reopened=ThemeSelectionController(scope,Dispatchers.Unconfined,store,SavedStateHandle())
   reopened.onAction(ThemePickerAction.Preview(ThemeId.Original));reopened.onAction(ThemePickerAction.Apply)
   assertEquals(TerminalPaletteId.CATPPUCCIN_MOCHA,store.appearance.value.terminalPalette)
  } finally{scope.cancel()}
 }
}
