@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hdrezka.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.hdrezka.tv.data.CatalogSection
import com.hdrezka.tv.data.CatalogSort
import com.hdrezka.tv.data.catalogGenres
import com.hdrezka.tv.data.catalogYears
import com.hdrezka.tv.data.AccountStore
import com.hdrezka.tv.data.GithubProgressSync
import com.hdrezka.tv.data.GithubSyncConfigStore
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.LibraryStore
import com.hdrezka.tv.data.MovieDetails
import com.hdrezka.tv.data.MovieItem
import com.hdrezka.tv.data.ParentalStore
import com.hdrezka.tv.data.PendingPlaybackStore
import com.hdrezka.tv.data.ProgressEntry
import com.hdrezka.tv.data.ProgressStore
import com.hdrezka.tv.data.ReleaseCheckScheduler
import com.hdrezka.tv.data.ReleaseWatch
import com.hdrezka.tv.data.ReleaseWatchStore
import com.hdrezka.tv.data.SiteContinueItem
import com.hdrezka.tv.data.StreamingServices

private data class ContinueUiItem(
    val key: String,
    val title: String,
    val pageUrl: String,
    val movieId: String,
    val posterUrl: String?,
    val progress: ProgressEntry?,
    val site: SiteContinueItem?
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<MovieItem?>(null) }
    var play by remember { mutableStateOf<PlayRequest?>(null) }
    var account by remember { mutableStateOf(AccountStore.load(context)) }
    var showLogin by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    var showStreaming by remember { mutableStateOf(false) }
    var streamingPlatformId by remember {
        mutableStateOf(StreamingServices.fallback().first().id)
    }
    var streamingCategoryName by remember {
        mutableStateOf(StreamingCategory.FILMS.name)
    }
    val streamingCache = remember {
        mutableMapOf<String, StreamingCacheEntry>()
    }
    val streamingGridState = rememberLazyGridState()
    val streamingScope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showParentalGate by remember { mutableStateOf(false) }
    var pendingSettings by remember { mutableStateOf(false) }
    var showUpcoming by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    val searchState = remember { SearchUiState() }
    var resume by remember { mutableStateOf<ProgressEntry?>(null) }
    var autoPlay by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    var homeFocusedOnce by remember { mutableStateOf(false) }
    var restoreKey by remember { mutableStateOf<String?>(null) }
    var restoreHero by remember { mutableStateOf(false) }
    var restoreNav by remember { mutableStateOf<String?>(null) }
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    var homeContinue by remember { mutableStateOf<List<ProgressEntry>>(emptyList()) }
    var homeSpecialRows by remember { mutableStateOf<List<HomeSpecialRow>>(emptyList()) }
    var homeSiteContinue by remember { mutableStateOf<List<SiteContinueItem>>(emptyList()) }
    var notificationsSeen by remember { mutableStateOf(NotificationsStore.seen(context)) }
    val unseenNotifications = remember(homeSiteContinue, notificationsSeen) {
        newEpisodeNotifications(homeSiteContinue).count {
            notificationKey(it) !in notificationsSeen
        }
    }
    var homeRefresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected, play, homeRefresh) {
        if (selected == null && play == null) {
            homeContinue = withContext(Dispatchers.IO) {
                ProgressStore.list(context).filter {
                    it.positionMs > 10_000 && it.progress < 0.95
                }
            }
            if (GithubSyncConfigStore.load(context)?.isReady == true) {
                runCatching { GithubProgressSync.sync(context) }
                homeContinue = withContext(Dispatchers.IO) {
                    ProgressStore.list(context).filter {
                        it.positionMs > 10_000 && it.progress < 0.95
                    }
                }
            }
        }
    }

    LaunchedEffect(selected, play, homeRefresh, homeContinue) {
        if (selected != null || play != null) return@LaunchedEffect
        val favorites = withContext(Dispatchers.IO) {
            LibraryStore.favorites(context).map { it.item }.take(14)
        }
        val top = runCatching { HdRezkaClient.top100(10) }.getOrDefault(emptyList())
        val last = homeContinue.maxByOrNull { it.updatedAt }
        val section = if (last?.pageUrl?.contains("/series/") == true) {
            CatalogSection.SERIES
        } else {
            CatalogSection.FILMS
        }
        val recommended = runCatching {
            HdRezkaClient.catalog(section, 1, sort = CatalogSort.POPULAR)
        }.getOrDefault(emptyList())
            .filter { it.id != last?.movieId }
            .take(14)
        homeSpecialRows = buildList {
            if (favorites.isNotEmpty()) {
                add(HomeSpecialRow("my_list", "МОЙ СПИСОК", favorites))
            }
            if (top.isNotEmpty()) {
                add(HomeSpecialRow("top10", "ТОП-10", top.take(10)))
            }
            if (recommended.isNotEmpty()) {
                add(HomeSpecialRow("recommended", "РЕКОМЕНДУЕМ", recommended))
            }
        }
    }

    // Опрос GitHub, пока открыта главная: свежие позиции с Mac
    // появляются на TV в течение нескольких секунд.
    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000)
            if (selected == null && play == null &&
                !showLogin && !showSearch && !showLibrary &&
                !showCollections && !showSettings
            ) {
                val changed = GithubProgressSync.pull(context)
                    .getOrNull()
                    ?.changed == true
                if (changed) {
                    homeContinue = withContext(Dispatchers.IO) {
                        ProgressStore.list(context).filter {
                            it.positionMs > 10_000 && it.progress < 0.95
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(account?.id) {
        if (account != null) {
            homeSiteContinue = runCatching { HdRezkaClient.siteContinue() }
                .getOrDefault(emptyList())
        }
    }

    // При запуске проверяем, не вышли ли уже тайтлы, добавленные
    // в избранное из «В ожидании».
    LaunchedEffect(Unit) {
        if (ReleaseWatchStore.list(context).isNotEmpty()) {
            ReleaseWatch.checkAndNotify(context)
            if (ReleaseWatchStore.list(context).isNotEmpty()) {
                ReleaseCheckScheduler.scheduleNext(context)
            }
        }
    }

    // Если приложение было выгружено во время просмотра в Vimu,
    // сохраняем хотя бы оценку позиции — иначе время фильма терялось.
    LaunchedEffect(Unit) {
        delay(2_500)
        if (play != null) return@LaunchedEffect
        val pending = PendingPlaybackStore.load(context) ?: return@LaunchedEffect
        val age = System.currentTimeMillis() - pending.launchedAt
        if (age in 0..12L * 60L * 60L * 1000L) {
            val position = pending.estimatedPosition()
            if (position > 5_000L) {
                ProgressStore.save(
                    context = context,
                    key = pending.key,
                    movieId = pending.movieId,
                    title = pending.title,
                    posterUrl = pending.posterUrl,
                    pageUrl = pending.pageUrl,
                    voiceId = pending.voiceId,
                    seasonId = pending.seasonId,
                    episodeId = pending.episodeId,
                    positionMs = position,
                    durationMs = pending.durationMs
                )
                if (GithubSyncConfigStore.load(context)?.isReady == true) {
                    runCatching { GithubProgressSync.sync(context) }
                }
            }
        }
        PendingPlaybackStore.clear(context)
    }

    LaunchedEffect(Unit) {
        val fresh = HdRezkaClient.currentAccount()
        if (fresh != null) {
            account = fresh
            AccountStore.save(context, fresh)
        }
    }

    val screen: ScreenState = when {
        play != null -> ScreenState.Player(play!!)
        selected != null -> ScreenState.Details(selected!!, resume, autoPlay)
        showLogin -> ScreenState.Login
        showSearch -> ScreenState.Search
        showLibrary -> ScreenState.Library
        showCollections -> ScreenState.Collections
        showStreaming -> ScreenState.Streaming
        showUpcoming -> ScreenState.Upcoming
        showNotifications -> ScreenState.Notifications
        showSettings -> ScreenState.Settings
        else -> ScreenState.Home
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(230, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.992f,
                    animationSpec = tween(230, easing = FastOutSlowInEasing)
                ))
                .togetherWith(fadeOut(tween(150, easing = FastOutSlowInEasing)))
                .using(SizeTransform(clip = false))
        },
        label = "screens"
    ) { target ->
        when (target) {
            is ScreenState.Player -> PlayerScreen(
                request = target.request,
                onBack = { play = null },
                onLogin = {
                    play = null
                    selected = null
                    showLogin = true
                }
            )

            is ScreenState.Details -> DetailsScreen(
                item = target.item,
                resume = target.resume,
                autoPlay = target.autoPlay,
                onBack = {
                    selected = null
                    resume = null
                },
                onPlay = {
                    autoPlay = false
                    play = it
                },
                onOpenItem = {
                    selected = it
                    resume = null
                }
            )

            ScreenState.Login -> LoginScreen(
                onBack = { showLogin = false },
                onLoggedIn = {
                    account = it
                    showLogin = false
                }
            )

            ScreenState.Search -> SearchScreen(
                state = searchState,
                onBack = { showSearch = false },
                onOpen = {
                    selected = it
                    resume = null
                }
            )

            ScreenState.Library -> LibraryScreen(
                onBack = { showLibrary = false },
                onOpen = {
                    selected = it
                    resume = null
                }
            )

            ScreenState.Collections -> CollectionsScreen(
                onBack = { showCollections = false },
                onOpen = {
                    selected = it
                    resume = null
                }
            )

            ScreenState.Streaming -> StreamingScreen(
                platformId = streamingPlatformId,
                categoryName = streamingCategoryName,
                cache = streamingCache,
                gridState = streamingGridState,
                onPlatformSelected = {
                    streamingPlatformId = it
                    streamingScope.launch { streamingGridState.scrollToItem(0) }
                },
                onCategorySelected = {
                    streamingCategoryName = it.name
                    streamingScope.launch { streamingGridState.scrollToItem(0) }
                },
                onBack = { showStreaming = false },
                onOpen = {
                    selected = it
                    resume = null
                }
            )

            ScreenState.Upcoming -> UpcomingScreen(
                onBack = { showUpcoming = false },
                onOpen = {
                    selected = it
                    resume = null
                }
            )

            ScreenState.Notifications -> NotificationsScreen(
                notifications = newEpisodeNotifications(homeSiteContinue),
                onBack = { showNotifications = false },
                onOpen = {
                    selected = it
                    resume = null
                }
            )

            ScreenState.Settings -> SettingsScreen(
                onBack = {
                    showSettings = false
                    homeRefresh++
                },
                onLoggedOut = {
                    account = null
                    showSettings = false
                    homeRefresh++
                },
                onLogin = {
                    showSettings = false
                    showLogin = true
                }
            )

            ScreenState.Home -> {
                val model: CatalogViewModel = viewModel()
                HomeScreen(
                    model = model,
                    listState = homeListState,
                    initialFocusDone = homeFocusedOnce,
                    onInitialFocusDone = { homeFocusedOnce = true },
                    restoreKey = restoreKey,
                    onRestored = { restoreKey = null },
                    restoreHero = restoreHero,
                    onHeroRestored = { restoreHero = false },
                    restoreNav = restoreNav,
                    onNavRestored = { restoreNav = null },
                    rowStates = rowStates,
                    continueEntries = homeContinue,
                    siteContinue = homeSiteContinue,
                    specialRows = homeSpecialRows,
                    onOpen = {
                        restoreKey = "item:${it.id}"
                        restoreHero = false
                        autoPlay = false
                        selected = it
                        resume = null
                    },
                    onOpenSpecial = { row, item ->
                        restoreKey = "special:${row.key}:${item.id}"
                        restoreHero = false
                        autoPlay = false
                        selected = item
                        resume = null
                    },
                    onOpenHero = {
                        restoreKey = null
                        restoreHero = true
                        autoPlay = false
                        selected = it
                        resume = null
                    },
                    onContinue = { entry ->
                        restoreKey = "local:${entry.key}"
                        autoPlay = true
                        resume = entry
                        selected = MovieItem(
                            id = entry.movieId,
                            title = entry.title,
                            originalTitle = null,
                            year = null,
                            pageUrl = entry.pageUrl,
                            posterUrl = entry.posterUrl
                        )
                    },
                    onContinueSite = { entry ->
                        restoreKey = "site:${entry.id}"
                        autoPlay = false
                        resume = ProgressEntry(
                            key = "site:${entry.id}",
                            movieId = entry.movieId,
                            title = entry.title,
                            posterUrl = entry.posterUrl,
                            pageUrl = entry.pageUrl,
                            voiceId = "",
                            seasonId = null,
                            episodeId = null,
                            positionMs = 0,
                            durationMs = 0,
                            updatedAt = System.currentTimeMillis(),
                            seasonNumber = entry.season,
                            episodeNumber = entry.episode
                        )
                        selected = MovieItem(
                            id = entry.movieId,
                            title = entry.title,
                            originalTitle = null,
                            year = null,
                            pageUrl = entry.pageUrl,
                            posterUrl = entry.posterUrl
                        )
                    },
                    onSearch = {
                        restoreNav = "Поиск"
                        showSearch = true
                    },
                    onLibrary = {
                        restoreNav = "Библиотека"
                        showLibrary = true
                    },
                    onCollections = {
                        restoreNav = "Подборки"
                        showCollections = true
                    },
                    onStreaming = {
                        restoreNav = "Стриминги"
                        showStreaming = true
                    },
                    onUpcoming = {
                        restoreNav = "В ожидании"
                        showUpcoming = true
                    },
                    onNotifications = {
                        restoreNav = "Уведомления"
                        val keys = newEpisodeNotifications(homeSiteContinue)
                            .map(::notificationKey)
                            .toSet()
                        NotificationsStore.markSeen(context, keys)
                        notificationsSeen = notificationsSeen + keys
                        showNotifications = true
                    },
                    notificationsDot = unseenNotifications > 0,
                    onSettings = {
                        if (ParentalStore.isEnabled(context)) {
                            pendingSettings = true
                            showParentalGate = true
                        } else {
                            restoreNav = "Настройки"
                            showSettings = true
                        }
                    }
                )
            }
        }
        if (showParentalGate) {
            ParentalPinDialog(
                title = "Родительский контроль",
                subtitle = "Введите PIN, чтобы открыть настройки",
                onSuccess = {
                    showParentalGate = false
                    if (pendingSettings) {
                        restoreNav = "Настройки"
                        showSettings = true
                    }
                    pendingSettings = false
                },
                onDismiss = {
                    showParentalGate = false
                    pendingSettings = false
                }
            )
        }
    }
}

data class HomeSpecialRow(
    val key: String,
    val title: String,
    val items: List<MovieItem>
)

private sealed interface ScreenState {
    data object Home : ScreenState
    data class Details(
        val item: MovieItem,
        val resume: ProgressEntry?,
        val autoPlay: Boolean
    ) : ScreenState
    data class Player(val request: PlayRequest) : ScreenState
    data object Login : ScreenState
    data object Search : ScreenState
    data object Library : ScreenState
    data object Collections : ScreenState
    data object Streaming : ScreenState
    data object Upcoming : ScreenState
    data object Notifications : ScreenState
    data object Settings : ScreenState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    model: CatalogViewModel,
    listState: LazyListState,
    initialFocusDone: Boolean,
    onInitialFocusDone: () -> Unit,
    restoreKey: String?,
    onRestored: () -> Unit,
    restoreHero: Boolean,
    onHeroRestored: () -> Unit,
    restoreNav: String?,
    onNavRestored: () -> Unit,
    rowStates: MutableMap<String, LazyListState>,
    continueEntries: List<ProgressEntry>,
    siteContinue: List<SiteContinueItem>,
    specialRows: List<HomeSpecialRow> = emptyList(),
    onOpen: (MovieItem) -> Unit,
    onOpenSpecial: (HomeSpecialRow, MovieItem) -> Unit = { _, item -> onOpen(item) },
    onOpenHero: (MovieItem) -> Unit,
    onContinue: (ProgressEntry) -> Unit,
    onContinueSite: (SiteContinueItem) -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onCollections: () -> Unit,
    onStreaming: () -> Unit,
    onUpcoming: () -> Unit,
    onNotifications: () -> Unit,
    notificationsDot: Boolean,
    onSettings: () -> Unit
) {
    val state = model.state
    // После смены зеркала возвращаемся на главную с прошлой ошибкой —
    // пробуем загрузить каталог заново автоматически.
    LaunchedEffect(Unit) {
        if (!state.loading && (state.rows.isEmpty() || state.error != null)) {
            model.refresh()
        }
    }
    val firstFocus = remember { FocusRequester() }
    val navFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val filterSummary = if (
        model.genre != null || model.year != null || model.sort != CatalogSort.FRESH
    ) {
        buildString {
            append(
                model.genre?.let { id ->
                    catalogGenres.firstOrNull { it.first == id }?.second ?: id
                } ?: "Все"
            )
            append(" · ")
            append(model.year ?: "все годы")
            append(" · ")
            append(model.sort.title)
        }
    } else null
    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var focusedRowItem by remember { mutableIntStateOf(-1) }
    var heroFocused by remember { mutableStateOf(false) }
    val scrollTop: () -> Unit = {
        scope.launch { listState.animateScrollToItem(0) }
    }
    // BringIntoView от фокуса hero срабатывает уже после нашего скролла и
    // оставляет карточку срезанной. Пока фокус в hero — несколько раз
    // доводим список к самому верху, как только фокус уходит — цикл отменяется.
    LaunchedEffect(heroFocused) {
        if (!heroFocused) return@LaunchedEffect
        repeat(12) {
            listState.scrollToItem(0)
            delay(50)
        }
    }
    // Возврат из «Продолжить»: ряд находится ниже «Новинок», поэтому
    // сначала прокручиваем к нему, иначе карточка не создана и фокус
    // не восстанавливается, а restoreKey навсегда блокирует прокрутку.
    LaunchedEffect(restoreKey, continueEntries.size, siteContinue.size) {
        if (restoreKey == null) return@LaunchedEffect
        if (restoreKey.startsWith("local:") || restoreKey.startsWith("site:")) {
            if (continueEntries.isNotEmpty() || siteContinue.isNotEmpty()) {
                runCatching { listState.scrollToItem(1) }
            }
        }
        delay(6_000)
        onRestored()
    }

    LaunchedEffect(restoreHero) {
        if (!restoreHero) return@LaunchedEffect
        delay(450)
        runCatching { listState.scrollToItem(0) }
        for (i in 0 until 12) {
            withFrameNanos { }
            if (runCatching { firstFocus.requestFocus() }.isSuccess) break
            delay(40)
        }
        onHeroRestored()
    }
    val goHome: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    // derivedStateOf: без него HomeScreen пересобирается на каждый пиксель
    // прокрутки — отсюда были рывки.
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        }
    }
    BackHandler(enabled = scrolled) {
        scope.launch {
            runCatching { navFocus.requestFocus() }
            listState.animateScrollToItem(0)
        }
    }

    val rowAlpha = remember { Animatable(1f) }
    val rowShift = remember { Animatable(0f) }
    var rowAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(focusedRowItem) {
        if (focusedRowItem < 0) return@LaunchedEffect
        if (focusedRowItem == 0) {
            runCatching { listState.scrollToItem(0) }
            return@LaunchedEffect
        }
        runCatching { listState.scrollToItem(focusedRowItem, 0) }
        // Перестановка мгновенная, а «дорогое» ощущение даёт короткое
        // затухание/подъём — оно рисуется на GPU без пересборки списка.
        rowAnimating = true
        rowAlpha.snapTo(0.45f)
        rowShift.snapTo(with(density) { 20.dp.toPx() }.toFloat())
        rowAlpha.animateTo(1f, tween(durationMillis = 150, easing = FastOutSlowInEasing))
        rowShift.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
        rowAnimating = false
    }

    val horizontalBringIntoView = remember {
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
    val verticalBringIntoView = remember {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec = tween<Float>(
                durationMillis = 160,
                easing = FastOutSlowInEasing
            )
            // Даём фокусу прокрутить список к следующему ряду. Раньше здесь
            // возвращался 0, и после «Новинок» фокус не мог уйти вниз.
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

    CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoView) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Header(
            navFocus = navFocus,
            filterSummary = filterSummary,
            onNavFocus = scrollTop,
            restoreNav = restoreNav,
            onNavRestored = onNavRestored,
            onHome = goHome,
            onSearch = onSearch,
            onLibrary = onLibrary,
            onCollections = onCollections,
            onStreaming = onStreaming,
            onUpcoming = onUpcoming,
            onNotifications = onNotifications,
            notificationsDot = notificationsDot,
            onFilters = { showFilters = true },
            onSettings = onSettings
        )

        Spacer(Modifier.height(8.dp))

        when {
            state.loading && state.rows.isEmpty() -> Loading()
            state.rows.isEmpty() -> ErrorState(
                message = state.error ?: "Не удалось загрузить каталог",
                onRetry = { model.refresh() }
            )
            else -> CompositionLocalProvider(
                LocalBringIntoViewSpec provides verticalBringIntoView
            ) {
            CatalogContent(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (rowAnimating) {
                            Modifier.graphicsLayer {
                                alpha = rowAlpha.value
                                translationY = rowShift.value
                            }
                        } else {
                            Modifier
                        }
                    ),
                horizontalBringIntoView = horizontalBringIntoView,
                model = model,
                listState = listState,
                restoreKey = restoreKey,
                onRestored = onRestored,
                rowStates = rowStates,
                rows = state.rows.filter { it.section != CatalogSection.FRESH },
                specialRows = specialRows,
                newItems = state.rows.firstOrNull { it.section == CatalogSection.FRESH }
                    ?.items.orEmpty(),
                error = state.error,
                continueEntries = continueEntries,
                siteContinue = siteContinue,
                firstFocus = firstFocus,
                requestInitialFocus = !initialFocusDone,
                onInitialFocus = onInitialFocusDone,
                onOpen = onOpen,
                onOpenSpecial = onOpenSpecial,
                onOpenHero = onOpenHero,
                onContinue = onContinue,
                onContinueSite = onContinueSite,
                onRowFocus = { focusedRowItem = it },
                onHeroFocus = { focused ->
                    heroFocused = focused
                    if (focused) scrollTop()
                },
                onLoadMore = { model.loadMore(it) }
            )
            }
        }
    }

    if (showFilters) {
        FiltersDialog(model = model, onClose = { showFilters = false })
    }
    }
}

