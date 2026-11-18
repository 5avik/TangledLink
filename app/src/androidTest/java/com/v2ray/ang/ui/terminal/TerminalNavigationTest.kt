package com.v2ray.ang.ui.terminal

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.main.*
import kotlinx.coroutines.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.junit.*
import org.junit.Assert.*

/** Production navigation and console, without a network request or service start. */
class TerminalNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val world = mutableStateOf(ThemeId.Terminal)
    private val sequence = mutableLongStateOf(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var toggles = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
    private var imeSettled = false
    private lateinit var inputMode: androidx.compose.ui.input.InputModeManager
    private val server = ServerRowUiModel("qa-guid", ProfileItem(configType=EConfigType.VLESS),
        "🇷🇴 №3 gRPC", "", "", 12, "")
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val handler = TerminalCommandHandler(scope, ThemeManager,
        { MainUiState(selectedGuid=server.guid) }, { listOf(server) }, { false }, {label(it)}, "Android QA", "test")
    private val dispatch: (MainAction) -> Unit = { action ->
        when (action) {
            is MainAction.OpenTerminal -> handler.open(action.page)
            is MainAction.TerminalInput -> handler.input(action.value)
            is MainAction.TerminalHistory -> handler.history(action.delta)
            MainAction.TerminalComplete -> handler.complete()
            MainAction.TerminalSubmit -> handler.execute { if(it==MainAction.ToggleService) toggles++ }
            MainAction.ToggleService -> toggles++
            else -> Unit
        }
    }
    @After fun cleanup() { scope.cancel() }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun Fixture() {
        inputMode = LocalInputModeManager.current
        backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
        keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val density=androidx.compose.ui.platform.LocalDensity.current
        val ime=WindowInsets.ime.getBottom(density)
        imeSettled=ime>0 && ime==WindowInsets.imeAnimationTarget.getBottom(density)
        val config = ThemeCatalog[world.value]
        AppTheme {
            CompositionLocalProvider(LocalThemeConfig provides config) {
                val state by handler.state.collectAsStateWithLifecycle()
                PremiumTabNavigation(sequence.longValue, handler::open) { route, select, back, padding, bottom ->
                    val terminal = world.value==ThemeId.Terminal
                    val openServers = { select(if(terminal) "serverPicker" else "servers") }
                    Box(Modifier.fillMaxSize().testTag("page_$route")) {
                        when(route) {
                            "home" -> if(terminal) TerminalHome(MainUiState(),server,dispatch,
                                openServers,{select("more")},padding,bottom)
                            else HomeScreen(ConnectionStatus.Disconnected,server,{},openServers,
                                {select("more")},contentInsets=padding,bottomPadding=bottom)
                            "servers", "serverPicker" -> if(terminal && route=="servers")
                                TerminalToolsScreen(state,handler,dispatch,padding,bottom)
                            else ServersScreen(listOf(server),PremiumBrowserInput(),
                                PremiumBrowserState(rows=listOf(server)),false,{},{},server.guid,
                                emptySet(),{},{},{},{},padding,bottom,if(route=="serverPicker") back else null)
                            "more" -> MoreScreen(PremiumPreferences(),{},dispatch,openServers,padding,bottom)
                            "subscription" -> SubscriptionScreen(null,1,{},contentInsets=padding,bottomPadding=bottom)
                        }
                    }
                }
            }
        }
    }
    private fun tab(id:Int) = compose.onNode(hasText(label(id)) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and hasAnyAncestor(hasTestTag("floating_navigation")))
    private fun page(route:String) = compose.onNodeWithTag("page_$route").assertIsDisplayed()
    private fun back() = compose.runOnIdle {
        backDispatcher.onBackPressed()
    }

    @Test fun consoleIsSecondTabAndToolsAreAbsentFromHome() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Fixture() }
        page("home")
        compose.onNodeWithText(label(R.string.terminal_neofetch_title)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.terminal_logs_title)).assertDoesNotExist()
        compose.onAllNodesWithText(label(R.string.terminal_console_title)).assertCountEquals(1)
        tab(R.string.terminal_console_title).performClick();page("servers")
        tab(R.string.terminal_console_title).assertIsSelected()
        compose.onNodeWithTag("terminal_page_console").assertIsSelected()
        compose.onNodeWithText(label(R.string.terminal_command_hint)).performScrollTo().performTextInput("status")
        compose.onNodeWithText(label(R.string.terminal_command_hint)).performImeAction()
        compose.runOnIdle { assertEquals(listOf("status"),handler.state.value.history) }
        compose.onNodeWithTag("terminal_page_fetch").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Android QA",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("terminal_page_events").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(1,handler.state.value.events.size) }
        tab(R.string.premium_home).performClick();page("home")
        compose.runOnIdle { assertEquals("closed",handler.state.value.page) }
        tab(R.string.terminal_console_title).performClick()
        compose.onNodeWithTag("terminal_page_console").assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        page("servers");tab(R.string.terminal_console_title).assertIsSelected()
        compose.runOnIdle { assertEquals(listOf("status"),handler.state.value.history);assertEquals(0,toggles) }
    }

    @Test fun pickerReturnsToItsOriginAndCannotBeRestoredByConsoleOrHomeTab() {
        compose.setContent { Fixture() }
        repeat(2) {
            compose.onNodeWithTag("terminal_selected_server").performScrollTo().performClick()
            page("serverPicker")
            back();page("home")
            compose.onNodeWithTag("terminal_selected_server").performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.terminal_back)).performScrollTo().performClick()
            page("home")
            compose.onNodeWithTag("terminal_selected_server").performScrollTo().performClick()
            tab(R.string.terminal_console_title).performClick();page("servers")
            compose.onNodeWithTag("terminal_page_console").assertIsSelected()
            tab(R.string.premium_home).performClick();page("home")
        }
        tab(R.string.premium_more).performClick()
        compose.onNodeWithText(label(R.string.premium_protocol)).performScrollTo().performClick()
        page("serverPicker");back();page("more")
        tab(R.string.premium_home).performClick();page("home")
        compose.runOnIdle { assertEquals(0,toggles) }
    }

    @Test fun consoleInputAndActionRemainReachableOnSmallScreensWithLargeText() {
        val wide=mutableStateOf(false)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                if(wide.value) androidx.compose.ui.unit.DpSize(640.dp,360.dp)
                else androidx.compose.ui.unit.DpSize(320.dp,568.dp)
            ) then DeviceConfigurationOverride.FontScale(1.6f)) { Fixture() }
        }
        tab(R.string.terminal_console_title).performClick()
        val field=compose.onNodeWithText(label(R.string.terminal_command_hint))
        field.performScrollTo().performClick()
        compose.waitUntil(5000) {imeSettled}
        field.performTextInput("status")
        compose.onNodeWithText(label(R.string.terminal_run_command)).performScrollTo()
            .assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(listOf("status"),handler.state.value.history)}
        field.performScrollTo().performTextInput("help")
        field.performImeAction()
        compose.onNodeWithText(label(R.string.terminal_copy_diagnostics)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("floating_navigation").assertDoesNotExist()
        compose.runOnIdle {keyboard?.hide()}
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("floating_navigation").fetchSemanticsNodes().isNotEmpty()
        }
        // ForcedSize changes the Compose viewport, not the physical portrait IME.
        // Exercise the short wide viewport with an external keyboard instead.
        compose.runOnIdle {wide.value=true}
        field.performScrollTo().performTextInput("status")
        field.performKeyInput {pressKey(Key.Enter)}
        compose.runOnIdle {assertEquals(listOf("status","help","status"),handler.state.value.history)}
        compose.onNodeWithText(label(R.string.terminal_run_command)).performScrollTo().assertIsDisplayed()
        tab(R.string.premium_home).assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,toggles)}
    }

    @Test fun changingWorldKeepsSecondSlotAndOtherTabsAndSupportsKeyboardDpad() {
        compose.setContent { Fixture() }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        val console=tab(R.string.terminal_console_title)
        console.performSemanticsAction(SemanticsActions.RequestFocus) {it()}
        console.assertIsFocused().performKeyInput {pressKey(Key.Enter)}
        page("servers")
        val fetch=compose.onNodeWithTag("terminal_page_fetch").performScrollTo()
        fetch.performSemanticsAction(SemanticsActions.RequestFocus) {it()}
        fetch.assertIsFocused().performKeyInput {pressKey(Key.DirectionCenter)}
        fetch.assertIsSelected()
        for(id in listOf(ThemeId.Original,ThemeId.Sakura,ThemeId.Aurora,ThemeId.Cyberpunk)) {
            compose.runOnIdle {world.value=id}
            page("servers");tab(R.string.premium_servers).assertIsSelected()
            compose.onNodeWithText(label(R.string.premium_search)).assertExists()
            compose.onNodeWithTag("terminal_page_console").assertDoesNotExist()
            compose.runOnIdle {world.value=ThemeId.Terminal}
            tab(R.string.terminal_console_title).assertIsSelected()
            compose.onNodeWithTag("terminal_page_console").assertIsSelected()
        }
        tab(R.string.premium_subscription).performClick();page("subscription")
        tab(R.string.premium_more).performClick();page("more")
        compose.runOnIdle {sequence.longValue=1}
        page("subscription")
        tab(R.string.premium_home).performClick();page("home")
        compose.runOnIdle {assertEquals(0,toggles)}
        compose.onRoot().printToLog("TerminalNavigationSemantics")
    }
}
