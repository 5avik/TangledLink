package com.v2ray.ang.ui.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalThemeConfig

/** The global AppTheme owns palette and typography on every screen. */
@Composable
internal fun PremiumTheme(content: @Composable () -> Unit) = content()

@Composable
internal fun ConnectionButton(status: ConnectionStatus, enabled: Boolean = true, diameter: Dp = 228.dp, onClick: () -> Unit) {
    com.v2ray.ang.ui.compose.ConnectionPortal(status, enabled, diameter, onClick = onClick)
}

internal val countryFlags = linkedMapOf("🇷🇺" to R.string.premium_ru,"🇩🇪" to R.string.premium_de,
    "🇳🇱" to R.string.premium_nl,"🇬🇧" to R.string.premium_uk,"🇺🇸" to R.string.premium_us,
    "🇸🇬" to R.string.premium_sg,"🇫🇷" to R.string.premium_fr,"🇷🇴" to R.string.premium_ro)
@Composable
internal fun serverCountryName(flag: String?): String? {
    countryFlags[flag]?.let { return stringResource(it) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val locale = androidx.core.os.ConfigurationCompat.getLocales(configuration)[0]
        ?: java.util.Locale.ROOT
    return remember(flag, locale) { countryNameFromFlag(flag, locale) }
}

@Composable
internal fun ServerCard(row:ServerRowUiModel,selected:Boolean,favorite:Boolean,onSelect:()->Unit,onFavorite:(()->Unit)?,compact:Boolean=false) {
    val accent=MaterialTheme.colorScheme.primary
    Surface(onClick=onSelect,modifier=Modifier.fillMaxWidth().semantics { this.selected=selected },
        shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,if(selected)accent.copy(alpha=.65f)else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(start=18.dp,end=8.dp,top=if(compact)12.dp else 16.dp,bottom=if(compact)12.dp else 16.dp),verticalAlignment=Alignment.CenterVertically) {
            val label = row.label
            val country = serverCountryName(label.countryFlag)
            val title = when {
                label.automatic -> stringResource(R.string.premium_auto_select)
                country != null -> country
                label.detail.isNotBlank() -> label.detail
                else -> stringResource(R.string.premium_server)
            }
            val detail = when {
                label.automatic -> stringResource(R.string.premium_auto_select_note)
                country != null && label.detail.isNotBlank() -> label.detail
                else -> stringResource(R.string.premium_location)
            }
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.surfaceContainer,
                modifier=Modifier.padding(end=14.dp).size(40.dp).clearAndSetSemantics {}) {
                Box(contentAlignment=Alignment.Center) {
                    if (label.countryFlag != null) Text(label.countryFlag,fontSize=24.sp)
                    else Icon(androidx.compose.ui.res.painterResource(
                        if(label.automatic) R.drawable.ic_server_auto_24dp else R.drawable.ic_premium_servers),
                        contentDescription=null,modifier=Modifier.size(24.dp),tint=accent)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(compact)4.dp else 6.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(row.testDelayMillis>0)stringResource(R.string.premium_ping,row.testDelayMillis)else stringResource(R.string.premium_unknown_ping),
                    style=MaterialTheme.typography.bodySmall,color=if(row.testDelayMillis>0)accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(onFavorite!=null) {
                val favoriteLabel=stringResource(if(favorite) R.string.premium_unfavorite else R.string.premium_favorite)
                IconButton(onClick=onFavorite,modifier=Modifier.semantics {contentDescription=favoriteLabel}) {
                Text(if(favorite)"★" else "☆",fontSize=25.sp,color=if(favorite)accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier=Modifier.clearAndSetSemantics {})
                // IconButton supplies one accessible action.
            }} else Text("›",Modifier.padding(12.dp).clearAndSetSemantics {},fontSize=28.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TrafficChart(samples:List<Float>,download:String?,upload:String?,duration:String?,compact:Boolean=false) {
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if(compact)12.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                val labelStyle=if(compact)MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
                val valueStyle=MaterialTheme.typography.titleLarge.let { if(compact)it.copy(fontSize=20.sp,lineHeight=24.sp) else it }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.premium_download),style=labelStyle,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(download?:"—",style=valueStyle)
                }
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                    Text(stringResource(R.string.premium_upload),style=labelStyle,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(upload?:"—",style=valueStyle)
                }
            }
            val color=MaterialTheme.colorScheme.primary
            if(samples.isEmpty()) Text(stringResource(R.string.premium_empty_chart),Modifier.fillMaxWidth().heightIn(min=if(compact)32.dp else 72.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else Canvas(Modifier.fillMaxWidth().height(if(compact)32.dp else 72.dp)) {
                val finite=samples.takeLast(60).map { if(it.isFinite())it.coerceAtLeast(0f)else 0f }
                val max=finite.maxOrNull()?.coerceAtLeast(1f)?:1f
                val path=Path()
                finite.forEachIndexed { i,v -> val x=size.width*i/(finite.size-1).coerceAtLeast(1); val y=size.height*(1-v/max);if(i==0)path.moveTo(x,y)else path.lineTo(x,y) }
                drawPath(path,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.premium_duration),style=MaterialTheme.typography.bodySmall)
                Text(duration?:"—",style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun SubscriptionCard() {
    val accent=MaterialTheme.colorScheme.primary
    val animate = com.v2ray.ang.ui.compose.sceneMotionAllowed()
    val shimmer = if (animate) {
        rememberInfiniteTransition(label="pro sheen").animateFloat(
            -1f, 1f, infiniteRepeatable(tween(6500,easing=LinearEasing),RepeatMode.Reverse),label="sheen position")
    } else rememberUpdatedState(0f)
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Box(Modifier.heightIn(min=108.dp),contentAlignment=Alignment.CenterStart) {
            Canvas(Modifier.matchParentSize()) {
                val x=size.width * shimmer.value
                drawRect(Brush.linearGradient(
                    listOf(Color.Transparent,accent.copy(alpha=.05f),accent.copy(alpha=.22f),Color.Transparent),
                    start=Offset(x-size.width*.5f,0f),end=Offset(x+size.width,size.height)))
                val ribbon=Path().apply {
                    moveTo(size.width*.45f,size.height*1.3f)
                    cubicTo(size.width*.2f,-size.height*.3f,size.width*1.2f,size.height*1.1f,size.width*1.05f,-size.height*.2f)
                }
                drawPath(ribbon,accent.copy(alpha=.09f),style=Stroke(28.dp.toPx()))
            }
            Text(stringResource(R.string.premium_pro),Modifier.padding(24.dp),
                style=MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
internal fun SettingsItem(title:String,subtitle:String?,onClick:()->Unit,enabled:Boolean=true,icon:Int?=null) {
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
            icon?.let { Icon(androidx.compose.ui.res.painterResource(it), null, Modifier.padding(end=16.dp).size(22.dp), tint=MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›",modifier=Modifier.clearAndSetSemantics {},fontSize=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsToggle(title:String, subtitle:String, checked:Boolean, onCheckedChange:(Boolean)->Unit) {
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().toggleable(checked, role=Role.Switch, onValueChange=onCheckedChange).padding(20.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_flash_on_24dp),null,Modifier.padding(end=16.dp).size(22.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked,onCheckedChange=null,modifier=Modifier.padding(start=12.dp),colors=SwitchDefaults.colors(
                uncheckedTrackColor=MaterialTheme.colorScheme.surfaceContainer,
                uncheckedThumbColor=MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor=MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
internal fun BottomNavigationBar(selected:String,onSelect:(String)->Unit) {
    val routes=listOf("home","servers","subscription","more")
    val terminal = LocalThemeConfig.current.id == com.v2ray.ang.ui.compose.ThemeId.Terminal
    val labels=listOf(R.string.premium_home,if(terminal) R.string.terminal_console_title else R.string.premium_servers,
        R.string.premium_subscription,R.string.premium_more)
    val icons=listOf(R.drawable.ic_premium_home,R.drawable.ic_premium_servers,R.drawable.ic_subscriptions_24dp,R.drawable.ic_menu_24dp)
    Surface(Modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp),shape=RoundedCornerShape(26.dp),
        color=MaterialTheme.colorScheme.surface.copy(alpha=.94f),shadowElevation=12.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.widthIn(max=640.dp).fillMaxWidth().heightIn(min=72.dp)) {
            routes.forEachIndexed { i,route ->
                NavigationBarItem(selected==route,{onSelect(route)},icon={
                    if(terminal && i==1) Text(">_",fontFamily=com.v2ray.ang.ui.terminal.TerminalFont,
                        modifier=Modifier.clearAndSetSemantics {})
                    else Icon(androidx.compose.ui.res.painterResource(icons[i]),null,Modifier.size(24.dp))
                },
                    label={Text(stringResource(labels[i]),maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)},colors=NavigationBarItemDefaults.colors(
                        selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,
                        indicatorColor=Color.Transparent),modifier=Modifier.weight(1f))
            }
        }
    }
}
