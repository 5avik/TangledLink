package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ServerCardLabelsTest {
    @get:Rule val compose=createComposeRule()
    private data class Appearance(val dark:Boolean=false,val dynamic:Boolean=false,
        val wide:Boolean=false,val compact:Boolean=false)
    private val appearance=mutableStateOf(Appearance())
    private val selected=mutableStateOf("ro")
    private val selections=mutableListOf<String>()
    private val favorites=mutableListOf<String>()
    private lateinit var inputMode:InputModeManager
    private fun row(id:String,name:String)=ServerRowUiModel(id,
        ProfileItem(configType=EConfigType.VLESS),name,"","",20,"")
    private val rows=listOf(row("auto","🔃 Автовыбор ⏳Осталось 26694d"),
        row("uk","🇬🇧 №1 TCP"),row("pl","🇵🇱 XHTTP"),row("ro","🇷🇴 №3 gRPC"))
    private fun label(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun title(row:ServerRowUiModel): String {
        if (row.guid == "pl") {
            val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
            val locale = androidx.core.os.ConfigurationCompat.getLocales(config)[0]
            return if (locale?.language == "ru") "Польша" else
                java.util.Locale.Builder().setRegion("PL").build()
                    .getDisplayCountry(locale ?: java.util.Locale.ROOT)
        }
        return label(when(row.guid){
        "auto"->R.string.premium_auto_select;"uk"->R.string.premium_uk;else->R.string.premium_ro
        })
    }
    private fun card(row:ServerRowUiModel)=compose.onNode(hasText(title(row)) and hasClickAction())

    @Composable private fun Fixture() {
        val config=appearance.value
        val context=LocalContext.current
        inputMode=LocalInputModeManager.current
        val colors=when {
            config.dynamic && android.os.Build.VERSION.SDK_INT>=31 ->
                if(config.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            config.dark -> darkColorScheme()
            else -> lightColorScheme()
        }
        DeviceConfigurationOverride(
            DeviceConfigurationOverride.ForcedSize(DpSize(if(config.wide)840.dp else 320.dp,900.dp)) then
                DeviceConfigurationOverride.FontScale(1.3f)
        ) {
            MaterialTheme(colorScheme=colors) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        rows.chunked(if(config.wide)2 else 1).forEach { chunk ->
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                chunk.forEach { row ->
                                    Box(Modifier.weight(1f).testTag("card_${row.guid}")) {
                                        ServerCard(row,row.guid==selected.value,false,{
                                            selected.value=row.guid;selections.add(row.guid)
                                        },{favorites.add(row.guid)},compact=config.compact)
                                    }
                                }
                                if(config.wide && chunk.size==1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun assertLabels() {
        compose.onNodeWithText("TCP").assertExists()
        compose.onNodeWithText("gRPC").assertExists()
        val poland = rows.first { it.guid == "pl" }
        compose.onNode(hasText(title(poland)) and hasText("XHTTP") and hasClickAction()).assertExists()
        compose.onNodeWithText("№",substring=true).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.premium_auto_select_note)).assertExists()
        compose.onNodeWithText("🇬🇧 №1 TCP").assertDoesNotExist()
        compose.onNodeWithText("🇷🇴 №3 gRPC").assertDoesNotExist()
        compose.onNodeWithText("Осталось",substring=true).assertDoesNotExist()
        var left:Float?=null
        rows.forEach { row ->
            val text=compose.onNodeWithText(title(row),useUnmergedTree=true).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val container=compose.onNodeWithTag("card_${row.guid}").fetchSemanticsNode().boundsInRoot
            val inset=text.left-container.left
            left?.let { assertEquals(it,inset,1f) }
            left=inset
        }
    }

    @Test fun allCardsHaveAlignedLocalizedLabelsAndOneSeparateFavoriteAction() {
        compose.setContent {Fixture()}
        assertLabels()
        rows.forEach { row -> card(row).assertExists() }
        card(rows.last()).assertIsSelected()
        compose.onAllNodesWithContentDescription(label(R.string.premium_favorite)).assertCountEquals(rows.size)
        compose.onRoot().printToLog("ServerCardLabels")
    }

    @Test fun lightDarkDynamicAndTwoColumnLayoutsKeepLabelsAndSelectionUsable() {
        compose.setContent {Fixture()}
        var clicks=0
        for(dark in listOf(false,true)) for(dynamic in listOf(false,true))
            for(wide in listOf(false,true)) for(compact in listOf(false,true)) {
                compose.runOnIdle {appearance.value=Appearance(dark,dynamic,wide,compact)}
                assertLabels()
                card(rows.first()).performClick().assertIsSelected()
                compose.runOnIdle {assertEquals(++clicks,selections.size)}
            }
    }

    @Test fun touchKeyboardDpadAndFavoritePreserveDomainIdentity() {
        compose.setContent {Fixture()}
        card(rows.first()).performClick().assertIsSelected()
        compose.runOnIdle {inputMode.requestInputMode(InputMode.Keyboard)}
        val uk=card(rows[1])
        uk.performSemanticsAction(SemanticsActions.RequestFocus){it()}
        uk.assertIsFocused().performKeyInput {pressKey(Key.Enter)}
        uk.assertIsSelected()
        val ro=card(rows.last())
        ro.performSemanticsAction(SemanticsActions.RequestFocus){it()}
        ro.assertIsFocused().performKeyInput {pressKey(Key.DirectionCenter)}
        ro.assertIsSelected()
        // The star is a separate action and must not select a different server.
        compose.onAllNodesWithContentDescription(label(R.string.premium_favorite))[0].performClick()
        ro.assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf("auto","uk","ro"),selections)
            assertEquals(listOf("auto"),favorites)
        }
    }
}
