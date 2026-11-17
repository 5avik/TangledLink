package com.v2ray.ang.ui.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.v2ray.ang.ui.motion.MotionMath

/** The NavHost supplies actual route visibility, including its outgoing compositions. */
val LocalMotionVisible = compositionLocalOf { true }

@Composable
fun sceneMotionAllowed(visible: Boolean = LocalMotionVisible.current): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateAsState()
    val reduce = LocalReduceEffects.current
    var scale by remember { mutableFloatStateOf(0f) }
    var powerSave by remember { mutableStateOf(true) }
    var interactive by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val resolver = context.contentResolver
        val power = context.getSystemService(PowerManager::class.java)
        fun refresh() {
            scale = runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(0f)
            powerSave = power?.isPowerSaveMode ?: true
            interactive = power?.isInteractive ?: false
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh()
        }
        val resume = LifecycleEventObserver { _, event -> if(event==Lifecycle.Event.ON_RESUME) refresh() }
        refresh()
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        owner.lifecycle.addObserver(resume)
        onDispose {
            resolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
            owner.lifecycle.removeObserver(resume)
        }
    }
    return MotionMath.allowed(lifecycle==Lifecycle.State.RESUMED, visible && interactive, reduce, scale, powerSave)
}
