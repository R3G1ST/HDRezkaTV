package com.hdrezka.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.LibraryStore
import com.hdrezka.tv.data.MovieItem

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(LibraryStore.favorites(context)) }
    val watchLater = remember { LibraryStore.watchLater(context) }
    val initialFocus = remember { FocusRequester() }
    FocusWhenReady(initialFocus, favorites.isNotEmpty() || watchLater.isNotEmpty())

    LaunchedEffect(Unit) {
        runCatching {
            LibraryStore.importFavorites(context, HdRezkaClient.siteFavorites())
            favorites = LibraryStore.favorites(context)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Библиотека",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TvButton(text = "Назад", onClick = onBack, large = true)
        }

        Spacer(Modifier.height(22.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            item {
                SavedRow(
                    title = "ИЗБРАННОЕ",
                    items = favorites.map { it.item },
                    empty = "Пока пусто. Нажмите «В избранное» на карточке тайтла.",
                    firstFocus = if (favorites.isNotEmpty()) initialFocus else null,
                    onOpen = onOpen
                )
            }
            item {
                SavedRow(
                    title = "СМОТРЕТЬ ПОЗЖЕ",
                    items = watchLater.map { it.item },
                    empty = "Пока пусто. Нажмите «Смотреть позже» на карточке тайтла.",
                    firstFocus = if (favorites.isEmpty() && watchLater.isNotEmpty()) initialFocus else null,
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun SavedRow(
    title: String,
    items: List<MovieItem>,
    empty: String,
    firstFocus: FocusRequester? = null,
    onOpen: (MovieItem) -> Unit
) {
    Column {
        Text(
            title,
            color = AccentWarm,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        if (items.isEmpty()) {
            Text(empty, color = TextSecondary, fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    PosterCard(
                        item = item,
                        focusRequester = if (index == 0) firstFocus else null,
                        onOpen = { onOpen(item) }
                    )
                }
            }
        }
    }
}
