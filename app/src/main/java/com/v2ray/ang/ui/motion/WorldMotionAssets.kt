package com.v2ray.ang.ui.motion

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.ThemeId
import kotlin.math.max

data class WorldMotionAsset(val world: MotionWorld, @RawRes val video: Int, @DrawableRes val poster: Int)

object WorldMotionAssets {
    operator fun get(id: ThemeId): WorldMotionAsset? = when(id) {
        ThemeId.Original -> WorldMotionAsset(MotionWorld.ORIGINAL, R.raw.tngl_motion_original, R.drawable.tngl_bg_original)
        ThemeId.Sakura -> WorldMotionAsset(MotionWorld.SAKURA, R.raw.tngl_motion_sakura, R.drawable.tngl_bg_sakura)
        ThemeId.Aurora -> WorldMotionAsset(MotionWorld.AURORA, R.raw.tngl_motion_aurora, R.drawable.tngl_bg_aurora)
        ThemeId.Cyberpunk -> WorldMotionAsset(MotionWorld.CYBERPUNK, R.raw.tngl_motion_cyberpunk, R.drawable.tngl_bg_cyberpunk)
        ThemeId.Terminal -> null
    }
}

/** TextureView initially stretches to its bounds. Scale it equally in video coordinates to Crop. */
internal fun centerCropScale(viewWidth: Int, viewHeight: Int, videoWidth: Float, videoHeight: Float): Pair<Float, Float> {
    if(viewWidth<=0 || viewHeight<=0 || !videoWidth.isFinite() || !videoHeight.isFinite() || videoWidth<=0 || videoHeight<=0)
        return 1f to 1f
    val scale=max(viewWidth/videoWidth, viewHeight/videoHeight)
    return scale*videoWidth/viewWidth to scale*videoHeight/viewHeight
}
