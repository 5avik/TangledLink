@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.v2ray.ang.ui.motion

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest

internal enum class BackgroundPlaybackEvent { PosterReady, Created, FirstFrame, Loop, Released, Failed }
internal fun interface BackgroundSession { fun release() }
internal fun interface BackgroundSessionFactory {
    fun create(context: Context, view: TextureView, raw: Int, event: (BackgroundPlaybackEvent)->Unit): BackgroundSession
}
private val Media3BackgroundFactory = BackgroundSessionFactory { context, view, raw, event ->
    Media3BackgroundSession(context, view, raw, event)
}

/** One local decoder while allowed. The poster is retained below the transparent surface. */
@Composable
internal fun AnimatedWorldBackground(
    asset: WorldMotionAsset,
    allowMotion: Boolean,
    modifier: Modifier = Modifier,
    factory: BackgroundSessionFactory = Media3BackgroundFactory,
    onPlaybackEvent: (BackgroundPlaybackEvent)->Unit = {},
) {
    val context=LocalContext.current
    var failed by remember(asset.video, allowMotion) { mutableStateOf(false) }
    var posterReady by remember(asset.poster) { mutableStateOf(false) }
    val event by rememberUpdatedState(onPlaybackEvent)
    val poster=remember(context,asset.poster) {
        ImageRequest.Builder(context).data(asset.poster).memoryCachePolicy(CachePolicy.DISABLED).build()
    }
    Box(modifier.clipToBounds().background(com.v2ray.ang.ui.compose.LocalThemeConfig.current.scene.background).clearAndSetSemantics {}) {
        AsyncImage(poster,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,
            onSuccess={posterReady=true;event(BackgroundPlaybackEvent.PosterReady)})
        if(allowMotion && posterReady && !failed) key(asset.video) {
            val view=remember(context,asset.video) {
                TextureView(context).apply {
                    isOpaque=false
                    alpha=0f
                    isClickable=false
                    isFocusable=false
                    isFocusableInTouchMode=false
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            }
            AndroidView(factory={view}, modifier=Modifier.fillMaxSize().clearAndSetSemantics {})
            DisposableEffect(view,asset.video,factory) {
                val session=runCatching {
                    factory.create(context,view,asset.video) { change ->
                        if(change==BackgroundPlaybackEvent.FirstFrame) view.alpha=1f
                        if(change==BackgroundPlaybackEvent.Failed) {view.alpha=0f;failed=true}
                        event(change)
                    }
                }.getOrElse {
                    failed=true
                    event(BackgroundPlaybackEvent.Failed)
                    null
                }
                onDispose {
                    view.alpha=0f
                    session?.release()
                }
            }
        }
    }
}

/** Media operations stay on the application/UI thread; ExoPlayer performs file IO/decoding internally. */
private class Media3BackgroundSession(
    context: Context,
    private val view: TextureView,
    raw: Int,
    private val event: (BackgroundPlaybackEvent)->Unit,
) : BackgroundSession {
    private val renderers=DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true).apply {
        // The AVD goldfish H.264 codec reports support but fails queueCSDInputBuffer on API 37.
        // Prefer the platform software decoder on these virtual devices; physical devices retain Media3 defaults.
        if(Build.HARDWARE=="ranchu" || Build.HARDWARE=="goldfish") {
            setMediaCodecSelector { mime, secure, tunnel ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mime,secure,tunnel).sortedBy { !it.softwareOnly }
            }
        }
    }
    private val player=ExoPlayer.Builder(context.applicationContext,renderers).build()
    private var width=720f
    private var height=1600f
    private var first=false
    private var released=false
    private fun crop() {
        val (x,y)=centerCropScale(view.width,view.height,width,height)
        view.setTransform(Matrix().apply {setScale(x,y,view.width/2f,view.height/2f)})
    }
    private val layout=View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> crop() }
    private val listener=object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            width=videoSize.width * videoSize.pixelWidthHeightRatio
            height=videoSize.height.toFloat()
            crop()
        }
        override fun onRenderedFirstFrame() {
            if(!first && !released) {first=true;event(BackgroundPlaybackEvent.FirstFrame)}
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && !released) event(BackgroundPlaybackEvent.Loop)
        }
        override fun onPlayerError(error: PlaybackException) {
            if(!released) event(BackgroundPlaybackEvent.Failed)
        }
    }
    init {
        try {
            view.addOnLayoutChangeListener(layout)
            player.volume=0f
            player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
            player.repeatMode=Player.REPEAT_MODE_ONE
            player.addListener(listener)
            player.setVideoTextureView(view)
            player.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$raw")))
            player.prepare()
            player.play()
            event(BackgroundPlaybackEvent.Created)
        } catch(error: Exception) {
            release()
            throw error
        }
    }
    override fun release() {
        if(released) return
        released=true
        view.removeOnLayoutChangeListener(layout)
        player.removeListener(listener)
        player.clearVideoTextureView(view)
        player.release()
        event(BackgroundPlaybackEvent.Released)
    }
}
