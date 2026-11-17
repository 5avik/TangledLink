package com.v2ray.ang.ui.motion

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/** AVDs use the supplied Canvas alternative: no software video codec or TextureView upload. */
internal enum class WorldPlaybackBackend { Video, NativeCanvas }
internal fun worldPlaybackBackend(hardware: String): WorldPlaybackBackend =
    if(hardware=="ranchu" || hardware=="goldfish") WorldPlaybackBackend.NativeCanvas else WorldPlaybackBackend.Video

@Composable
internal fun NativeWorldBackground(asset: WorldMotionAsset, allowMotion: Boolean, modifier: Modifier = Modifier) {
    val context=LocalContext.current
    val request=remember(context,asset.poster) {
        ImageRequest.Builder(context).data(asset.poster).memoryCachePolicy(CachePolicy.DISABLED).build()
    }
    val painter=rememberAsyncImagePainter(request)
    val image by painter.state.collectAsStateWithLifecycle()
    NativeMotionBackground(painter,asset.world,allowMotion && image is AsyncImagePainter.State.Success,modifier)
}
