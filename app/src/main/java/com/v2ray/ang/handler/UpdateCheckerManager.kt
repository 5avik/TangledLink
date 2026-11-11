package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = if (includePreRelease) {
                AppConfig.APP_API_URL
            } else {
                AppConfig.APP_API_URL.concatUrl("latest")
            }

            val proxyUsername = SettingsManager.getSocksUsername()
            val proxyPassword = SettingsManager.getSocksPassword()

            var response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000
                )
            )
            if (response.isNullOrEmpty()) {
                val httpPort = SettingsManager.getHttpPort()
                response = HttpUtil.getUrlContent(
                    UrlContentRequest(
                        url = url,
                        timeout = 5000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword
                    )
                )
            }
            if (response.isNullOrEmpty()) {
                return@withContext CheckUpdateResult(hasUpdate = false)
            }

            val latestRelease = if (includePreRelease) {
                JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
                    ?.firstOrNull()
            } else {
                JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
            }
            if (latestRelease == null) {
                return@withContext CheckUpdateResult(hasUpdate = false)
            }

            val latestVersion = latestRelease.tagName.removePrefix("v").trim()
            LogUtil.i(
                AppConfig.TAG,
                "Checked GitHub update: latest=$latestVersion, current=${BuildConfig.VERSION_NAME}"
            )

            if (latestVersion.isNotEmpty() && compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
                val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                CheckUpdateResult(
                    hasUpdate = true,
                    latestVersion = latestVersion,
                    releaseNotes = latestRelease.body,
                    downloadUrl = downloadUrl,
                    isPreRelease = latestRelease.prerelease
                )
            } else {
                CheckUpdateResult(hasUpdate = false)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to check for update from GitHub", e)
            CheckUpdateResult(hasUpdate = false)
        }
    }

    private fun compareVersions(version1: String, version2: String): Int =
        compareReleaseVersions(version1, version2)

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        if (release.assets.isEmpty()) return ""
        val fDroid = "fdroid"
        val assetsByAbi = release.assets.filter { it.name.contains(abi, true) }
        val asset = if (BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)) {
            assetsByAbi.firstOrNull { it.name.contains(fDroid, ignoreCase = true) } ?: release.assets.firstOrNull()
        } else {
            assetsByAbi.firstOrNull { !it.name.contains(fDroid, ignoreCase = true) } ?: release.assets.firstOrNull()
        }
        return asset?.browserDownloadUrl.orEmpty()
    }
}
