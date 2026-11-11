package com.v2ray.ang.ui.checkupdate

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class UpdateDownloadActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun invalidDownloadShowsError() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, UpdateDownloadActivity::class.java)
            .putExtra("downloadUrl", "https://example.com/invalid.apk")
        ActivityScenario.launch<UpdateDownloadActivity>(intent).use {
            val error = context.getString(com.v2ray.ang.R.string.updater_failed)
            compose.waitUntil(10000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun completeReleaseDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val url = InstrumentationRegistry.getArguments().getString("downloadUrl")
        org.junit.Assume.assumeTrue(
            "Provide downloadUrl for a same-signed APK with versionCode >= the installed app",
            !url.isNullOrBlank()
        )
        val scenario = ActivityScenario.launch<UpdateDownloadActivity>(
            Intent(context, UpdateDownloadActivity::class.java).putExtra("downloadUrl", url))
        lateinit var model: UpdateDownloadViewModel
        scenario.onActivity { model = androidx.lifecycle.ViewModelProvider(it)[UpdateDownloadViewModel::class.java] }
        compose.waitUntil(180000) { model.state.value.ready || model.state.value.failed }
        org.junit.Assert.assertTrue("APK download and signature validation must succeed", model.state.value.ready)
        // Leave the system permission/installer screen visible for manual verification.
        compose.waitForIdle()
        Thread.sleep(20000)
        scenario.close()
    }

    @Test fun releaseDownloadStaysInsideApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context, UpdateDownloadActivity::class.java)
            .putExtra("downloadUrl", "https://github.com/5avik/TangledLink/releases/download/v0.6/tngl_0.6_arm64-v8a.apk")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val downloading = context.getString(com.v2ray.ang.R.string.updater_downloading)
        compose.waitUntil(10000) { compose.onAllNodesWithText(downloading).fetchSemanticsNodes().isNotEmpty() }
    }
}
