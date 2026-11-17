package com.v2ray.ang.ui.main

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.navigation.compose.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalThemeConfig
import com.v2ray.ang.ui.compose.serviceStatusLabel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun PremiumShell(vm: MainViewModel, action: (MainAction)->Unit, navigate: (MainDestination)->Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val connectionTarget by vm.connectionGate.targetState.collectAsStateWithLifecycle()
    val terminalState by vm.terminal.state.collectAsStateWithLifecycle()
    val prefs by vm.premiumState.collectAsStateWithLifecycle()
    val browser by vm.premiumBrowserState.collectAsStateWithLifecycle()
    val browserInput by vm.premiumBrowserInput.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    val billing by vm.billingState.collectAsStateWithLifecycle()
    val group by remember(state.selectedGroupId) { vm.serverGroupState(state.selectedGroupId) }.collectAsStateWithLifecycle()
    val selected = group.rows.firstOrNull { it.guid == state.selectedGuid }
    PremiumTheme {
        if (billing.visible) BillingDialog(billing, action)
        val terminalMode = LocalThemeConfig.current.id == com.v2ray.ang.ui.compose.ThemeId.Terminal
        PremiumTabNavigation(billing.returnSequence,
            onTerminalPageChanged={action(MainAction.OpenTerminal(it))}) { route, selectTab, goBack, padding, bottomClearance ->
            val openServers = { selectTab(if (terminalMode) "serverPicker" else "servers") }
            when (route) {
                "home" -> if(LocalThemeConfig.current.id == com.v2ray.ang.ui.compose.ThemeId.Terminal)
                    com.v2ray.ang.ui.terminal.TerminalHome(state, selected, action, openServers, {selectTab("more")},
                        padding,bottomClearance)
                else HomeScreen(
                    state.connectionPresentation(),
                    selected, {action(MainAction.ToggleService)}, {selectTab("servers")}, {selectTab("more")},
                    accountLogin=state.accountLogin, traffic=state.traffic, contentInsets=padding, bottomPadding=bottomClearance,
                    connectionEnabled=state.connectionActionAvailable(connectionTarget!=null))
                "servers", "serverPicker" -> if (terminalMode && route == "servers")
                    com.v2ray.ang.ui.terminal.TerminalToolsScreen(terminalState, vm.terminal, action, padding, bottomClearance)
                else ServersScreen(group.rows, browserInput, browser, loading,
                    {action(MainAction.SearchPremiumServers(it))}, {action(MainAction.FilterPremiumServers(it))},
                    state.selectedGuid, prefs.favorites, {action(MainAction.SelectServer(it))},
                    {action(MainAction.ToggleFavorite(it))}, {action(MainAction.TestAllServers)},
                    {action(MainAction.UpdateSubscriptions)}, contentInsets=padding, bottomPadding=bottomClearance,
                    back=if(route=="serverPicker") goBack else null)
                "subscription" -> SubscriptionScreen(
                    state.groups.firstOrNull { it.id==state.selectedGroupId }?.usageInfo,
                    prefs.months, {action(MainAction.SelectPremiumPlan(it))},
                    onRenew={action(MainAction.BeginBilling(prefs.months))}, contentInsets=padding, bottomPadding=bottomClearance)
                "more" -> MoreScreen(prefs, navigate, action, openServers,
                    contentInsets=padding, bottomPadding=bottomClearance, account={selectTab("account")})
                "account" -> AccountScreen(state.accountLogin,
                    state.groups.firstOrNull {it.id==com.v2ray.ang.AppConfig.TNGL_SUB_ID}?.usageInfo,
                    action, {selectTab("more")}, padding, bottomClearance)
            }
        }
    }
}

