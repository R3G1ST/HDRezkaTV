package com.hdrezka.tv.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hdrezka.tv.data.MovieItem
import com.hdrezka.tv.data.SiteContinueItem

object NotificationsStore {
    private const val PREFS = "hdrezka_notifications"
    private const val SEEN = "seen"

    fun seen(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(SEEN, emptySet())
            ?.toSet()
            ?: emptySet()

    fun markSeen(context: Context, keys: Collection<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(SEEN, emptySet()).orEmpty().toMutableSet()
        updated.addAll(keys)
        prefs.edit().putStringSet(SEEN, updated).apply()
    }
}

fun notificationKey(item: SiteContinueItem): String =
    "${item.movieId}:${item.season.orEmpty()}:${item.episode.orEmpty()}"

fun newEpisodeNotifications(items: List<SiteContinueItem>): List<SiteContinueItem> =
    items.filter { it.hasNewEpisode }

@Composable
fun NotificationsScreen(
    notifications: List<SiteContinueItem>,
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val backFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    FocusWhenReady(backFocus, notifications.isEmpty())
    FocusWhenReady(firstRowFocus, notifications.isNotEmpty())
    LaunchedEffect(Unit) {
        NotificationsStore.markSeen(context, notifications.map(::notificationKey))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "Уведомления",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (notifications.isEmpty()) {
                        "Новых серий пока нет"
                    } else {
                        "Новые серии в ваших сериалах"
                    },
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            TvButton(
                text = "Назад",
                onClick = onBack,
                large = true,
                icon = Icons.Filled.ArrowBack,
                focusRequester = backFocus
            )
        }

        Spacer(Modifier.height(18.dp))

        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF3A3A45),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Пока нет новых серий в сериалах из «Досмотреть»",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(notifications, key = { _, it -> notificationKey(it) }) { index, item ->
                    NotificationRow(
                        item = item,
                        focusRequester = if (index == 0) firstRowFocus else null,
                        onOpen = { onOpen(item.toMovieItem()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: SiteContinueItem,
    focusRequester: FocusRequester?,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Color(0xFF202028) else SurfaceDark)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Accent else Hairline,
                shape
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.posterUrl)
                .size(180, 270)
                .crossfade(false)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(56.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceStrong)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append("Новая серия")
                    item.season?.let { append(" · S$it") }
                    item.episode?.let { append(" · E$it") }
                },
                color = AccentWarm,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (item.dateText.isNotBlank()) {
                Text(
                    item.dateText,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Text(
            "Открыть ›",
            color = if (focused) Accent else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun SiteContinueItem.toMovieItem(): MovieItem = MovieItem(
    id = movieId,
    title = title,
    originalTitle = null,
    year = year,
    pageUrl = pageUrl,
    posterUrl = posterUrl
)
