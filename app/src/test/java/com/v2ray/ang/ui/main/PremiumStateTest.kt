package com.v2ray.ang.ui.main
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class PremiumStateTest {
    @Test fun plansHaveNoInventedDiscounts() {
        assertEquals(150,planPrice(1));assertEquals(450,planPrice(3));assertEquals(900,planPrice(6));assertEquals(1800,planPrice(12))
        assertThrows(IllegalStateException::class.java){planPrice(0)}
        assertThrows(IllegalStateException::class.java){planPrice(2)}
    }
    @Test fun launchConnectionNeedsOptInAndAReadyAccount() {
        val optedIn=PremiumPreferences(connectOnLaunch=true)
        val ready=MainUiState(selectedGuid="server-guid")
        assertTrue(canConnectOnLaunch(optedIn,ready))
        assertFalse(canConnectOnLaunch(PremiumPreferences(),ready))
        assertFalse(canConnectOnLaunch(optedIn,MainUiState()))
        assertFalse(canConnectOnLaunch(optedIn,ready.copy(selectedGuid=" ")))
        assertFalse(canConnectOnLaunch(optedIn,ready.copy(isRunning=true)))
        assertFalse(canConnectOnLaunch(optedIn,ready.copy(isConnecting=true)))
        assertFalse(canConnectOnLaunch(optedIn,ready.copy(showTnglLoginDialog=true)))
    }
    @Test fun serverFiltersUseIdsAndExcludeUnknownOrFailedPings() {
        fun row(id:String, title:String, ping:Long)=ServerRowUiModel(
            id,com.v2ray.ang.dto.entities.ProfileItem(configType=com.v2ray.ang.enums.EConfigType.VLESS),
            title,"","",ping,"")
        val rows=listOf(row("uk","🇬🇧 TCP",21),row("de","🇩🇪 Test",0),row("failed","Fail",-1),row("slow","Slow",200))
        val countries=mapOf("🇬🇧" to "Великобритания","🇩🇪" to "Германия")
        assertEquals(rows,filterPremiumServers(rows,PremiumBrowserInput(),emptySet(),countries))
        assertEquals(listOf("uk"),filterPremiumServers(rows,PremiumBrowserInput(query="британ"),emptySet(),countries).map{it.guid})
        assertEquals(listOf("de"),filterPremiumServers(rows,PremiumBrowserInput(filter=1),setOf("de"),countries).map{it.guid})
        assertEquals(listOf("uk"),filterPremiumServers(rows,PremiumBrowserInput(filter=2),emptySet(),countries).map{it.guid})
        assertTrue(filterPremiumServers(rows,PremiumBrowserInput(query="absent"),emptySet(),countries).isEmpty())
        assertTrue(filterPremiumServers(emptyList(),PremiumBrowserInput(),emptySet(),countries).isEmpty())
        assertTrue(filterPremiumServers(rows,PremiumBrowserInput(filter=1),emptySet(),countries).isEmpty())
    }
    @Test fun accountLoginBelongsToAuthenticatedTangledLinkAccount() {
        val account=com.v2ray.ang.dto.GroupMapItem(com.v2ray.ang.AppConfig.TNGL_SUB_ID," svk ")
        val unrelated=com.v2ray.ang.dto.GroupMapItem("other-subscription","Another subscription")
        val state=MainUiState(groups=listOf(unrelated,account),selectedGroupId=unrelated.id)
        assertNull(MainUiState().accountLogin)
        assertEquals("svk",state.accountLogin)
        assertNull(state.copy(showTnglLoginDialog=true).accountLogin)
        assertNull(state.copy(groups=listOf(unrelated)).accountLogin)
        assertNull(state.copy(groups=listOf(account.copy(remarks="  "))).accountLogin)
        assertEquals("other-user",state.copy(groups=listOf(account.copy(remarks="other-user"))).accountLogin)
    }
    @Test fun initialPreferencesHaveNoFakeFavorites() {
        val initial=PremiumPreferences()
        assertTrue(initial.favorites.isEmpty());assertEquals(1,initial.months)
    }
}
