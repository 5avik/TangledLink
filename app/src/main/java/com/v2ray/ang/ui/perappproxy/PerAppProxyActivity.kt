package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.v2ray.ang.util.AppIconFetcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils

private enum class PerAppMenuAction(@StringRes val labelRes: Int) {
    SelectAll(R.string.menu_item_select_all),
    InvertSelection(R.string.menu_item_invert_selection),
    SelectProxyApps(R.string.menu_item_select_proxy_app),
    ImportSelection(R.string.menu_item_import_proxy_app),
    ExportSelection(R.string.menu_item_export_proxy_app)
}

class PerAppProxyActivity : BaseComponentActivity() {

    private val viewModel: PerAppProxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val blacklist by viewModel.blacklist.collectAsStateWithLifecycle()
        val perAppProxyEnabled by viewModel.perAppProxyEnabled.collectAsStateWithLifecycle()
        val bypassApps by viewModel.bypassApps.collectAsStateWithLifecycle()

        com.v2ray.ang.ui.main.PremiumTheme {
        PerAppProxyScreen(
            apps = apps,
            isLoading = isLoading,
            blacklist = blacklist,
            perAppProxyEnabled = perAppProxyEnabled,
            bypassApps = bypassApps,
            onBackClick = { finish() },
            onPerAppProxyChanged = { viewModel.setPerAppProxyEnabled(it) },
            onBypassAppsChanged = { viewModel.setBypassAppsEnabled(it) },
            onToggleApp = { viewModel.toggle(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() },
            onSelectProxyAuto = { viewModel.selectProxyAppAuto(this) },
            onImportProxyApp = {
                val content = Utils.getClipboard(applicationContext)
                viewModel.importProxyApp(content, this)
            },
            onExportProxyApp = {
                val export = viewModel.exportProxyApp()
                Utils.setClipboard(applicationContext, export)
                toastSuccess(R.string.toast_success)
            }
        )
        }
    }
}

@Composable
private fun PremiumAppItem(app: AppInfo, throughVpn: Boolean, enabled: Boolean, toggle: ()->Unit) {
    val context=LocalContext.current
    val icon=remember(app.packageName) {
        ImageRequest.Builder(context).data("appicon:${app.packageName}")
            .fetcherFactory(AppIconFetcher.Factory(context)).build()
    }
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().toggleable(throughVpn,enabled=enabled,role=Role.Switch,onValueChange={toggle()}).padding(16.dp),
            verticalAlignment=Alignment.CenterVertically) {
            AsyncImage(icon,null,Modifier.size(40.dp),contentScale=ContentScale.Fit,
                error=painterResource(R.drawable.ic_image_24dp))
            Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                Text(app.appName,style=MaterialTheme.typography.titleMedium)
                Text(stringResource(if(!enabled)R.string.premium_rules_disabled else if(throughVpn)R.string.premium_via_vpn else R.string.premium_without_vpn),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(throughVpn,onCheckedChange=null,enabled=enabled)
        }
    }
}

@Composable
fun PerAppProxyScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    blacklist: Set<String>,
    perAppProxyEnabled: Boolean,
    bypassApps: Boolean,
    onBackClick: () -> Unit,
    onPerAppProxyChanged: (Boolean) -> Unit,
    onBypassAppsChanged: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectProxyAuto: () -> Unit,
    onImportProxyApp: () -> Unit,
    onExportProxyApp: () -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    val onInfoClick = { showInfoDialog = true }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onSearch(searchQuery)
    }

    Scaffold(

        topBar = {
            AppTopBar(
                title = stringResource(R.string.per_app_proxy_settings),
                onBackClick = onBackClick,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    onSearch(query)
                },
                onSearchClose = {
                    searchQuery = ""
                    onSearch("")
                    showSearch = false
                },
                searchPlaceholder = stringResource(R.string.menu_item_search),
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                painterResource(R.drawable.ic_search_24dp),
                                contentDescription = stringResource(R.string.acc_search)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                contentDescription = stringResource(R.string.acc_more)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppDropdownMenuItems(PerAppMenuAction.entries, { it.labelRes }) { action ->
                                showMenu = false
                                when (action) {
                                    PerAppMenuAction.SelectAll -> onSelectAll()
                                    PerAppMenuAction.InvertSelection -> onInvertSelection()
                                    PerAppMenuAction.SelectProxyApps -> onSelectProxyAuto()
                                    PerAppMenuAction.ImportSelection -> onImportProxyApp()
                                    PerAppMenuAction.ExportSelection -> onExportProxyApp()
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                com.v2ray.ang.ui.main.SettingsToggle(
                    stringResource(R.string.per_app_proxy_settings_enable),
                    stringResource(R.string.premium_routing_saved),
                    perAppProxyEnabled,onPerAppProxyChanged)
                com.v2ray.ang.ui.main.SettingsToggle(
                    stringResource(R.string.switch_bypass_apps_mode),
                    stringResource(if(bypassApps) R.string.premium_bypass_note else R.string.premium_only_note),
                    bypassApps,onBypassAppsChanged)
                TextButton(onClick=onInfoClick) { Text(stringResource(R.string.acc_per_app_proxy_information)) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = apps, key = { it.packageName }) { app ->
                    val throughVpn = if (bypassApps) app.packageName !in blacklist else app.packageName in blacklist
                    PremiumAppItem(app,throughVpn,perAppProxyEnabled) { onToggleApp(app.packageName) }
                }
            }
        }
    }

    if (showInfoDialog) {
        ConfirmDialog(
            message = stringResource(R.string.summary_pref_per_app_proxy),
            dismissText = null,
            onConfirm = {},
            onDismiss = { showInfoDialog = false },
        )
    }
}
