package com.hdrezka.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.MovieItem
import com.hdrezka.tv.data.SearchHistoryStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Состояние поиска живёт выше экрана, чтобы результаты не терялись при открытии тайтла. */
class SearchUiState {
    val queryState = mutableStateOf("")
    val itemsState = mutableStateOf<List<MovieItem>>(emptyList())
    val loadingState = mutableStateOf(false)
    val errorState = mutableStateOf<String?>(null)
    val searchedState = mutableStateOf(false)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val searchFocus = rememberInitialFocus()
    val resultFocus = remember { FocusRequester() }
    val searchButtonFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var history by remember { mutableStateOf(SearchHistoryStore.list(context)) }

    var query by state.queryState
    var items by state.itemsState
    var loading by state.loadingState
    var error by state.errorState
    var searched by state.searchedState
    var fieldFocused by remember { mutableStateOf(false) }
    var focusResults by remember { mutableStateOf(false) }

    FocusWhenReady(resultFocus, focusResults)

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        loading = true
        error = null
        searched = true
        focusResults = false
        scope.launch {
            try {
                items = HdRezkaClient.search(q)
                if (items.isNotEmpty()) {
                    SearchHistoryStore.add(context, q)
                    history = SearchHistoryStore.list(context)
                } else {
                    error = "Ничего не найдено"
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            loading = false
            focusResults = items.isNotEmpty()
        }
    }

    fun clear() {
        query = ""
        items = emptyList()
        error = null
        searched = false
        focusResults = false
        scope.launch {
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
    }

    val smoothBringIntoView = remember {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec = tween<Float>(
                durationMillis = 220,
                easing = FastOutSlowInEasing
            )

            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float = when {
                offset < 0f -> offset
                offset + size > containerSize -> offset + size - containerSize
                else -> 0f
            }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides smoothBringIntoView) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "Поиск",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Фильмы, сериалы и мультфильмы",
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
                icon = Icons.Filled.ArrowBack
            )
        }

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (fieldFocused) Color(0xFF1E1E26) else SurfaceStrong)
                .border(
                    width = if (fieldFocused) 2.dp else 1.dp,
                    color = if (fieldFocused) Accent else Hairline,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = if (fieldFocused) Accent else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Название фильма или сериала",
                        color = TextSecondary,
                        fontSize = 17.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown
                            ) {
                                runCatching { searchButtonFocus.requestFocus() }
                                true
                            } else {
                                false
                            }
                        }
                        .focusRequester(searchFocus)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TvActionButton(
                text = if (loading) "Поиск…" else "Найти",
                icon = Icons.Filled.Search,
                style = TvActionStyle.Primary,
                focusRequester = searchButtonFocus,
                onClick = { search() }
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                TvActionButton(
                    text = "Очистить",
                    icon = Icons.Filled.Clear,
                    style = TvActionStyle.Ghost,
                    onClick = { clear() }
                )
            }
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty() && !loading) {
                Text(
                    "Найдено: ${items.size}",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        if (query.isBlank() && !searched && history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "НЕДАВНИЕ ЗАПРОСЫ",
                color = AccentWarm,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                items(history, key = { it }) { entry ->
                    TvButton(
                        text = entry,
                        onClick = {
                            query = entry
                            search()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Text(
                        "Ищем…",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        error!!,
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                    if (query.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        TvActionButton(
                            text = "Повторить",
                            style = TvActionStyle.Ghost,
                            onClick = { search() }
                        )
                    }
                }
            }

            items.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        PosterCard(
                            item = item,
                            focusRequester = if (index == 0) resultFocus else null,
                            onOpen = { onOpen(item) },
                            width = 132.dp
                        )
                    }
                }
            }

            searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = TextSecondary, fontSize = 15.sp)
            }

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF3A3A45),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Введите название и нажмите «Найти»",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
    }
}