@Composable
private fun Header(
    navFocus: FocusRequester,
    filterSummary: String?,
    onNavFocus: () -> Unit,
    restoreNav: String?,
    onNavRestored: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onCollections: () -> Unit,
    onStreaming: () -> Unit,
    onUpcoming: () -> Unit,
    onNotifications: () -> Unit,
    notificationsDot: Boolean,
    onFilters: () -> Unit,
    onSettings: () -> Unit
) {
    val searchFocus = remember { FocusRequester() }
    val collectionsFocus = remember { FocusRequester() }
    val libraryFocus = remember { FocusRequester() }
    val streamingFocus = remember { FocusRequester() }
    val upcomingFocus = remember { FocusRequester() }
    val bellFocus = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val navRequesters = remember(navFocus) {
        mapOf(
            "Главная" to navFocus,
            "Поиск" to searchFocus,
            "Подборки" to collectionsFocus,
            "Библиотека" to libraryFocus,
            "Стриминги" to streamingFocus,
            "В ожидании" to upcomingFocus,
            "Уведомления" to bellFocus,
            "Фильтры" to filtersFocus,
            "Настройки" to settingsFocus
        )
    }
    LaunchedEffect(restoreNav) {
        if (restoreNav != null) {
            // Экран перехода ещё живёт ~200 мс и может сбросить фокус.
            repeat(6) {
                withFrameNanos { }
                runCatching { navRequesters[restoreNav]?.requestFocus() }
                delay(60)
            }
            onNavRestored()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "HDREZKA",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            " TV",
            color = Accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.width(18.dp))
        NavItem("Главная", onClick = onHome, active = true, focusRequester = navFocus, onFocus = onNavFocus)
        NavItem("Поиск", onSearch, focusRequester = searchFocus, onFocus = onNavFocus)
        NavItem("Подборки", onCollections, focusRequester = collectionsFocus, onFocus = onNavFocus)
        NavItem("Библиотека", onLibrary, focusRequester = libraryFocus, onFocus = onNavFocus)
        NavItem(
            "Стриминги",
            onStreaming,
            focusRequester = streamingFocus,
            onFocus = onNavFocus
        )
        NavItem(
            "В ожидании",
            onUpcoming,
            focusRequester = upcomingFocus,
            onFocus = onNavFocus
        )

        Spacer(Modifier.weight(1f))

        if (filterSummary != null) {
            Text(
                filterSummary,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        NavBellItem(
            hasDot = notificationsDot,
            onClick = onNotifications,
            focusRequester = bellFocus,
            onFocus = onNavFocus
        )
        NavItem("Фильтры", onFilters, focusRequester = filtersFocus, onFocus = onNavFocus)
        NavItem("Настройки", onSettings, focusRequester = settingsFocus, onFocus = onNavFocus)
    }
}

@Composable
private fun NavBellItem(
    hasDot: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "bell")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bellGlow"
    )
    Box(
        Modifier
            .size(42.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .border(
                if (focused) 1.dp else 0.dp,
                if (focused) Accent else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = "Уведомления",
            tint = if (focused) Accent else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        if (hasDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-9).dp, y = 9.dp)
                    .size(8.dp)
                    .alpha(glow)
                    .background(Accent, CircleShape)
            )
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    onClick: () -> Unit,
    active: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = FocusSpring,
        label = "navScale"
    )
    val background by animateColorAsState(
        targetValue = if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "navBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Accent else Color.Transparent,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "navBorder"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            focused -> Accent
            active -> Color.White
            else -> TextSecondary
        },
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "navText"
    )
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = textColor,
            fontSize = 12.5.sp,
            fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Medium
        )
        if (active) {
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .background(Accent, CircleShape)
            )
        }
    }
}

