package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerLabelTest {
    private fun row(id: String, name: String, ping: Long = 20) =
        ServerRowUiModel(id, ProfileItem(configType=EConfigType.VLESS, remarks=name), name, "", "", ping, "")

    @Test fun countryRowsUseTheSameNumberAndTransportFormat() {
        assertEquals(ServerLabel("🇷🇴",false,"gRPC"),serverLabel("🇷🇴 №3 gRPC"))
        assertEquals(ServerLabel("🇬🇧",false,"TCP"),serverLabel(" 🇬🇧  № 1 tcp "))
        assertEquals(ServerLabel("🇳🇱",false,"XHTTP"),serverLabel("🇳🇱 # 7 - xhttp"))
        assertEquals(ServerLabel("🇩🇪",false,""),serverLabel("🇩🇪 №8"))
        assertEquals(ServerLabel("🇫🇷",false,"Paris"),serverLabel("🇫🇷 Paris"))
    }

    @Test fun automaticSelectionHasNoRawEmojiOrSubscriptionCountdownInItsLabel() {
        for (name in listOf("🔃 Автовыбор","🔝 АВТОВЫБОР ⏳Осталось 26694d 0h 30m",
            "Авто-выбор","Auto select","AutoSelection")) {
            assertEquals(ServerLabel(null,true,""),serverLabel(name),name)
        }
        assertFalse(serverLabel("Autobahn Frankfurt").automatic)
        assertFalse(serverLabel("МойАвтовыборСервер").automatic)
    }

    @Test fun emptyMalformedAndUnknownNamesHaveSafeFallbacksWithoutInventingLocations() {
        assertEquals(ServerLabel(null,false,""),serverLabel(""))
        assertEquals(ServerLabel(null,false,""),serverLabel("  \n "))
        assertEquals(ServerLabel("🇷🇴",false,""),serverLabel("🇷🇴"))
        assertEquals(ServerLabel("🇯🇵",false,"Tokyo"),serverLabel("🇯🇵 Tokyo"))
        assertEquals(ServerLabel(null,false,"Private gateway"),serverLabel("Private   gateway"))
        assertNull(serverLabel("🇷").countryFlag)
    }

    @Test fun presentationDoesNotRenameTheProfileOrChangeItsIdentity() {
        val name="🇷🇴 №3 gRPC"
        val original=row("romania-guid",name)
        assertEquals("romania-guid",original.guid)
        assertEquals(name,original.profile.remarks)
        assertEquals(name,original.remarks)
        val replacement=original.copy(remarks="🔃 Автовыбор")
        assertTrue(replacement.label.automatic)
        assertFalse(original.label.automatic)
        assertEquals("romania-guid",replacement.guid)
    }

    @Test fun searchFindsRomaniaAndNormalizedLabelsAndKeepsIdBasedFilters() {
        val rows=listOf(row("ro","🇷🇴 №3 gRPC"),row("uk","🇬🇧 №1 TCP"),
            row("auto","🔃 Автовыбор"),row("slow","🇷🇴 №4 XHTTP",300))
        val countries=mapOf("🇷🇴" to "Румыния","🇬🇧" to "Великобритания")
        fun search(query:String,filter:Int=0)=filterPremiumServers(rows,
            PremiumBrowserInput(query,filter),setOf("ro"),countries).map {it.guid}
        assertEquals(listOf("ro","slow"),search("румы"))
        assertEquals(listOf("ro"),search("gRPC"))
        assertEquals(listOf("uk"),search("🇬🇧"))
        assertEquals(listOf("auto"),search("автовыбор"))
        assertEquals(listOf("ro"),search("Румыния",1))
        assertEquals(listOf("ro"),search("Румыния",2))
        assertEquals(rows.map {it.guid},search(""))
        assertTrue(search("missing").isEmpty())
        assertTrue(filterPremiumServers(emptyList(),PremiumBrowserInput(),emptySet(),countries).isEmpty())
    }
}
