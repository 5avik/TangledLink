package com.v2ray.ang.ui.main

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Explicit opt-in: starts the saved profile using an already granted VPN permission and downloads 1 MiB. */
class TrafficIntegrationTest {
    @Test fun absentDaemonReportsNotRunning(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyVpnTraffic") == "true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AngApplication
        val repository=MainRepository(app)
        try {
            com.v2ray.ang.core.LauncherManager.stopService(app)
            repository.sendMsg2Service(AppConfig.MSG_QUERY_TRAFFIC,"")
            withTimeout(5000) {
                repository.mainServiceEvent.first { it is MainServiceEvent.StateNotRunning }
            }
        } finally { repository.close() }
    }

    @Test fun realDaemonCountersCrossProcessBoundary() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyVpnTraffic") == "true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AngApplication
        assumeTrue("VPN permission must already be granted",android.net.VpnService.prepare(app)==null)
        val oldSpeed=com.v2ray.ang.handler.MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED)
        var scenario: androidx.test.core.app.ActivityScenario<MainActivity>? = null
        val repository=MainRepository(app)
        var poll: Job?=null
        try {
            InstrumentationRegistry.getArguments().getString("notificationSpeed")?.let {
                com.v2ray.ang.handler.MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED,it.toBooleanStrict())
            }
            val activeScenario=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            scenario=activeScenario
            activeScenario.onActivity {
                com.v2ray.ang.core.LauncherManager.startService(it)
                com.v2ray.ang.core.LauncherManager.startService(it)
            }
            val running=withTimeout(15000) {
                repository.mainServiceEvent.first { it is MainServiceEvent.StateRunning || it is MainServiceEvent.StateStartSuccess || it is MainServiceEvent.StateStartFailure }
            }
            assertTrue("Saved VPN profile must start",running is MainServiceEvent.StateRunning || running is MainServiceEvent.StateStartSuccess)
            poll=launch {
                while (true) {
                    repository.sendMsg2Service(AppConfig.MSG_QUERY_TRAFFIC,"")
                    delay(1000)
                }
            }
            val transfer=async(Dispatchers.IO) {
                val client=OkHttpClient.Builder()
                    .proxy(Proxy(Proxy.Type.SOCKS,InetSocketAddress("127.0.0.1",SettingsManager.getSocksPort())))
                    .callTimeout(30,TimeUnit.SECONDS).build()
                try {
                    client.newCall(Request.Builder().url("https://speed.cloudflare.com/__down?bytes=1048576").build())
                        .execute().use { response ->
                            assertTrue("Download must succeed",response.isSuccessful)
                            response.body!!.bytes().size
                        }
                } finally {
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                }
            }
            var up=0.0
            var down=0.0
            val sample=withTimeout(35000) {
                repository.mainServiceEvent.first { event ->
                    if (event is MainServiceEvent.Traffic) {
                        up=maxOf(up,event.snapshot.upload)
                        down=maxOf(down,event.snapshot.download)
                    }
                    event is MainServiceEvent.Traffic && up>0 && down>0
                } as MainServiceEvent.Traffic
            }
            assertEquals(1048576,transfer.await())
            assertTrue(sample.snapshot.connectedMillis>0)
            InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {
                putString("traffic","Observed real upload=$up B/s, download=$down B/s, elapsed=${sample.snapshot.connectedMillis} ms; notificationSpeed=${com.v2ray.ang.handler.MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED)}")
            })
        } finally {
            poll?.cancelAndJoin()
            com.v2ray.ang.core.LauncherManager.stopService(app)
            com.v2ray.ang.core.LauncherManager.stopService(app)
            repository.close()
            scenario?.close()
            com.v2ray.ang.handler.MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED,oldSpeed)
        }
    }
}