@Composable
private fun Loading() {
    val pulse = rememberInfiniteTransition(label = "homeSkeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homeSkeletonAlpha"
    )
    val block = Color(0xFF202028).copy(alpha = alpha)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(236.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(block)
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(7) {
                Box(
                    Modifier
                        .width(104.dp)
                        .height(156.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(block)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .width(150.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(block)
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) {
                Box(
                    Modifier
                        .width(132.dp)
                        .height(198.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(block)
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Каталог не загрузился",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                message,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Повторить", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogContent(
    modifier: Modifier = Modifier,
    horizontalBringIntoView: BringIntoViewSpec,
    model: CatalogViewModel,
    listState: LazyListState,
    restoreKey: String?,
    onRestored: () -> Unit,
    rowStates: MutableMap<String, LazyListState>,
    rows: List<CatalogRow>,
    specialRows: List<HomeSpecialRow> = emptyList(),
    newItems: List<MovieItem>,
    error: String?,
    continueEntries: List<ProgressEntry>,
    siteContinue: List<SiteContinueItem>,
    firstFocus: FocusRequester,
    requestInitialFocus: Boolean,
    onInitialFocus: () -> Unit,
    onOpen: (MovieItem) -> Unit,
    onOpenSpecial: (HomeSpecialRow, MovieItem) -> Unit = { _, item -> onOpen(item) },
    onOpenHero: (MovieItem) -> Unit,
    onContinue: (ProgressEntry) -> Unit,
    onContinueSite: (SiteContinueItem) -> Unit,
    onRowFocus: (Int) -> Unit,
    onHeroFocus: (Boolean) -> Unit,
    onLoadMore: (CatalogSection) -> Unit
) {
    val continueState = remember("continue") {
        rowStates.getOrPut("continue") { LazyListState() }
    }
    val mergedContinue = remember(continueEntries, siteContinue) {
        val locals = continueEntries
            .sortedByDescending { it.updatedAt }
            .distinctBy { it.movieId }
        val localIds = locals.mapTo(HashSet()) { it.movieId }
        val result = ArrayList<ContinueUiItem>()
        for (entry in locals) {
            result.add(
                ContinueUiItem(
                    key = "local:${entry.key}",
                    title = entry.title,
                    pageUrl = entry.pageUrl,
                    movieId = entry.movieId,
                    posterUrl = entry.posterUrl,
                    progress = entry,
                    site = siteContinue.firstOrNull { it.movieId == entry.movieId }
                )
            )
        }
        val seenSites = HashSet<String>()
        for (site in siteContinue) {
            if (site.movieId !in localIds && seenSites.add(site.movieId)) {
                result.add(
                    ContinueUiItem(
                        key = "site:${site.id}",
                        title = site.title,
                        pageUrl = site.pageUrl,
                        movieId = site.movieId,
                        posterUrl = site.posterUrl,
                        progress = null,
                        site = site
                    )
                )
            }
        }
        result
    }
    val continueItemIndex = if (mergedContinue.isNotEmpty()) 1 else -1
    val specialBase = 1 + if (mergedContinue.isNotEmpty()) 1 else 0
    val catalogBase = specialBase + specialRows.size
    val firstContentIndex = if (specialRows.isNotEmpty()) specialBase else catalogBase
    val navScope = rememberCoroutineScope()
    val rowRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun rowRequester(index: Int): FocusRequester =
        rowRequesters.getOrPut(index) { FocusRequester() }
    val lastRowIndex = catalogBase + rows.lastIndex
    fun focusRow(index: Int) {
        if (index < 0 || index > lastRowIndex) return
        onRowFocus(index)
        navScope.launch {
            repeat(20) {
                withFrameNanos { }
                if (runCatching { rowRequester(index).requestFocus() }.isSuccess) {
                    return@launch
                }
                delay(20)
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero", contentType = "hero") {
            var heroIndex by remember { mutableIntStateOf(0) }
            var lastInteraction by remember { mutableLongStateOf(0L) }
            val safeIndex = heroIndex.coerceIn(
                0,
                (newItems.size - 1).coerceAtLeast(0)
            )
            val heroItem = newItems.getOrNull(safeIndex)
                ?: rows.firstOrNull()?.items?.firstOrNull()
            val selectHero: (Int) -> Unit = { index ->
                lastInteraction = System.currentTimeMillis()
                heroIndex = index.coerceIn(
                    0,
                    (newItems.size - 1).coerceAtLeast(0)
                )
            }
            val bar = remember { Animatable(0f) }
            // Новинки сами сменяются по кругу; полоса заполняется 7 секунд,
            // показывая, сколько осталось до следующей.
            LaunchedEffect(safeIndex, newItems.size, lastInteraction) {
                if (newItems.size <= 1) return@LaunchedEffect
                bar.snapTo(0f)
                bar.animateTo(
                    1f,
                    tween(durationMillis = 7_000, easing = LinearEasing)
                )
                if (System.currentTimeMillis() - lastInteraction >= 7_000) {
                    heroIndex = (safeIndex + 1) % newItems.size
                }
            }
            Column {
                HeroBillboard(
                    item = heroItem,
                    focusRequester = firstFocus,
                    requestInitialFocus = requestInitialFocus,
                    onInitialFocus = onInitialFocus,
                    onFocused = onHeroFocus,
                    onOpen = onOpenHero
                )
                if (newItems.size > 1) {
                    HeroNewRow(
                        items = newItems,
                        selectedIndex = safeIndex,
                        onSelect = selectHero,
                        onLoadMore = { onLoadMore(CatalogSection.FRESH) },
                        horizontalBringIntoView = horizontalBringIntoView,
                        progress = { bar.value },
                        rowFocusRequester = rowRequester(0),
                        onDown = {
                            focusRow(
                                if (mergedContinue.isNotEmpty()) 1 else firstContentIndex
                            )
                        },
                        onOpen = onOpenHero
                    )
                }
            }
        }

        if (mergedContinue.isNotEmpty()) {
            item(key = "continue", contentType = "continue") {
                Column {
                    Text(
                        "ПРОДОЛЖИТЬ",
                        color = AccentWarm,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                    )
                    CompositionLocalProvider(
                        LocalBringIntoViewSpec provides horizontalBringIntoView
                    ) {
                    LazyRow(
                        state = continueState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        itemsIndexed(mergedContinue, key = { _, it -> it.key }) { i, entry ->
                            ContinueCard(
                                item = entry,
                                clampLeft = i == 0,
                                clampRight = i == mergedContinue.lastIndex,
                                focusKey = entry.key,
                                restoreKey = restoreKey,
                                onRestored = onRestored,
                                onFocus = { onRowFocus(continueItemIndex) },
                                rowFocusRequester = if (i == 0) rowRequester(1) else null,
                                onDown = { focusRow(firstContentIndex) },
                                onUp = { focusRow(0) },
                                onOpen = {
                                    val progress = entry.progress
                                    val site = entry.site
                                    if (progress != null) {
                                        onContinue(progress)
                                    } else if (site != null) {
                                        onContinueSite(site)
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            }
        }

        itemsIndexed(specialRows, key = { _, row -> row.key }) { index, row ->
            val rowIndex = specialBase + index
            val rowState = remember(row.key) {
                rowStates.getOrPut("special:${row.key}") { LazyListState() }
            }
            Column {
                Text(
                    row.title,
                    color = AccentWarm,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides horizontalBringIntoView
                ) {
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        itemsIndexed(row.items, key = { _, item -> "${row.key}:${item.id}" }) { i, item ->
                            LandscapeCard(
                                item = item,
                                clampLeft = i == 0,
                                clampRight = i == row.items.lastIndex,
                                focusKey = "special:${row.key}:${item.id}",
                                restoreKey = restoreKey,
                                onRestored = onRestored,
                                onFocus = { onRowFocus(rowIndex) },
                                rowFocusRequester = if (i == 0) rowRequester(rowIndex) else null,
                                onDown = {
                                    focusRow(
                                        if (index + 1 < specialRows.size) rowIndex + 1
                                        else catalogBase
                                    )
                                },
                                onUp = {
                                    focusRow(
                                        if (index == 0) {
                                            if (mergedContinue.isNotEmpty()) 1 else 0
                                        } else {
                                            rowIndex - 1
                                        }
                                    )
                                },
                                onOpen = { onOpenSpecial(row, item) }
                            )
                        }
                    }
                }
            }
        }

        itemsIndexed(rows, key = { _, row -> row.section.name }) { index, row ->
            val rowState = remember(row.section.name) {
                rowStates.getOrPut(row.section.name) { LazyListState() }
            }
            Column {
                Text(
                    row.title.uppercase(),
                    color = AccentWarm,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides horizontalBringIntoView
                ) {
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(row.items, key = { _, it -> it.id }) { i, item ->
                        val rowIndex = catalogBase + index
                        LandscapeCard(
                            item = item,
                            clampLeft = i == 0,
                            clampRight = i == row.items.lastIndex && row.ended,
                            focusKey = "item:${item.id}",
                            restoreKey = restoreKey,
                            onRestored = onRestored,
                            onFocus = {
                                onRowFocus(rowIndex)
                                // Бесконечная полка: подгружаем следующую
                                // страницу сайта, как только дошли до конца.
                                if (!row.ended && !row.loadingMore &&
                                    i >= row.items.lastIndex - 3
                                ) {
                                    onLoadMore(row.section)
                                }
                            },
                            rowFocusRequester = if (i == 0) rowRequester(rowIndex) else null,
                            onDown = { focusRow(rowIndex + 1) },
                            onUp = {
                                focusRow(
                                    if (index == 0) {
                                        if (mergedContinue.isNotEmpty()) 1 else 0
                                    } else {
                                        rowIndex - 1
                                    }
                                )
                            },
                            onOpen = { onOpen(item) }
                        )
                    }
                    if (!row.ended) {
                        item(key = "more-${row.section.name}") {
                            ShowMoreCard(
                                loading = row.loadingMore,
                                clampRight = true,
                                onFocus = { onRowFocus(catalogBase + index) },
                                onDown = { focusRow(catalogBase + index + 1) },
                                onUp = {
                                    focusRow(
                                        if (index == 0) {
                                            if (mergedContinue.isNotEmpty()) 1 else 0
                                        } else {
                                            catalogBase + index - 1
                                        }
                                    )
                                },
                                onOpen = { onLoadMore(row.section) }
                            )
                        }
                    }
                }
                }
            }
        }

        if (error != null) {
            item(key = "error") {
                Text(
                    "Часть разделов не загрузилась: $error",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun Modifier.rowNavigation(
    onDown: (() -> Unit)?,
    onUp: (() -> Unit)?
): Modifier = onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionDown -> {
                onDown?.invoke()
                onDown != null
            }
            Key.DirectionUp -> {
                onUp?.invoke()
                onUp != null
            }
            else -> false
        }
    } else {
        false
    }
}

@Composable
private fun ContinueCard(
    item: ContinueUiItem,
    clampLeft: Boolean = false,
    clampRight: Boolean = false,
    focusKey: String,
    restoreKey: String?,
    onRestored: () -> Unit,
    onFocus: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
    onDown: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val internalFocus = remember { FocusRequester() }
    val cardFocus = rowFocusRequester ?: internalFocus
    LaunchedEffect(restoreKey) {
        if (!restoreKey.isNullOrBlank() && restoreKey == focusKey) {
            // Даём экрану деталей забрать композицию: без задержки
            // restoreKey очищался ещё при открытии фильма.
            delay(450)
            repeat(12) {
                withFrameNanos { }
                if (runCatching { cardFocus.requestFocus() }.isSuccess) {
                    onRestored()
                    return@LaunchedEffect
                }
                delay(25)
            }
            onRestored()
        }
    }
    val progress = item.progress
    val site = item.site
    val episodeLabel = buildString {
        site?.season?.let { append("S$it") }
        site?.episode?.let { if (isNotEmpty()) append(" · "); append("E$it") }
    }
    val info = buildString {
        site?.year?.let { append(it) }
        if (site != null && episodeLabel.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(episodeLabel)
        }
        site?.translator?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
        if (isEmpty()) {
            if (progress != null) append("продолжить")
            else append(site?.dateText.orEmpty())
        }
    }

    Column(
        modifier = Modifier
            .width(132.dp)
            .focusRequester(cardFocus)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusProperties {
                if (clampLeft) left = FocusRequester.Cancel
                if (clampRight) right = FocusRequester.Cancel
            }
            .rowNavigation(onDown, onUp)
            .clickable(onClick = onOpen)
            .then(if (focused) Modifier.zIndex(1f) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Hairline,
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            AsyncImage(
                model = posterModel(item.posterUrl),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (site?.hasNewEpisode == true) {
                Text(
                    "новые серии",
                    color = Color.White,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(AccentWarm, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            if (progress != null) {
                Text(
                    "${remainingMinutes(progress)} мин",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.progress.toFloat())
                            .height(4.dp)
                            .background(Accent)
                    )
                }
            } else if (episodeLabel.isNotEmpty()) {
                Text(
                    episodeLabel,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            info.ifBlank { "продолжить" },
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun remainingMinutes(entry: ProgressEntry): Int {
    val left = (entry.durationMs - entry.positionMs).coerceAtLeast(0)
    return (left / 60_000L).toInt().coerceAtLeast(1)
}

@Composable
private fun heroModel(url: String?): Any? {
    val context = LocalContext.current
    return remember(url) {
        url?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(520, 780)
                .crossfade(false)
                .build()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroNewRow(
    items: List<MovieItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onLoadMore: () -> Unit,
    horizontalBringIntoView: BringIntoViewSpec,
    progress: () -> Float,
    rowFocusRequester: FocusRequester? = null,
    onDown: (() -> Unit)? = null,
    onOpen: (MovieItem) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, items.size) {
        if (selectedIndex in items.indices) {
            val first = listState.firstVisibleItemIndex
            val last = first + 4
            if (selectedIndex < first || selectedIndex > last) {
                runCatching {
                    listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
                }
            }
            if (selectedIndex >= items.lastIndex - 3) onLoadMore()
        }
    }
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Новинки",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .width(260.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2C2C34))
                    .drawBehind {
                        drawRect(
                            color = Accent,
                            size = Size(
                                size.width * progress().coerceIn(0f, 1f),
                                size.height
                            )
                        )
                    }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${selectedIndex + 1} / ${items.size}",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides horizontalBringIntoView
        ) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    HeroStripCard(
                        item = item,
                        selected = index == selectedIndex,
                        focusRequester = if (index == 0) rowFocusRequester else null,
                        onDown = onDown,
                        onFocus = { onSelect(index) },
                        onOpen = { onOpen(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStripCard(
    item: MovieItem,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onDown: (() -> Unit)? = null,
    onFocus: () -> Unit,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val internalFocus = remember { FocusRequester() }
    val cardFocus = focusRequester ?: internalFocus
    Column(Modifier.width(104.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusRequester(cardFocus)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .rowNavigation(onDown, null)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .border(
                    width = if (selected) 3.dp else if (focused) 2.dp else 1.dp,
                    color = if (selected) Accent
                    else if (focused) Color.White
                    else Hairline,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onOpen)
        ) {
            AsyncImage(
                model = posterModel(item.posterUrl),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            item.kind?.takeIf { it.isNotBlank() }?.let { kind ->
                Text(
                    kind,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .background(
                            Color.Black.copy(alpha = 0.68f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroBillboard(
    item: MovieItem?,
    focusRequester: FocusRequester,
    requestInitialFocus: Boolean,
    onInitialFocus: () -> Unit,
    onFocused: (Boolean) -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    if (item == null) return
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(item.id, requestInitialFocus) {
        if (!requestInitialFocus) return@LaunchedEffect
        repeat(12) {
            delay(100)
            runCatching { focusRequester.requestFocus() }
        }
        onInitialFocus()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(20.dp))
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .clickable { onOpen(item) }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Accent else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Crossfade(
            targetState = item,
            animationSpec = tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing
            ),
            label = "heroContent"
        ) { current ->
            HeroContent(current)
        }
    }
}

@Composable
private fun HeroContent(item: MovieItem) {
    var details by remember(item.id) { mutableStateOf<MovieDetails?>(null) }
    LaunchedEffect(item.id) {
        details = runCatching { HdRezkaClient.details(item.pageUrl, item.title) }.getOrNull()
    }
    Box(Modifier.fillMaxSize()) {
        // Затемнённый постер как фон.
        AsyncImage(
            model = heroModel(details?.posterUrl ?: item.posterUrl),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color(0xF0101014),
                            0.50f to Color(0xC0101014),
                            1f to Color(0x50101014)
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color(0xE0101014)
                        )
                    )
                }
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(113.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.18f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                AsyncImage(
                    model = posterModel(item.posterUrl),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                item.kind?.takeIf { it.isNotBlank() }?.let { kind ->
                    Text(
                        kind,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.width(22.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    (item.kind ?: "Фильм").uppercase(),
                    color = AccentWarm,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 38.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    item.year?.let { append(it); append(" · ") }
                    details?.genres?.take(3)?.joinToString(" · ")?.let { append(it) }
                    details?.maxQuality?.let { append(" · "); append(it) }
                }
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (!details?.overview.isNullOrBlank()) {
                    Text(
                        details!!.overview,
                        color = Color(0xFFD5D5DB),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroActionButton(
    text: String,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    trapLeft: Boolean = false,
    trapRight: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val trapModifier = if (trapLeft || trapRight) {
        Modifier.focusProperties {
            if (trapLeft) left = FocusRequester.Cancel
            if (trapRight) right = FocusRequester.Cancel
        }
    } else {
        Modifier
    }
    TvActionButton(
        text = text,
        onClick = onClick,
        modifier = trapModifier,
        style = if (primary) TvActionStyle.Primary else TvActionStyle.Secondary,
        icon = if (primary) Icons.Filled.PlayArrow else Icons.Filled.Info,
        focusRequester = focusRequester,
        onFocusChanged = onFocusChanged
    )
}

@Composable
private fun LandscapeCard(
    item: MovieItem,
    clampLeft: Boolean = false,
    clampRight: Boolean = false,
    focusKey: String,
    restoreKey: String?,
    onRestored: () -> Unit,
    onFocus: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
    onDown: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val internalFocus = remember { FocusRequester() }
    val cardFocus = rowFocusRequester ?: internalFocus
    LaunchedEffect(restoreKey) {
        if (!restoreKey.isNullOrBlank() && restoreKey == focusKey) {
            // Даём экрану деталей забрать композицию: без задержки
            // restoreKey очищался ещё при открытии фильма.
            delay(450)
            repeat(12) {
                withFrameNanos { }
                if (runCatching { cardFocus.requestFocus() }.isSuccess) {
                    onRestored()
                    return@LaunchedEffect
                }
                delay(25)
            }
            onRestored()
        }
    }
    Column(
        Modifier
            .width(132.dp)
            .then(if (focused) Modifier.zIndex(2f) else Modifier)
    ) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusRequester(cardFocus)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .focusProperties {
                    if (clampLeft) left = FocusRequester.Cancel
                    if (clampRight) right = FocusRequester.Cancel
                }
                .rowNavigation(onDown, onUp)
                .clip(shape)
                .background(SurfaceDark)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Hairline,
                    shape = shape
                )
                .clickable(onClick = onOpen)
        ) {
            AsyncImage(
                model = posterModel(item.posterUrl),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (focused) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Accent)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            color = if (focused) Color.White else Color(0xFFDADAE0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.subtitle.ifBlank { item.kind ?: "" },
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun FiltersDialog(model: CatalogViewModel, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val genres = remember {
            listOf<Pair<String?, String>>(null to "Все") +
                catalogGenres.map { it.first to it.second }
        }
        val years = remember { listOf<String?>(null) + catalogYears }
        val firstChipFocus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { firstChipFocus.requestFocus() }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFA0B0B0D))
                .padding(horizontal = 32.dp, vertical = 22.dp)
        ) {
            Column(Modifier.fillMaxSize().then(rememberDialogAppear())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Фильтры",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.weight(1f))
                    TvActionButton(
                        text = "Готово",
                        onClick = onClose,
                        style = TvActionStyle.Primary,
                        icon = Icons.Filled.Check
                    )
                }

                Spacer(Modifier.height(18.dp))

                FilterLabel("Жанр")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(genres, key = { _, it -> it.first ?: "all" }) { index, (id, title) ->
                        ChoiceChip(
                            label = title,
                            selected = model.genre == id,
                            onClick = { model.applyFilters(id, model.year, model.sort) },
                            focusRequester = if (index == 0) firstChipFocus else null
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                FilterLabel("Год")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(years, key = { it ?: "all" }) { year ->
                        ChoiceChip(
                            label = year ?: "Все",
                            selected = model.year == year,
                            onClick = { model.applyFilters(model.genre, year, model.sort) }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                FilterLabel("Сортировка")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CatalogSort.entries, key = { it.name }) { sort ->
                        ChoiceChip(
                            label = sort.title,
                            selected = model.sort == sort,
                            onClick = { model.applyFilters(model.genre, model.year, sort) }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvActionButton(
                        text = "Сбросить",
                        onClick = { model.resetFilters() },
                        icon = Icons.Filled.Refresh
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Изменения применяются сразу",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = AccentWarm,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    selected -> Accent
                    focused -> Color(0xFF2A2A33)
                    else -> Color(0xFF17171C)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White
                    selected -> Accent
                    else -> Hairline
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF111111) else Color.White,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShowMoreCard(
    loading: Boolean,
    clampRight: Boolean = false,
    onFocus: () -> Unit,
    focusRequester: FocusRequester? = null,
    onDown: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val internalFocus = remember { FocusRequester() }
    val cardFocus = focusRequester ?: internalFocus
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = FocusSpring,
        label = "moreScale"
    )
    val animatedScale = scale
    Column(
        modifier = Modifier
            .width(132.dp)
            .then(if (focused) Modifier.zIndex(1f) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .focusRequester(cardFocus)
                .focusProperties {
                    if (clampRight) right = FocusRequester.Cancel
                }
                .rowNavigation(onDown, onUp)
                .then(
                    if (focused) Modifier.graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    } else Modifier
                )
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Hairline,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(color = Accent)
            } else {
                Text("＋\nЕщё", color = Color.White, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("Ещё", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun posterModel(url: String?): Any? {
    val context = LocalContext.current
    return remember(url) {
        url?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(320, 480)
                .crossfade(false)
                .build()
        }
    }
}

@Composable
fun PosterCard(
    item: MovieItem,
    focusRequester: FocusRequester?,
    onOpen: () -> Unit,
    width: Dp = 156.dp,
    titleLines: Int = 2
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(width)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onOpen)
            .then(if (focused) Modifier.zIndex(1f) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) Accent else Hairline,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            AsyncImage(
                model = posterModel(item.posterUrl),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            item.year?.let { year ->
                Text(
                    year,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.12f))
                )
                Text(
                    "▶",
                    color = Color.White,
                    fontSize = 30.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            color = if (focused) Accent else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            minLines = titleLines,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
