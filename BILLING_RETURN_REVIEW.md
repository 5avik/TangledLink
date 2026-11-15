# Best Practices and Security Alignment Update: Billing return intents

Дата: 15 ноября 2026. Priority: Medium.

## Security alignment area / impact
Публичная ссылка открывает только проверку текущего аккаунта. Защита от поддельного подтверждения оплаты,
Intent redirection и выбора чужого аккаунта через URL. Никакой платёж или продление не запускается из Intent.

## Scope
Android: BillingReturnLink, MainActivity, MainContract, MainViewModel, BillingController,
PremiumScreens, AndroidManifest.xml, BillingReturnLinkTest, BillingControllerTest.
Backend: lava.mjs, lava.test.mjs, app-links.test.mjs, package.json.
Website: server/app-links/ (assetlinks.json, HTML/JS fallback, Nginx locations).
Новых зависимостей нет. Подтверждены публичные release и локальный debug сертификаты pro.tngl.

## Implementation summary
VIEW + точные scheme/host/port/rawPath проверяются одинаково в onCreate/onNewIntent.
onNewIntent вызывает setIntent. Extras и вложенные Intent не читаются и не запускаются.
status/invoiceId/account не используются для выбора аккаунта или признания оплаты.
Параметры не отражаются в HTML; fallback запускает только фиксированный пакет pro.tngl.
Секреты не передаются в ссылке. Браузерный переход вызывает только авторизованный GET текущего заказа.
При отсутствии токена сначала показывается вход. При отложенном webhook — ограниченная повторная проверка.
Подписка по-прежнему изменяется только сервером после сверки Lava API.
Файл связи домена выдаётся по HTTPS 443 без redirect. Manifest ограничен /billing/return.
При смене ключей сборки/App Signing нужно обновить assetlinks.json.

## Testing and verification
- Сборка Android и JVM-тесты прошли: допустимые/запрещённые URL, неверный action, userinfo, порт,
  чужой host, encoded path, отсутствие статуса, повтор возврата без заказа/браузера.
- 29 серверных тестов прошли: return URL для всех исходов, безопасный fallback, подписи, прежний биллинг.
- 11 Compose-тестов на Medium Phone прошли.
- Android pm get-app-links: app.tngl.pro = verified.
- Реальный Chrome на Medium Phone открыл TangledLink со страницы возврата.
- Холодный запуск (LaunchState COLD) и onNewIntent в уже открытом MainActivity проверены.
- URL с status=success показал реальное pending-состояние, не выдал оплату за успешную.
- Публичные HTTPS endpoints вернули 200, Nginx config test прошёл, webhook 401/200 работает.
- Production настроен для новых счетов. Уже созданные счета не перевыставлялись.
- Реальный платёж/автосписание для проверки не выполнялись.

## Not run
Полный платёж Lava → банк → return, все Android-браузеры, ручное нажатие fallback при заблокированном auto-open,
TalkBack, полная матрица D-pad, возврат с отсутствующей/просроченной сессией на реальном устройстве.
Путь отсутствующей сессии реализован в ViewModel; новые ключи/подписи сторонних сборок не авторизованы.

