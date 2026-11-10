package com.v2ray.ang.ui.checkupdate

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateDownloadState(
    val downloading: Boolean = false,
    val progress: Float? = null,
    val ready: Boolean = false,
    val failed: Boolean = false
)

class UpdateDownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val mutableState = MutableStateFlow(UpdateDownloadState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var call: okhttp3.Call? = null
    val apk: File get() = File(getApplication<Application>().cacheDir, "updates/update.apk")

    fun download(url: String) {
        if (job?.isActive == true || state.value.ready) return
        mutableState.value = UpdateDownloadState(downloading = true)
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(isUpdateDownloadUrl(url))
                    apk.parentFile!!.mkdirs()
                    apk.delete()
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS).build()
                    val request = client.newCall(Request.Builder().url(url).build())
                    call = request
                    request.execute().use { response ->
                        check(response.isSuccessful)
                        val body = response.body ?: error("Empty body")
                        val total = body.contentLength()
                        var received = 0L
                        var lastPercent = -1
                        body.byteStream().use { input ->
                            apk.outputStream().use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    received += count
                                    check(received <= 512L * 1024 * 1024)
                                    output.write(buffer, 0, count)
                                    val percent = if (total > 0) (received * 100 / total).toInt() else -1
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        mutableState.value = UpdateDownloadState(true, if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null)
                                    }
                                }
                            }
                        }
                        check(received > 0 && (total <= 0 || received == total))
                    }
                    validateApk()
                }
                mutableState.value = UpdateDownloadState(ready = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { apk.delete() }
                mutableState.value = UpdateDownloadState(failed = true)
            } finally {
                call = null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk() {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES) ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        check(archive.packageName == context.packageName)
        val candidateSignatures = archive.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val currentSignatures = installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        check(candidateSignatures.isNotEmpty() && candidateSignatures == currentSignatures)
        val candidateVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        check(candidateVersion >= installedVersion)
    }

    fun cancel() {
        call?.cancel()
        job?.cancel()
        mutableState.value = UpdateDownloadState()
    }

    fun installationFailed() { mutableState.value = UpdateDownloadState(ready = true, failed = true) }

    override fun onCleared() {
        call?.cancel()
        super.onCleared()
    }
}

internal fun isUpdateDownloadUrl(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    uri.scheme == "https" && uri.host == "github.com" &&
        uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
        uri.path.startsWith("/5avik/TangledLink/releases/download/") && uri.path.endsWith(".apk")
}.getOrDefault(false)

class UpdateDownloadActivity : BaseComponentActivity() {
    private val model: UpdateDownloadViewModel by viewModels()
    private var awaitingPermission = false
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        awaitingPermission = false
        if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) installUpdate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awaitingPermission = savedInstanceState?.getBoolean("awaitingPermission") ?: false
        if (savedInstanceState == null) model.download(intent.getStringExtra("downloadUrl").orEmpty())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaitingPermission", awaitingPermission)
        super.onSaveInstanceState(outState)
    }

    private fun installUpdate() {
        try {
            if (!model.state.value.ready || awaitingPermission) return
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                awaitingPermission = true
                permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.cache", model.apk)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            awaitingPermission = false
            model.installationFailed()
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by model.state.collectAsStateWithLifecycle()
        LaunchedEffect(state.ready) { if (state.ready) installUpdate() }
        Scaffold(topBar = {
            AppTopBar(title = stringResource(R.string.updater_title), onBackClick = { finish() })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(when {
                    state.failed -> R.string.updater_failed
                    state.ready -> R.string.updater_ready
                    state.downloading -> R.string.updater_downloading
                    else -> R.string.updater_cancelled
                }))
                if (state.downloading) {
                    state.progress?.let {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.updater_progress, (it * 100).toInt()))
                    } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { model.cancel() }) { Text(stringResource(R.string.action_cancel)) }
                } else if (state.ready) {
                    Button(onClick = { installUpdate() }) { Text(stringResource(R.string.updater_install)) }
                } else {
                    Button(onClick = { model.download(intent.getStringExtra("downloadUrl").orEmpty()) }) {
                        Text(stringResource(R.string.updater_retry))
                    }
                }
                Text(stringResource(R.string.updater_permission_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