/** Shared by the bottom bar, screen shortcuts and billing returns. */
@Composable
internal fun PremiumTabNavigation(
    billingReturnSequence: Long = 0,
    onTerminalPageChanged: (String) -> Unit = {},
    content: @Composable (String, (String) -> Unit, () -> Unit, PaddingValues, Dp) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    // Keep the second slot's route stable when changing worlds or restoring navigation.
    // Terminal renders its console in this slot; the server picker remains a child screen.
    val tabs = remember { listOf("home", "servers", "subscription", "more") }
    val terminalMode = LocalThemeConfig.current.id == com.v2ray.ang.ui.compose.ThemeId.Terminal
    val selectTab: (String) -> Unit = remember(nav) {
        { target ->
            if (nav.currentDestination?.route == "account" && target in tabs) {
                nav.popBackStack("more", inclusive=false)
            }
            if (nav.currentDestination?.route == "serverPicker" && target in tabs) {
                nav.popBackStack()
            }
            if (target == "serverPicker") { nav.navigate("serverPicker") { launchSingleTop=true } }
            if (target == "account" && nav.currentDestination?.route == "more") {
                nav.navigate("account") {launchSingleTop=true}
            } else if (target in tabs && nav.currentDestination?.route != target) {
                nav.navigate(target) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    // A saved stack can be associated with its root. Restoring
                    // "home" would then reopen the tab that was just popped.
                    restoreState = target != "home"
                }
            }
        }
    }
    // Recreating the activity must not replay an already handled payment return
    // after the user has deliberately selected another tab.
    var returnHandled by rememberSaveable(billingReturnSequence) { mutableStateOf(false) }
    LaunchedEffect(billingReturnSequence) {
        if (billingReturnSequence > 0 && !returnHandled) {
            selectTab("subscription")
            returnHandled = true
        }
    }
    LaunchedEffect(terminalMode) {
        if (!terminalMode && nav.currentDestination?.route == "serverPicker") selectTab("servers")
    }
    val terminalPageChanged by rememberUpdatedState(onTerminalPageChanged)
    LaunchedEffect(route, terminalMode) {
        terminalPageChanged(if (terminalMode && route=="servers") "console" else "closed")
    }
    val goBack: () -> Unit = { nav.popBackStack() }
    androidx.activity.compose.BackHandler(enabled=route=="account" || route=="serverPicker") {
        if (route=="serverPicker") goBack() else selectTab("more")
    }
    FloatingNavigationLayout(if(route=="account") "more" else if(route=="serverPicker") "servers" else route,
        selectTab, terminalConsole=terminalMode && route=="servers") { padding, bottomClearance ->
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination="home",
                modifier=Modifier.align(Alignment.TopCenter).widthIn(max=840.dp).fillMaxSize()) {
                (tabs + listOf("account","serverPicker")).forEach { target ->
                    composable(target) {
                        CompositionLocalProvider(com.v2ray.ang.ui.compose.LocalMotionVisible provides (target==route)) {
                            content(target, selectTab, goBack, padding, bottomClearance)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeScreen(
    status: ConnectionStatus,
    server: ServerRowUiModel?,
    connect: () -> Unit,
    servers: () -> Unit,
    settings: () -> Unit,
    accountLogin: String? = null,
    traffic: TrafficUiState = TrafficUiState(),
    contentInsets: PaddingValues = PaddingValues(0.dp),
    bottomPadding: Dp = 0.dp,
    connectionEnabled: Boolean = true,
) {
    val listState = rememberLazyListState()
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = (maxHeight - contentInsets.calculateTopPadding() - bottomPadding).coerceAtLeast(0.dp)
        val wide = maxWidth >= 600.dp
        val compact = availableHeight < 800.dp || fontScale > 1.15f
        val tight = availableHeight < 680.dp || fontScale > 1.3f
        val gap = if (tight) 8.dp else if (compact) 12.dp else 20.dp
        val horizontal = if (maxWidth < 360.dp || wide) 12.dp else 20.dp
        val vertical = if (tight) 8.dp else if (compact) 12.dp else 24.dp
        val diameter = if (wide) (availableHeight - 140.dp).coerceIn(128.dp, 200.dp)
            else (availableHeight - 460.dp).coerceIn(128.dp, 216.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_scroll"),
            state = listState,
            contentPadding = floatingContentPadding(contentInsets, bottomPadding, horizontal, vertical),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!wide) item(key = "header") { HomeHeader(compact, settings) }
            item(key = "dashboard") {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(gap)) {
                            HomeHeader(compact, settings)
                            HomeControls(status, connectionEnabled && (server != null || status==ConnectionStatus.Connected), accountLogin, diameter, compact, connect)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                            HomeServer(server, servers, compact)
                            HomeTraffic(traffic, compact)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(gap)) {
                        HomeControls(status, connectionEnabled && (server != null || status==ConnectionStatus.Connected), accountLogin, diameter, compact, connect)
                        HomeServer(server, servers, compact)
                        HomeTraffic(traffic, compact)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(compact: Boolean, settings: () -> Unit) {
    val scene = LocalThemeConfig.current.scene
    Surface(color = scene.background.copy(alpha = .78f), contentColor = scene.onScene,
        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        coil3.compose.AsyncImage(R.drawable.tngl_brand_mark, null,
            Modifier.width(if (compact) 32.dp else 40.dp).aspectRatio(256f / 240f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = settings) {
            Icon(painterResource(R.drawable.ic_settings_24dp), stringResource(R.string.premium_settings))
        }
    }
    }
}

@Composable
private fun HomeControls(
    status: ConnectionStatus,
    enabled: Boolean,
    login: String?,
    diameter: Dp,
    compact: Boolean,
    connect: () -> Unit,
) {
    val textStyle = if (compact) MaterialTheme.typography.titleMedium.copy(
        fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
    ) else MaterialTheme.typography.titleLarge
    val scene = LocalThemeConfig.current.scene
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!login.isNullOrBlank()) {
            Text(login, modifier = Modifier.background(scene.background.copy(alpha = .85f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
                style = textStyle, color = scene.onScene, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        ConnectionButton(status, enabled = enabled, diameter = diameter, onClick = connect)
        Text(
            stringResource(status.serviceStatusLabel()),
            modifier = Modifier.clearAndSetSemantics {}.background(scene.background.copy(alpha = .85f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = textStyle,
            color = scene.onScene,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun HomeServer(server: ServerRowUiModel?, servers: () -> Unit, compact: Boolean) {
    if (server == null) SettingsItem(stringResource(R.string.premium_no_server), stringResource(R.string.premium_choose), servers)
    else ServerCard(server, true, false, servers, null, compact = compact)
}

@Composable
private fun HomeTraffic(traffic: TrafficUiState, compact: Boolean) {
    val snapshot = traffic.latest
    val duration = snapshot?.connectedMillis?.div(1_000L)
    TrafficChart(
        traffic.samples,
        snapshot?.let { formatTrafficSpeed(it.download) },
        snapshot?.let { formatTrafficSpeed(it.upload) },
        duration?.let { stringResource(R.string.premium_elapsed, it / 3600, (it / 60) % 60, it % 60) },
        compact = compact,
    )
}

@Composable
internal fun ServersScreen(rows: List<ServerRowUiModel>, input: PremiumBrowserInput, browser: PremiumBrowserState, loading: Boolean, search:(String)->Unit, setFilter:(Int)->Unit, selected: String?, favorites: Set<String>,
                          select:(String)->Unit, favorite:(String)->Unit, measure:()->Unit, refresh:()->Unit, contentInsets:PaddingValues=PaddingValues(0.dp), bottomPadding:Dp=0.dp, back:(()->Unit)?=null) {
    val query=input.query
    val filter=input.filter
    val filtered=browser.rows
    LazyColumn(Modifier.fillMaxSize(), contentPadding=floatingContentPadding(contentInsets,bottomPadding,20.dp,20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if (back != null) item(key="back") {
            TextButton(onClick=back) { Text(stringResource(R.string.terminal_back)) }
        }
        item { Text(stringResource(R.string.premium_servers),style=MaterialTheme.typography.headlineMedium) }
        item { OutlinedTextField(query,search,Modifier.fillMaxWidth(),singleLine=true,
            label={Text(stringResource(R.string.premium_search))},shape=RoundedCornerShape(18.dp)) }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(R.string.premium_all,R.string.premium_favorites,R.string.premium_fast).forEachIndexed { i,label ->
                    FilterChip(filter==i,{setFilter(i)},label={Text(stringResource(label))})
                }
            }
        }
        item {
            val best=rows.filter { it.testDelayMillis>0 }.minByOrNull { it.testDelayMillis }
            SettingsItem(stringResource(R.string.premium_auto),stringResource(R.string.premium_auto_note),
                {best?.let {select(it.guid)}},enabled=best!=null)
        }
        item { Row { TextButton(onClick=measure){Text(stringResource(R.string.premium_measure))}; TextButton(onClick=refresh){Text(stringResource(R.string.premium_refresh))} } }
        if(loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(filtered.isEmpty() && !loading) item { Text(stringResource(if(rows.isEmpty())R.string.premium_empty else R.string.premium_no_results),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(filtered,key={it.guid}) { row ->
            ServerCard(row,row.guid==selected,row.guid in favorites,{select(row.guid)},{favorite(row.guid)})
        }
    }
}

@Composable
internal fun SubscriptionScreen(info: com.v2ray.ang.dto.entities.SubscriptionUsageInfo?, months: Int, select:(Int)->Unit, onRenew:()->Unit = {}, contentInsets:PaddingValues=PaddingValues(0.dp), bottomPadding:Dp=0.dp) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.ROOT
    LazyColumn(Modifier.fillMaxSize(),contentPadding=floatingContentPadding(contentInsets,bottomPadding,24.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.premium_subscription),style=MaterialTheme.typography.headlineMedium) }
        item { SubscriptionCard() }
        item {
            if(info==null) Text(stringResource(R.string.premium_unknown_subscription),color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val expiry=info.expire*1000L
                Text(stringResource(when {
                    expiry>0 && expiry<=System.currentTimeMillis() -> R.string.premium_expired
                    info.total>0 && info.used>=info.total -> R.string.premium_depleted
                    else -> R.string.premium_active
                }))
                if(expiry>0) Text(stringResource(R.string.premium_expiry,SimpleDateFormat("dd.MM.yyyy",locale).format(java.util.Date(expiry)),
                    ((expiry-System.currentTimeMillis()).coerceAtLeast(0)/86400000).toInt()),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        listOf(1,3,6,12).forEach { month -> item(key=month) {
            Surface(onClick={select(month)},shape=RoundedCornerShape(20.dp),
                modifier=Modifier.semantics { role=Role.RadioButton; selected=months==month },
                border=BorderStroke(1.dp,if(months==month)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                color=if(months==month)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(months==month,null,modifier=Modifier.clearAndSetSemantics {})
                    Text(stringResource(R.string.premium_plan,month),Modifier.weight(1f).padding(start=12.dp))
                    Text(stringResource(R.string.premium_price,planPrice(month)),style=MaterialTheme.typography.titleLarge)
                }
            }
        }}
        item { Button(onClick=onRenew,Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) {Text(stringResource(R.string.premium_renew))} }

    }
}

@Composable
internal fun MoreScreen(prefs: PremiumPreferences, navigate:(MainDestination)->Unit, action:(MainAction)->Unit, servers:()->Unit, contentInsets:PaddingValues=PaddingValues(0.dp), bottomPadding:Dp=0.dp, account:()->Unit = {}) {
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    if (languageDialog) AlertDialog(
        onDismissRequest={languageDialog=false},
        title={Text(stringResource(R.string.premium_language))},
        text={
            Column {
                listOf("ru" to R.string.premium_russian, "en" to R.string.premium_english).forEach { (code, label) ->
                    TextButton(onClick={languageDialog=false; action(MainAction.SetLanguage(code))},modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={languageDialog=false}){Text(stringResource(android.R.string.cancel))}}
    )
    LazyColumn(Modifier.fillMaxSize(),contentPadding=floatingContentPadding(contentInsets,bottomPadding,24.dp,24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.premium_settings),style=MaterialTheme.typography.headlineMedium) }
        item { SettingsItem(stringResource(R.string.premium_manage_account),
            stringResource(R.string.premium_manage_account_note),account,icon=R.drawable.ic_lock_24dp) }
        item { SettingsItem(stringResource(R.string.themes_title), stringResource(R.string.themes_note),
            {navigate(MainDestination.Themes)}, icon=R.drawable.ic_premium_home) }
        item { SettingsItem(stringResource(R.string.premium_auto_connect),stringResource(R.string.premium_autoconnect_note),{action(MainAction.OpenSystemVpnSettings)},icon=R.drawable.ic_lock_24dp) }
        item { SettingsToggle(stringResource(R.string.premium_start_connect),stringResource(R.string.premium_launch_note),prefs.connectOnLaunch,{action(MainAction.SetConnectOnLaunch(it))}) }
        item { SettingsItem(stringResource(R.string.premium_notifications),stringResource(R.string.premium_notification_note),{action(MainAction.OpenNotificationSettings)},icon=R.drawable.ic_description_24dp) }
        item { SettingsItem(stringResource(R.string.premium_language),stringResource(R.string.premium_language_note),{languageDialog=true},icon=R.drawable.ic_translate_24dp) }
        item { SettingsItem(stringResource(R.string.premium_protocol),stringResource(R.string.premium_protocol_note),servers,icon=R.drawable.ic_routing_24dp) }
        item { SettingsItem(stringResource(R.string.premium_split),stringResource(R.string.premium_apps),{navigate(MainDestination.PerAppProxy)}) }
        item { SettingsItem(stringResource(R.string.premium_about),"TangledLink",{navigate(MainDestination.About)},icon=R.drawable.ic_about_24dp) }
        item { SettingsItem(stringResource(R.string.premium_advanced),stringResource(R.string.premium_advanced_note),{advanced=!advanced}) }
        if (advanced) {
            MainDestination.entries.filter { it !in setOf(MainDestination.About, MainDestination.PerAppProxy, MainDestination.Themes) }.forEach { destination ->
                item(key=destination.name) { SettingsItem(stringResource(destination.labelRes),stringResource(R.string.premium_settings_note),{navigate(destination)},icon=destination.iconRes) }
            }
            item { SettingsItem(stringResource(R.string.premium_restart),stringResource(R.string.premium_restart_note),{action(MainAction.RestartService)},icon=R.drawable.ic_refresh_24dp) }
            item { SettingsItem(stringResource(R.string.premium_sort),stringResource(R.string.premium_auto_note),{action(MainAction.SortByTestResults)}) }
        }
    }
}
