package com.hdrezka.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrezka.tv.R
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.MovieItem
import com.hdrezka.tv.data.StreamingService
import com.hdrezka.tv.data.StreamingServices
import kotlinx.coroutines.launch

enum class StreamingCategory(val title: String) {
    FILMS("Фильмы"),
    SERIES("Сериалы")
}

data class StreamingCacheEntry(
    val items: List<MovieItem>,
    val page: Int,
    val ended: Boolean
)

@Composable
fun StreamingScreen(
    platformId: String,
    categoryName: String,
    cache: MutableMap<String, StreamingCacheEntry>,
    gridState: LazyGridState,
    onPlatformSelected: (String) -> Unit,
    onCategorySelected: (StreamingCategory) -> Unit,
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val firstPlatformFocus = rememberInitialFocus()
    val filmsFocus = remember { FocusRequester() }
    val seriesFocus = remember { FocusRequester() }
    val gridFirstFocus = remember { FocusRequester() }
    val platformListState = rememberLazyListState()

    var services by remember {
        mutableStateOf(StreamingServices.cache ?: StreamingServices.fallback())
    }
    var servicesLoading by remember { mutableStateOf(StreamingServices.cache == null) }

    LaunchedEffect(Unit) {
        if (StreamingServices.cache == null) {
            val loaded = runCatching { StreamingServices.load() }.getOrNull()
            if (!loaded.isNullOrEmpty()) {
                services = loaded
                if (loaded.none { it.id == platformId }) {
                    onPlatformSelected(loaded.first().id)
                }
            }
            servicesLoading = false
        }
    }

    val platform = services.firstOrNull { it.id == platformId }
        ?: services.first()
    val category = runCatching { StreamingCategory.valueOf(categoryName) }
        .getOrDefault(StreamingCategory.FILMS)
    val cacheKey = "${platform.id}:${category.name}"
    val restored = cache[cacheKey]

    var items by remember(cacheKey) {
        mutableStateOf(restored?.items ?: emptyList())
    }
    var loading by remember(cacheKey) { mutableStateOf(false) }
    var page by remember(cacheKey) { mutableIntStateOf(restored?.page ?: 1) }
    var ended by remember(cacheKey) { mutableStateOf(restored?.ended ?: false) }
    var error by remember(cacheKey) { mutableStateOf<String?>(null) }
    var loadToken by remember(cacheKey) { mutableIntStateOf(0) }

    LaunchedEffect(platform.id, services.size) {
        val index = services.indexOfFirst { it.id == platform.id }
        if (index >= 0) platformListState.scrollToItem(index)
    }

    fun categoryOf(item: MovieItem): StreamingCategory? {
        val kind = item.kind?.lowercase().orEmpty()
        return when {
            kind.contains("сериал") -> StreamingCategory.SERIES
            kind.contains("фильм") || kind.contains("мульт") ||
                kind.contains("аниме") -> StreamingCategory.FILMS
            item.pageUrl.contains("/series/") -> StreamingCategory.SERIES
            item.pageUrl.contains("/films/") -> StreamingCategory.FILMS
            else -> null
        }
    }

    fun localizeItem(item: MovieItem, savedBase: String): MovieItem {
        val uri = runCatching { java.net.URI(item.pageUrl) }.getOrNull() ?: return item
        val path = uri.rawPath ?: return item
        val mapped = path
            .replaceFirst("/serialy/", "/series/")
            .replaceFirst("/filmy/", "/films/")
        return item.copy(
            pageUrl = savedBase.trimEnd('/') + mapped
        )
    }

    fun yearValue(item: MovieItem): Int =
        item.year?.filter { it.isDigit() }?.take(4)?.toIntOrNull() ?: 0

    fun newestFirst(list: List<MovieItem>): List<MovieItem> = list.sortedWith(
        compareByDescending<MovieItem> { yearValue(it) }
            .thenByDescending { it.id.toLongOrNull() ?: 0L }
    )

    suspend fun load(
        target: StreamingService,
        targetCategory: StreamingCategory,
        reset: Boolean
    ) {
        if (!reset && loading) return
        val token = ++loadToken
        loading = true
        if (reset) {
            items = emptyList()
            page = 1
            ended = false
            error = null
        }
        val key = cacheKey
        val savedBase = HdRezkaClient.baseUrl
        // Набираем несколько рядов сразу; для редких категорий
        // (например, фильмы HBO) проходим больше страниц подборки.
        val targetCount = if (reset) 40 else items.size + 24
        val maxPages = if (reset) 24 else 8
        try {
            HdRezkaClient.setBaseUrl("https://hdrezka.inc")
            var next = if (reset) 1 else page
            var fetched = 0
            while (!ended && fetched < maxPages) {
                if (token != loadToken) return
                val batch = HdRezkaClient.collectionItems(target.url, next)
                    .map { localizeItem(it, savedBase) }
                if (token != loadToken) return
                if (batch.isEmpty()) {
                    ended = true
                    break
                }
                val filtered = batch.filter { categoryOf(it) == targetCategory }
                items = newestFirst((items + filtered).distinctBy { it.id })
                next += 1
                page = next
                fetched += 1
                cache[key] = StreamingCacheEntry(items, page, ended)
                if (items.size >= targetCount) break
            }
        } catch (e: Exception) {
            if (token == loadToken) {
                error = e.message ?: e.javaClass.simpleName
            }
        } finally {
            HdRezkaClient.setBaseUrl(savedBase)
            if (token == loadToken) loading = false
        }
    }

    LaunchedEffect(cacheKey) {
        if (items.isNotEmpty() || ended) return@LaunchedEffect
        load(platform, category, reset = true)
    }

    // Догрузка следующих страниц при прокрутке. Запускаем её в scope,
    // а не внутри LaunchedEffect: иначе смена loading/items.size
    // перезапускает эффект и отменяет сам запрос.
    LaunchedEffect(cacheKey) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to items.size
        }.collect { (last, size) ->
            if (!loading && !ended && size > 0 && last >= size - 8) {
                scope.launch { load(platform, category, reset = false) }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(horizontal = 40.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "Стриминги",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Фильмы и сериалы онлайн-кинотеатров",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamingCategory.entries.forEach { entry ->
                    TvButton(
                        text = entry.title,
                        selected = category == entry,
                        large = false,
                        focusRequester = if (entry == StreamingCategory.FILMS) {
                            filmsFocus
                        } else {
                            seriesFocus
                        },
                        modifier = Modifier.focusProperties {
                            down = firstPlatformFocus
                        },
                        onClick = { onCategorySelected(entry) }
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            TvButton(
                text = "Назад",
                onClick = onBack,
                large = true,
                icon = Icons.Filled.ArrowBack
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(
            state = platformListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(services, key = { it.id }) { entry ->
                StreamingPlatformCard(
                    service = entry,
                    selected = platform.id == entry.id,
                    focusRequester = if (entry.id == platform.id) {
                        firstPlatformFocus
                    } else {
                        null
                    },
                    downFocus = gridFirstFocus,
                    onClick = { onPlatformSelected(entry.id) }
                )
            }
            if (servicesLoading) {
                item {
                    Box(
                        Modifier
                            .width(52.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            loading && items.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent)
            }

            error != null && items.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Не удалось загрузить подборку",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        error!!,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    TvActionButton(
                        text = "Повторить",
                        icon = Icons.Filled.Refresh,
                        onClick = {
                            scope.launch { load(platform, category, reset = true) }
                        }
                    )
                }
            }

            items.isEmpty() && !loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "В этой категории пока пусто",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(8),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    PosterCard(
                        item = item,
                        focusRequester = if (index == 0) gridFirstFocus else null,
                        onOpen = { onOpen(item) },
                        width = 99.dp,
                        titleLines = 2
                    )
                }
                if (loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Accent,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingPlatformCard(
    service: StreamingService,
    selected: Boolean,
    focusRequester: FocusRequester?,
    downFocus: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = if (selected) Color(0xFF25252E) else Color(0xFF17171D),
        animationSpec = tween(150),
        label = "streamingCardBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> Accent
            selected -> Color.White.copy(alpha = 0.88f)
            else -> Color.White.copy(alpha = 0.10f)
        },
        animationSpec = tween(150),
        label = "streamingCardBorder"
    )
    val logo = streamingLogo(service)
    Box(
        modifier = Modifier
            .width(164.dp)
            .height(50.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .then(
                if (downFocus != null) Modifier.focusProperties { down = downFocus }
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .border(
                width = if (selected || focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = service.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            )
        } else {
            Text(
                service.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun streamingLogo(service: StreamingService): Int? = when (service.id) {
    "netflix" -> R.drawable.streaming_netflix
    "hbo" -> R.drawable.streaming_hbo
    "amazon" -> R.drawable.streaming_amazon
    "disney" -> R.drawable.streaming_disneyplus
    "appletv" -> R.drawable.streaming_appletv
    "hulu" -> R.drawable.streaming_hulu
    "peacock" -> R.drawable.streaming_peacock
    else -> null
}
