package com.hdrezka.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrezka.tv.data.CatalogSection
import com.hdrezka.tv.data.CatalogSort
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.MovieItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Раздел сайта «В ожидании»: фильмы и сериалы, которые скоро появятся. */
@Composable
fun UpcomingScreen(
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    var items by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Фокус на «Назад» — он есть всегда; вниз уходим в сетку.
    FocusWhenReady(backFocus, true)

    suspend fun load(): List<MovieItem> = withContext(Dispatchers.IO) {
            val sections = listOf(
                CatalogSection.FILMS,
                CatalogSection.SERIES,
                CatalogSection.CARTOONS,
                CatalogSection.ANIME
            )
            sections.flatMap { section ->
                runCatching {
                    HdRezkaClient.catalog(section, 1, sort = CatalogSort.SOON)
                }.getOrDefault(emptyList())
            }.distinctBy { it.id }
                .sortedWith(
                    compareByDescending<MovieItem> {
                        it.year?.filter { ch -> ch.isDigit() }?.take(4)?.toIntOrNull() ?: 0
                    }.thenByDescending { it.id.toLongOrNull() ?: 0L }
                )
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            items = load()
            if (items.isEmpty()) error = "Сайт не вернул ожидаемые тайтлы"
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
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
                    "В ожидании",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Скоро появятся на сайте",
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

        Spacer(Modifier.height(20.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = TextSecondary, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    TvActionButton(
                        text = "Повторить",
                        icon = Icons.Filled.Refresh,
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                items = runCatching { load() }.getOrDefault(emptyList())
                                loading = false
                            }
                        }
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    PosterCard(
                        item = item,
                        focusRequester = if (index == 0) firstFocus else null,
                        onOpen = { onOpen(item.copy(upcoming = true)) },
                        width = 132.dp
                    )
                }
            }
        }
    }
}
