package com.hdrezka.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.hdrezka.tv.data.Collection
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.MovieItem
import kotlinx.coroutines.launch

@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    var selected by remember { mutableStateOf<Collection?>(null) }
    var items by remember { mutableStateOf<List<Collection>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val initialFocus = remember { FocusRequester() }
    FocusWhenReady(initialFocus, !loading && items.isNotEmpty())

    LaunchedEffect(Unit) {
        if (items.isEmpty()) {
            val loaded = runCatching { HdRezkaClient.collections(1) }
                .getOrDefault(emptyList())
            items = loaded
            loading = false
            ended = loaded.isEmpty()
        }
    }

    BackHandler(onBack = {
        if (selected != null) selected = null else onBack()
    })

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
    ) {
        val current = selected
        if (current == null) {
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
                HeaderRow("Подборки", onBack = onBack)

                Spacer(Modifier.height(18.dp))

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(300.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(items, key = { _, it -> it.id }) { index, collection ->
                            CollectionCard(
                                collection = collection,
                                focusRequester = if (index == 0) initialFocus else null
                            ) {
                                selected = collection
                            }
                        }
                        if (!ended) {
                            item(key = "more") {
                                MoreCard(
                                    loading = loadingMore,
                                    onClick = {
                                        if (!loadingMore) {
                                            scope.launch {
                                                loadingMore = true
                                                val next = page + 1
                                                val loaded = runCatching {
                                                    HdRezkaClient.collections(next)
                                                }.getOrDefault(emptyList())
                                                if (loaded.isEmpty()) {
                                                    ended = true
                                                } else {
                                                    items = items + loaded
                                                    page = next
                                                }
                                                loadingMore = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            CollectionDetail(
                collection = current,
                onBack = { selected = null },
                onOpen = onOpen
            )
        }
    }
}

@Composable
private fun CollectionDetail(
    collection: Collection,
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    var items by remember(collection.id) { mutableStateOf<List<MovieItem>>(emptyList()) }
    var page by remember(collection.id) { mutableIntStateOf(1) }
    var loading by remember(collection.id) { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val initialFocus = remember { FocusRequester() }
    FocusWhenReady(initialFocus, !loading)

    LaunchedEffect(collection.id) {
        loading = true
        items = runCatching { HdRezkaClient.collectionItems(collection.url, 1) }
            .getOrDefault(emptyList())
        loading = false
    }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
        HeaderRow(collection.title, onBack = onBack)

        Spacer(Modifier.height(18.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    PosterCard(
                        item = item,
                        focusRequester = if (index == 0) initialFocus else null,
                        onOpen = { onOpen(item) }
                    )
                }
                if (!ended) {
                    item(key = "more") {
                        MoreCard(
                            loading = loadingMore,
                            focusRequester = if (items.isEmpty()) initialFocus else null,
                            onClick = {
                                if (!loadingMore) {
                                    scope.launch {
                                        loadingMore = true
                                        val next = page + 1
                                        val loaded = runCatching {
                                            HdRezkaClient.collectionItems(collection.url, next)
                                        }.getOrDefault(emptyList())
                                        if (loaded.isEmpty()) {
                                            ended = true
                                        } else {
                                            items = items + loaded
                                            page = next
                                        }
                                        loadingMore = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        TvButton(text = "Назад", onClick = onBack, large = true)
    }
}

@Composable
private fun CollectionCard(
    collection: Collection,
    focusRequester: FocusRequester? = null,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "collectionScale"
    )

    Column(
        Modifier
            .width(300.dp)
            .zIndex(if (focused) 1f else 0f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Accent else Hairline,
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onOpen)
        ) {
            AsyncImage(
                model = collection.coverUrl,
                contentDescription = collection.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (collection.count > 0) {
                Text(
                    "${collection.count}",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            collection.title,
            color = if (focused) Accent else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
    }
}

@Composable
private fun MoreCard(
    loading: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(150.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Accent else Hairline,
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(color = Accent)
            } else {
                Text("＋\nЕщё", color = if (focused) Accent else Color.White, fontSize = 14.sp)
            }
        }
    }
}
