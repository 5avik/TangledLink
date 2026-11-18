package com.v2ray.ang.ui.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.sceneMotionAllowed
import kotlin.math.*

private val Green = Color(0xFF65FF85)
private val Teal = Color(0xFF00DDB5)
private val Dark = Color(0xFF020B09)
private val Red = Color(0xFFFF5364)

@Composable
internal fun AuthPortalHost(showLogin: Boolean, success: Long, home: @Composable () -> Unit,
    login: @Composable (exiting: Boolean) -> Unit) {
    var completed by remember { mutableLongStateOf(success) }
    val exiting = !showLogin && success != completed
    val motion = sceneMotionAllowed()
    val check = remember(success) { Animatable(0f) }
    val reveal = remember(success) { Animatable(0f) }
    LaunchedEffect(exiting, motion, success) {
        if (exiting) {
            if (motion) {
                check.animateTo(1f, tween(420))
                reveal.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            }
            completed = success
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (!showLogin) home()
        if (showLogin || exiting && motion) {
            Box(Modifier.fillMaxSize().drawWithCache {
                val hole = Path()
                val cover = hypot(size.width, size.height) / 2f
                onDrawWithContent {
                    if (exiting && reveal.value > 0f) {
                        hole.reset()
                        hole.addOval(Rect(center, cover * reveal.value))
                        clipPath(hole, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                    } else drawContent()
                }
            }) {
                login(exiting)
                if (exiting) {
                    Canvas(Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }) {
                        drawRect(Dark.copy(alpha = .94f))
                    }
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                            if (reveal.value > 0f) Image(painterResource(R.drawable.tngl_brand_mark), null, Modifier.size(68.dp))
                            Canvas(Modifier.matchParentSize()) {
                                val progress = check.value
                                val radius = size.minDimension * .43f
                                drawCircle(Green, radius * (1f + progress * .35f), alpha = .22f * (1f - progress),
                                    style = Stroke(4.dp.toPx()))
                                drawArc(Green, -90f, 210f + 150f * progress, false,
                                    topLeft = center - Offset(radius,radius),
                                    size = androidx.compose.ui.geometry.Size(radius*2,radius*2),
                                    style = Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                                if (reveal.value == 0f) {
                                    val start = center + Offset(-radius*.43f,0f)
                                    val joint = center + Offset(-radius*.12f,radius*.29f)
                                    val end = center + Offset(radius*.46f,-radius*.32f)
                                    drawLine(Green,start,start+(joint-start)*(progress*2).coerceAtMost(1f),3.dp.toPx(),StrokeCap.Round)
                                    if(progress>.5f)drawLine(Green,joint,joint+(end-joint)*(progress*2-1),3.dp.toPx(),StrokeCap.Round)
                                }
                            }
                        }
                        Text(stringResource(R.string.auth_portal_welcome),color=Green)
                    }
                }
            }
            if (exiting) Canvas(Modifier.matchParentSize()) {
                val progress = reveal.value
                if (progress > 0f) {
                    val radius = hypot(size.width,size.height)/2f*progress
                    drawCircle(Teal,radius,alpha=(1f-progress)*.22f,style=Stroke(12.dp.toPx()))
                    drawCircle(Green,radius,alpha=1f-progress,style=Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

@Composable
internal fun AuthPortalLogo(loading: Boolean, failed: Boolean, motion: Boolean) {
    val phase = remember { Animatable(0f) }
    val transition = updateTransition(loading, label = "auth ring")
    val opacity by transition.animateFloat(
        transitionSpec = { tween(if(motion) 300 else 0) }, label = "ring opacity"
    ) { if(it)1f else 0f }
    LaunchedEffect(loading,motion) {
        if(loading && motion) while(true) {
            phase.animateTo(1f,tween(2200,easing=LinearEasing))
            phase.snapTo(0f)
        } else phase.snapTo(0f)
    }
    Box(Modifier.size(104.dp).drawWithCache {
        val scrim = Brush.radialGradient(
            0f to Dark, .73f to Dark, 1f to Color.Transparent,
            center = Offset(size.width / 2, size.height / 2), radius = size.minDimension * .55f
        )
        onDrawBehind { drawRect(scrim) }
    }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.tngl_brand_mark),null,Modifier.size(72.dp).graphicsLayer {
            val pulse = if(loading && motion) 1f+.0175f*(1f-cos(phase.value*2f*PI.toFloat())) else 1f
            scaleX=pulse
            scaleY=pulse
        })
        Canvas(Modifier.matchParentSize()) {
            if(failed && !loading) {
                drawCircle(Red,size.minDimension*.49f,style=Stroke(1.dp.toPx()))
                val marker = center + Offset(size.width*.34f,-size.height*.34f)
                drawCircle(Dark,6.dp.toPx(),marker)
                drawLine(Red,marker-Offset(0f,3.dp.toPx()),marker,1.5.dp.toPx(),StrokeCap.Round)
                drawCircle(Red,1.dp.toPx(),marker+Offset(0f,3.dp.toPx()))
            } else if(opacity>0f) {
                val angle=phase.value*360f
                for(i in 0..2) {
                    val radius=size.minDimension*(.41f+i*.035f)
                    val color=if(i==1)Teal else Green
                    val alpha=opacity*(if(i==2) .45f+.25f*cos(phase.value*2f*PI.toFloat()) else .9f)
                    drawArc(color,(if(i==1)-angle else angle)+120f*i,95f+20f*i,false,
                        topLeft=center-Offset(radius,radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2),
                        alpha=alpha,style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round))
                }
                val rad=phase.value*2f*PI.toFloat()
                val dot=center+Offset(cos(rad),sin(rad))*size.minDimension*.48f
                drawCircle(Green,4.dp.toPx(),dot,alpha=opacity*.2f)
                drawCircle(Green,2.dp.toPx(),dot,alpha=opacity)
            }
        }
    }
}