## Implementation diff
Ниже diff затронутых файлов рабочего дерева и нового валидатора.
```diff
diff --git i/app/src/main/AndroidManifest.xml w/app/src/main/AndroidManifest.xml
index eeee0e1..7d78b86 100644
--- i/app/src/main/AndroidManifest.xml
+++ w/app/src/main/AndroidManifest.xml
@@ -81,6 +81,18 @@
             <intent-filter>
                 <action android:name="android.service.quicksettings.action.QS_TILE_PREFERENCES" />
             </intent-filter>
+            <intent-filter android:autoVerify="true">
+                <action android:name="android.intent.action.VIEW" />
+                <category android:name="android.intent.category.DEFAULT" />
+                <category android:name="android.intent.category.BROWSABLE" />
+                <data android:scheme="https" android:host="app.tngl.pro" android:path="/billing/return" />
+            </intent-filter>
+            <intent-filter>
+                <action android:name="android.intent.action.VIEW" />
+                <category android:name="android.intent.category.DEFAULT" />
+                <category android:name="android.intent.category.BROWSABLE" />
+                <data android:scheme="tangledlink" android:host="billing" android:path="/return" />
+            </intent-filter>
 
             <meta-data
                 android:name="android.app.shortcuts"
diff --git i/app/src/main/java/com/v2ray/ang/ui/main/BillingController.kt w/app/src/main/java/com/v2ray/ang/ui/main/BillingController.kt
index 775e03d..7b73dd1 100644
--- i/app/src/main/java/com/v2ray/ang/ui/main/BillingController.kt
+++ w/app/src/main/java/com/v2ray/ang/ui/main/BillingController.kt
@@ -8,15 +8,20 @@ import kotlinx.coroutines.*
 import kotlinx.coroutines.flow.MutableStateFlow
 import kotlinx.coroutines.flow.asStateFlow
 import kotlinx.coroutines.flow.update
+import kotlinx.coroutines.flow.receiveAsFlow
 
 data class BillingUiState(
     val visible: Boolean = false,
     val busy: Boolean = false,
     val snapshot: BillingSnapshot? = null,
-    val error: Int? = null
+    val error: Int? = null,
+    val email: String = "",
+    val planId: String = "month30",
+    val hasCreatedOrder: Boolean = false,
+    val returnSequence: Long = 0
 ) {
     val canPay: Boolean
-        get() = visible && !busy && error == null && snapshot?.order?.status == "pending"
+        get() = visible && !busy && error == null && snapshot?.order?.status == "pending" && snapshot.order.paymentUrl?.startsWith("https://") == true
 }
 
 /** Owned by MainViewModel. All server operations run on its IO dispatcher. */
@@ -31,35 +36,68 @@ internal class BillingController(
     private var job: Job? = null
     private var appliedOrder: String? = null
     private var generation = 0
+    private val payments = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
+    val paymentEvents = payments.receiveAsFlow()
 
-    fun open() {
+    fun open(planId: String = "month30") {
         generation++
         job?.cancel()
-        state.value = BillingUiState(visible = true)
+        while (payments.tryReceive().isSuccess) { /* Drop cancelled navigation. */ }
+        state.value = BillingUiState(visible = true, planId = planId)
         refresh()
     }
+    fun returnFromPayment() {
+        generation++
+        job?.cancel()
+        while (payments.tryReceive().isSuccess) { /* Returning must never reopen checkout. */ }
+        state.value = BillingUiState(visible = true, hasCreatedOrder = true,
+            returnSequence = state.value.returnSequence + 1)
+        run(awaitConfirmation = true) { gateway.load() }
+    }
     fun close() {
         generation++
         job?.cancel()
+        while (payments.tryReceive().isSuccess) { /* Drop cancelled navigation. */ }
         state.update { it.copy(visible = false, busy = false) }
     }
     fun paymentUrl(): String? = if (state.value.canPay) {
-        com.v2ray.ang.handler.TnglBillingClient.PAYMENT_URL
+        state.value.snapshot?.order?.paymentUrl
     } else null
 
+    fun email(value: String) { if (!state.value.busy) state.update { it.copy(email = value.take(254), error = null) } }
+    fun selectPlan(value: String) { if (!state.value.busy) state.update { it.copy(planId = value, error = null) } }
     fun refresh() = run { gateway.load() }
     fun create() {
         val snapshot = state.value.snapshot ?: return
-        run { snapshot.copy(order = gateway.create(snapshot.plan.id)) }
+        val value = state.value
+        val email = value.email.trim()
+        if (!Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(email)) {
+            state.update { it.copy(error = R.string.billing_invalid_email) }; return
+        }
+        if (snapshot.plans.none { it.id == value.planId }) {
+            state.update { it.copy(error = R.string.billing_manual_review) }; return
+        }
+        run(openPayment = true) { snapshot.copy(order = gateway.create(value.planId, email)) }
     }
-    private fun run(operation: () -> BillingSnapshot) {
+    private fun run(openPayment: Boolean = false, awaitConfirmation: Boolean = false, operation: () -> BillingSnapshot) {
         if (state.value.busy) return
         state.update { it.copy(busy = true, error = null) }
         val currentGeneration = ++generation
         job = scope.launch {
             try {
-                val snapshot = withContext(io) { operation() }
-                state.update { it.copy(snapshot = snapshot) }
+                val snapshot = withContext(io) {
+                    var result = operation()
+                    if (awaitConfirmation) repeat(6) {
+                        if (result.order?.status !in listOf("pending", "applying", "retry")) return@withContext result
+                        delay(1500)
+                        result = operation()
+                    }
+                    result
+                }
+                if (currentGeneration != generation) return@launch
+                state.update { it.copy(snapshot = snapshot, hasCreatedOrder = it.hasCreatedOrder || openPayment) }
+                if (openPayment) snapshot.order?.takeIf { it.status == "pending" }
+                    ?.paymentUrl?.takeIf { it.startsWith("https://") }?.let { payments.trySend(it) }
                 val paid = snapshot.order?.takeIf { it.status == "paid" && !it.pendingSync }
                 if (paid != null && paid.id != appliedOrder) {
                     appliedOrder = paid.id
@@ -68,6 +106,8 @@ internal class BillingController(
             } catch (cancelled: CancellationException) { throw cancelled }
             catch (e: Exception) {
                 val error = when ((e as? TnglAuthException)?.errorCode) {
+                    "invalid_email" -> R.string.billing_invalid_email
+                    "invoice_requires_review", "existing_order_different_plan", "order_requires_review" -> R.string.billing_manual_review
                     "billing_unavailable" -> R.string.billing_unavailable
                     "session_expired" -> R.string.auth_session_expired
                     "client_disabled", "client_expiry_requires_review", "traffic_depleted",
diff --git i/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt w/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt
index 950f72a..ee658f6 100644
--- i/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt
+++ w/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt
@@ -10,6 +10,7 @@ import androidx.activity.result.contract.ActivityResultContracts
 import androidx.activity.viewModels
 import androidx.compose.runtime.Composable
 import androidx.lifecycle.lifecycleScope
+import androidx.lifecycle.repeatOnLifecycle
 import com.v2ray.ang.AngApplication
 import com.v2ray.ang.AppConfig
 import com.v2ray.ang.R
@@ -94,8 +95,42 @@ class MainActivity : HelperBaseComponentActivity() {
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         mainViewModel.onAction(MainAction.Initialize)
+        handleBillingReturn(intent)
+        lifecycleScope.launch {
+            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
+                mainViewModel.billingPaymentEvents.collect { url -> Utils.openUri(this@MainActivity, url) }
+            }
+        }
 
         checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
+        if (savedInstanceState == null) lifecycleScope.launch {
+            if (mainViewModel.shouldConnectOnLaunch()) {
+                requestServiceStart()
+            }
+        }
+    }
+
+    override fun onNewIntent(intent: Intent) {
+        super.onNewIntent(intent)
+        setIntent(intent)
+        handleBillingReturn(intent)
+    }
+
+    private fun handleBillingReturn(incoming: Intent?) {
+        if (BillingReturnLink.accepts(incoming?.action, incoming?.dataString)) {
+            // Ignore extras, invoiceId and status: ownership and payment are checked by the server.
+            mainViewModel.onAction(MainAction.ReturnFromBilling)
+        }
+    }
+
+    override fun onResume() {
+        super.onResume()
+        mainViewModel.observeTraffic(true)
+    }
+
+    override fun onPause() {
+        mainViewModel.observeTraffic(false)
+        super.onPause()
     }
 
     @Composable
@@ -106,6 +141,11 @@ class MainActivity : HelperBaseComponentActivity() {
             onAction = { action ->
                 when (action) {
                     MainAction.ToggleService -> handleFabAction()
+                    MainAction.OpenSystemVpnSettings -> openSystemSettings(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
+                    MainAction.OpenNotificationSettings -> openSystemSettings(
+                        if (Build.VERSION.SDK_INT >= 26) Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
+                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
+                        else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
                     MainAction.TestCurrentServer -> handleLayoutTestClick()
                     MainAction.ChangeAccount -> {
                         LauncherManager.stopService(this)
@@ -162,6 +202,12 @@ class MainActivity : HelperBaseComponentActivity() {
         settingsActivityLauncher.launch(intent)
     }
 
+    private fun openSystemSettings(intent: Intent) {
+        try { startActivity(intent) }
+        catch (error: android.content.ActivityNotFoundException) { toastError(R.string.toast_failure) }
+        catch (error: SecurityException) { toastError(R.string.toast_failure) }
+    }
+
     private fun handleFabAction() {
         if (mainViewModel.uiState.value.isRunning) {
             LauncherManager.stopService(this)
@@ -194,6 +240,7 @@ class MainActivity : HelperBaseComponentActivity() {
         ) {
             checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
         }
+        mainViewModel.connectionStarting()
         LauncherManager.startService(this)
     }
 
diff --git i/app/src/main/java/com/v2ray/ang/ui/main/MainContract.kt w/app/src/main/java/com/v2ray/ang/ui/main/MainContract.kt
index cbe68f9..7beef4b 100644
--- i/app/src/main/java/com/v2ray/ang/ui/main/MainContract.kt
+++ w/app/src/main/java/com/v2ray/ang/ui/main/MainContract.kt
@@ -21,6 +21,8 @@ data class MainUiState(
     val selectedGroupId: String = "",
     val selectedGuid: String? = null,
     val isRunning: Boolean = false,
+    val isConnecting: Boolean = false,
+    val traffic: TrafficUiState = TrafficUiState(),
     val isTesting: Boolean = false,
     val status: MainStatus = MainStatus.Disconnected,
     val locateTarget: LocateTarget? = null,
@@ -33,12 +35,26 @@ data class MainUiState(
     val authError: Int? = null,
     val showTnglUpdateDialog: Boolean = false,
     val updateResult: com.v2ray.ang.dto.CheckUpdateResult? = null
-)
+) {
+    /** Successful authentication stores the login as the dedicated TNGL subscription name. */
+    val accountLogin: String?
+        get() = if (showTnglLoginDialog) null else groups
+            .firstOrNull { it.id == com.v2ray.ang.AppConfig.TNGL_SUB_ID }
+            ?.remarks?.trim()?.takeIf { it.isNotEmpty() }
+}
 
 /**
  * All possible user interaction intents
  */
 sealed interface MainAction {
+    data class ToggleFavorite(val guid: String) : MainAction
+    data class SelectPremiumPlan(val months: Int) : MainAction
+    data class SetConnectOnLaunch(val enabled: Boolean) : MainAction
+    data class SetLanguage(val code: String) : MainAction
+    data class SearchPremiumServers(val query: String) : MainAction
+    data class FilterPremiumServers(val filter: Int) : MainAction
+    data object OpenSystemVpnSettings : MainAction
+    data object OpenNotificationSettings : MainAction
     data object Initialize : MainAction
     data object RefreshGroups : MainAction
     data object ToggleService : MainAction
@@ -78,8 +94,12 @@ sealed interface MainAction {
     data object LocateHandled : MainAction
 
     data object ShowBilling : MainAction
+    data object ReturnFromBilling : MainAction
+    data class BeginBilling(val months: Int) : MainAction
     data object DismissBilling : MainAction
     data object CreateBillingOrder : MainAction
+    data class BillingEmail(val value: String) : MainAction
+    data class BillingPlanSelected(val id: String) : MainAction
     data object RefreshBilling : MainAction
     data object OpenBillingPayment : MainAction
     data object ChangeAccount : MainAction
diff --git i/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt w/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt
index 47e3160..ff753d7 100644
--- i/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt
+++ w/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt
@@ -39,6 +39,11 @@ import kotlinx.coroutines.flow.SharingStarted
 import kotlinx.coroutines.flow.StateFlow
 import kotlinx.coroutines.flow.asStateFlow
 import kotlinx.coroutines.flow.map
+import kotlinx.coroutines.flow.first
+import kotlinx.coroutines.flow.combine
+import kotlinx.coroutines.flow.collectLatest
+import kotlinx.coroutines.flow.distinctUntilChanged
+import kotlinx.coroutines.flow.flatMapLatest
 import kotlinx.coroutines.flow.stateIn
 import kotlinx.coroutines.flow.update
 import kotlinx.coroutines.launch
@@ -93,7 +98,25 @@ class MainViewModel(
     val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
     private val billing = BillingController(viewModelScope, ioDispatcher,
         com.v2ray.ang.handler.TnglBillingClient, onPaid = { updateTnglSubscription() })
+    private val premiumStore = PremiumStore(application)
+    internal val premiumState = premiumStore.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PremiumPreferences())
+    private val browserInput = MutableStateFlow(PremiumBrowserInput())
+    internal val premiumBrowserInput = browserInput.asStateFlow()
+    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
+    internal val premiumBrowserState = _uiState.map { it.selectedGroupId }.distinctUntilChanged()
+        .flatMapLatest { groupId ->
+            combine(serverGroupState(groupId), premiumState, browserInput) { group, preferences, input ->
+                withContext(defaultDispatcher) {
+                    PremiumBrowserState(input, filterPremiumServers(
+                        group.rows, input, preferences.favorites,
+                        countryFlags.mapValues { dataSource.getString(it.value) }
+                    ))
+                }
+            }
+        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PremiumBrowserState())
+    private var billingReturnPending = false
     val billingState = billing.uiState
+    val billingPaymentEvents = billing.paymentEvents
     fun billingPaymentUrl(): String? = billing.paymentUrl()
 
     // ---------- Keyword filtering ----------
@@ -137,6 +160,9 @@ class MainViewModel(
 
     private fun handleServiceEvent(event: MainServiceEvent) {
         when (event) {
+            is MainServiceEvent.Traffic -> _uiState.update { state ->
+                if (state.isRunning) state.copy(traffic = state.traffic.receive(event.snapshot)) else state
+            }
             MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
             MainServiceEvent.StateNotRunning -> updateRunningState(false, clearTestingText = false)
             MainServiceEvent.StateStartSuccess -> {
@@ -316,7 +342,29 @@ class MainViewModel(
                 _uiState.update { it.copy(shareQRCodeBitmap = null) }
             }
 
-            MainAction.ShowBilling -> billing.open()
+            is MainAction.ToggleFavorite -> viewModelScope.launch { runCatching { premiumStore.toggle(action.guid) }.onFailure { toastError(R.string.toast_failure) } }
+            is MainAction.SelectPremiumPlan -> viewModelScope.launch { runCatching { premiumStore.select(action.months) }.onFailure { toastError(R.string.toast_failure) } }
+            is MainAction.SetConnectOnLaunch -> viewModelScope.launch {
+                try { premiumStore.setConnectOnLaunch(action.enabled) }
+                catch (cancelled: CancellationException) { throw cancelled }
+                catch (error: Exception) { toastError(R.string.toast_failure) }
+            }
+            is MainAction.SearchPremiumServers -> browserInput.update { it.copy(query = action.query) }
+            is MainAction.FilterPremiumServers -> if (action.filter in 0..2) browserInput.update { it.copy(filter = action.filter) }
+            is MainAction.SetLanguage -> if (action.code in setOf("ru", "en")) {
+                com.v2ray.ang.handler.AppLocaleManager.setApplicationLanguage(action.code)
+            }
+            is MainAction.BillingEmail -> billing.email(action.value)
+            is MainAction.BillingPlanSelected -> billing.selectPlan(action.id)
+            is MainAction.BeginBilling -> if (action.months in listOf(1,3,6,12)) billing.open("month" + action.months * 30)
+            MainAction.ReturnFromBilling -> {
+                billingReturnPending = true
+                if (!TnglSessionStore.token().isNullOrBlank()) {
+                    billingReturnPending = false
+                    billing.returnFromPayment()
+                } else _uiState.update { it.copy(showTnglLoginDialog = true) }
+            }
+            MainAction.ShowBilling -> billing.open("month" + premiumState.value.months * 30)
             MainAction.DismissBilling -> billing.close()
             MainAction.CreateBillingOrder -> billing.create()
             MainAction.RefreshBilling -> billing.refresh()
@@ -325,6 +373,8 @@ class MainViewModel(
                 _uiState.update { it.copy(showTnglUpdateDialog = false) }
             }
 
+            MainAction.OpenSystemVpnSettings,
+            MainAction.OpenNotificationSettings,
             MainAction.ToggleService,
             MainAction.TestCurrentServer,
             MainAction.ImportQRcode,
@@ -613,6 +663,10 @@ class MainViewModel(
                     if (dataSource.getSelectServer() !in servers) servers.firstOrNull()?.let(dataSource::setSelectServer)
                 }
                 _uiState.update { it.copy(showTnglLoginDialog = false) }
+                if (billingReturnPending) {
+                    billingReturnPending = false
+                    billing.returnFromPayment()
+                }
                 setupGroupTab(forceRefresh = true)
                 refreshSelectedGuid()
             } catch (cancelled: CancellationException) { throw cancelled }
@@ -1127,12 +1181,52 @@ class MainViewModel(
         _uiState.update { it.copy(locateTarget = null) }
     }
 
+    internal suspend fun shouldConnectOnLaunch(): Boolean {
+        initialPageReady.await()
+        return canConnectOnLaunch(premiumStore.state.first(), uiState.value)
+    }
+
     // ---------- Running state ----------
+    private var trafficPolling: Job? = null
+
+    fun observeTraffic(visible: Boolean) {
+        trafficPolling?.cancel()
+        trafficPolling = null
+        if (!visible) return
+        trafficPolling = viewModelScope.launch {
+            dataSource.sendMsg2Service(AppConfig.MSG_REGISTER_CLIENT, "")
+            uiState.map { it.isRunning }.distinctUntilChanged().collectLatest { running ->
+                if (running) while (true) {
+                    dataSource.sendMsg2Service(AppConfig.MSG_QUERY_TRAFFIC, "")
+                    delay(1_000L)
+                }
+            }
+        }
+    }
+
+    private var connectionTimeout: Job? = null
+
+    fun connectionStarting() {
+        connectionTimeout?.cancel()
+        _uiState.update { it.copy(isConnecting = true) }
+        connectionTimeout = viewModelScope.launch {
+            delay(30_000)
+            if (uiState.value.isConnecting) {
+                _uiState.update { it.copy(isConnecting = false) }
+                toastError(R.string.toast_services_failure)
+            }
+        }
+    }
+
     private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
+        connectionTimeout?.cancel()
+        connectionTimeout = null
         if (!running || clearTestingText) testRequests.invalidateCurrent()
         _uiState.update { state ->
             state.copy(
                 isRunning = running,
+                isConnecting = false,
+                traffic = if (running) state.traffic else TrafficUiState(),
                 isTesting = testRequests.isTesting,
                 status = if (!clearTestingText && state.isRunning == running) state.status
                 else if (running) MainStatus.Connected else MainStatus.Disconnected
--- /dev/null
+++ b/app/src/main/java/com/v2ray/ang/ui/main/BillingReturnLink.kt
@@ -0,0 +1,15 @@
+package com.v2ray.ang.ui.main
+
+import java.net.URI
+
+/** This link is a request to refresh the authenticated account, never proof of payment. */
+internal object BillingReturnLink {
+    fun accepts(action: String?, value: String?): Boolean {
+        if (action != "android.intent.action.VIEW" || value == null || value.length > 2048) return false
+        val uri = try { URI(value) } catch (_: Exception) { return false }
+        if (uri.rawUserInfo != null || uri.rawFragment != null) return false
+        return (uri.scheme.equals("https", true) && uri.host.equals("app.tngl.pro", true) &&
+            uri.port in listOf(-1,443) && uri.rawPath == "/billing/return") ||
+            (uri.scheme == "tangledlink" && uri.host == "billing" && uri.port == -1 && uri.rawPath == "/return")
+    }
+}

```
