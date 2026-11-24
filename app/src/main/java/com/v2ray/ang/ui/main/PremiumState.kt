package com.v2ray.ang.ui.main

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

internal enum class ConnectionStatus { Disconnected, Connecting, Connected }
internal data class PremiumPreferences(
    val favorites: Set<String> = emptySet(),
    val months: Int = 1,
    val connectOnLaunch: Boolean = false,
)
internal data class PremiumBrowserInput(val query: String = "", val filter: Int = 0)
internal data class PremiumBrowserState(
    val input: PremiumBrowserInput = PremiumBrowserInput(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

internal fun filterPremiumServers(
    rows: List<ServerRowUiModel>,
    input: PremiumBrowserInput,
    favorites: Set<String>,
    countryNames: Map<String, String>,
    locale: java.util.Locale = java.util.Locale.getDefault(),
): List<ServerRowUiModel> = rows.filter { row ->
    val flag = row.label.countryFlag
    val country = (countryNames[flag] ?: countryNameFromFlag(flag, locale)).orEmpty()
    (row.remarks.contains(input.query, true) || row.label.detail.contains(input.query, true) ||
        country.contains(input.query, true)) &&
        (input.filter != 1 || row.guid in favorites) &&
        (input.filter != 2 || row.testDelayMillis in 1..149)
}

internal fun planPrice(months: Int): Int = when (months) { 1, 3, 6, 12 -> months * 150; else -> error("Unsupported plan") }
internal fun canConnectOnLaunch(preferences: PremiumPreferences, state: MainUiState): Boolean =
    preferences.connectOnLaunch && !state.isRunning && !state.isConnecting &&
        !state.showTnglLoginDialog && !state.selectedGuid.isNullOrBlank()

private val Context.premiumStore by preferencesDataStore("premium_ui")
internal class PremiumStore(context: Context) {
    private val store = context.applicationContext.premiumStore
    private val favorites = stringSetPreferencesKey("favorites")
    private val months = intPreferencesKey("months")
    private val connectOnLaunch = booleanPreferencesKey("connect_on_launch")
    val state = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { PremiumPreferences(it[favorites] ?: emptySet(), (it[months] ?: 1).takeIf { m -> m in listOf(1,3,6,12) } ?: 1, it[connectOnLaunch] ?: false) }
    suspend fun toggle(guid: String) = store.edit { p ->
        val old = p[favorites] ?: emptySet()
        p[favorites] = if (guid in old) old - guid else old + guid
    }
    suspend fun select(month: Int) { planPrice(month); store.edit { it[months] = month } }
    suspend fun setConnectOnLaunch(enabled: Boolean) { store.edit { it[connectOnLaunch] = enabled } }
}
