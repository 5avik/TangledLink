package com.v2ray.ang.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import kotlinx.coroutines.delay

class SplashActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        SplashScreen {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onAnimationFinished)

    // A complete loop is 9 frames at 120 ms. Start the timer after decoding,
    // with a timeout so a failed resource load cannot trap the user here.
    LaunchedEffect(loaded, failed) {
        if (failed) {
            finish()
        } else {
            delay(if (loaded) 1_080L else 5_000L)
            finish()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF020809)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.raw.splash_logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { failed = true },
            modifier = Modifier.padding(32.dp).widthIn(max = 320.dp)
                .fillMaxWidth().aspectRatio(1f)
        )
    }
}
