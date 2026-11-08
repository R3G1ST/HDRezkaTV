package com.hdrezka.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Просит фокус у первого элемента экрана после его появления.
 * Без этого экран открывается «без фокуса»: первый D-pad приходится
 * тратить только на то, чтобы фокус вообще появился.
 */
@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        // Переход между экранами длится ~230 мс: держим фокус, пока
        // старый экран не исчез, иначе он успевает его сбросить.
        repeat(6) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
            delay(80)
        }
    }
    return requester
}

/** Просит фокус, когда данные экрана уже готовы и элемент появился в разметке. */
@Composable
fun FocusWhenReady(requester: FocusRequester, ready: Boolean) {
    LaunchedEffect(requester, ready) {
        if (!ready) return@LaunchedEffect
        // Фокус ставим несколько раз: при смене экрана предыдущий экран
        // уничтожается чуть позже и успевает сбросить фокус.
        repeat(4) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
            delay(150)
        }
    }
}

/** Пружина для «дорогого» отклика на фокус. */
val FocusSpring: SpringSpec<Float> = spring(
    dampingRatio = 0.76f,
    stiffness = 430f
)

/** Мягкое появление диалога: лёгкий масштаб + затухание. */
@Composable
fun rememberDialogAppear(): Modifier {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        appeared = true
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "dialogAlpha"
    )
    return Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    large: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    icon: ImageVector? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (large) 12.dp else 10.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = FocusSpring,
        label = "tvButtonScale"
    )
    val background by animateColorAsState(
        targetValue = when {
            primary -> Color.White
            selected -> Accent
            focused -> Color(0xFF2C2C36)
            else -> SurfaceStrong
        },
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "tvButtonBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused && selected -> Color.White
            focused -> Accent
            selected || primary -> Color.Transparent
            else -> Hairline
        },
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "tvButtonBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (primary || selected) Color(0xFF111111) else Color.White,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "tvButtonContent"
    )
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = if (large) 50.dp else 42.dp)
            .padding(
                horizontal = if (large) 22.dp else 18.dp,
                vertical = if (large) 12.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (large) 20.dp else 17.dp)
            )
        }
        Text(
            text,
            color = contentColor,
            fontSize = if (large) 14.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class TvActionStyle { Primary, Secondary, Ghost }

/** Крупная кнопка действия для страницы фильма и hero-блока. */
@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TvActionStyle = TvActionStyle.Secondary,
    icon: ImageVector? = null,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val height = when {
        style == TvActionStyle.Primary -> 54.dp
        compact -> 42.dp
        else -> 50.dp
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = FocusSpring,
        label = "actionScale"
    )
    val background by animateColorAsState(
        targetValue = when (style) {
            TvActionStyle.Primary -> Color.White
            TvActionStyle.Secondary ->
                if (focused) Color(0xFF2E2E38) else Color(0xFF1D1D24)
            TvActionStyle.Ghost ->
                if (focused) Color(0xFF23232B) else Color.Transparent
        },
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "actionBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> Accent
            style == TvActionStyle.Primary -> Color.Transparent
            style == TvActionStyle.Ghost -> Hairline
            else -> Color(0xFF33333D)
        },
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "actionBorder"
    )
    val contentColor = if (style == TvActionStyle.Primary) Color(0xFF101014) else Color.White
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .height(height)
            .padding(horizontal = if (compact) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (compact) 17.dp else 21.dp)
            )
        }
        Text(
            text,
            color = contentColor,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = if (style == TvActionStyle.Primary) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TvToggleRow(
    title: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val thumbOffset by animateDpAsState(
        targetValue = if (value) 30.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "toggleThumb"
    )
    val rowBackground by animateColorAsState(
        targetValue = if (focused) Color(0xFF202027) else Color.Transparent,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "toggleRowBackground"
    )
    val trackColor by animateColorAsState(
        targetValue = if (value) Accent else Color(0xFF3A3A43),
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "toggleTrack"
    )
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(rowBackground)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Accent else Color.Transparent,
                shape = shape
            )
            .clickable { onChange(!value) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .width(58.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(trackColor)
        ) {
            Box(
                Modifier
                    .padding(start = thumbOffset, top = 4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
