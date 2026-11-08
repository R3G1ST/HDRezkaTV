package com.hdrezka.tv.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hdrezka.tv.data.CatalogSection
import com.hdrezka.tv.data.Episode
import com.hdrezka.tv.data.catalogGenres
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.LibraryStore
import com.hdrezka.tv.data.PersonLink
import com.hdrezka.tv.data.Reminder
import com.hdrezka.tv.data.RemindersStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.hdrezka.tv.data.MovieDetails
import com.hdrezka.tv.data.MovieItem
import com.hdrezka.tv.data.ScheduleItem
import com.hdrezka.tv.data.ParentalStore
import com.hdrezka.tv.data.Parser
import com.hdrezka.tv.data.ProgressEntry
import com.hdrezka.tv.data.ProgressStore
import com.hdrezka.tv.data.ReleaseCheckScheduler
import com.hdrezka.tv.data.ReleaseWatchStore
import com.hdrezka.tv.data.SettingsStore

@Composable
fun DetailsScreen(
    item: MovieItem,
    resume: ProgressEntry? = null,
    autoPlay: Boolean = false,
    onBack: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onOpenItem: (MovieItem) -> Unit = {}
) {
    var details by remember { mutableStateOf<MovieDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var person by remember { mutableStateOf<PersonLink?>(null) }
    val context = LocalContext.current
    var parentalUnlocked by remember(item.pageUrl) { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(item.pageUrl) {
        loading = true
        error = null
        try {
            details = HdRezkaClient.details(item.pageUrl, item.title)
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
    ) {
        when {
            loading -> DetailsSkeleton()

            details == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(56.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Не удалось открыть тайтл",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (error?.contains("404") == true) {
                        "Этот тайтл недоступен на текущем зеркале. " +
                            "Попробуйте сменить зеркало в настройках."
                    } else {
                        error ?: ""
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
                )
                Button(onClick = onBack) {
                    Text("Назад")
                }
            }

            else -> {
                val loaded = details!!
                if (!parentalUnlocked &&
                    ParentalStore.isEnabled(context) &&
                    !loaded.isFamilyFriendly
                ) {
                    ParentalPinDialog(
                        title = "Взрослый контент",
                        subtitle = "Введите PIN, чтобы открыть",
                        onSuccess = { parentalUnlocked = true },
                        onDismiss = onBack
                    )
                } else {
                    val selectedPerson = person
                    if (selectedPerson != null) {
                        PersonScreen(
                            person = selectedPerson,
                            onBack = { person = null },
                            onOpen = onOpenItem
                        )
                    } else {
                        DetailsContent(
                            details = loaded,
                            fallbackPoster = item.posterUrl,
                            resume = resume,
                            autoPlay = autoPlay,
                            upcoming = item.upcoming,
                            onBack = onBack,
                            onPlay = onPlay,
                            onOpenItem = onOpenItem,
                            onPerson = { person = it }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsContent(
    details: MovieDetails,
    fallbackPoster: String?,
    resume: ProgressEntry?,
    autoPlay: Boolean,
    upcoming: Boolean = false,
    onBack: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onOpenItem: (MovieItem) -> Unit = {},
    onPerson: (PersonLink) -> Unit
) {
    val context = LocalContext.current
    // Самая свежая запись: локальная история или запись из «Продолжить».
    // После просмотра локальная запись новее — её и берём, иначе
    // страница возвращала старую позицию и плеер снова стартовал с неё.
    val localProgress = remember(details.id, resume) {
        ProgressStore.list(context)
            .filter {
                it.movieId == details.id &&
                    it.positionMs > 5_000L &&
                    it.progress < 0.95
            }
            .maxByOrNull { it.updatedAt }
    }
    val savedProgress = remember(details.id, resume, localProgress) {
        when {
            localProgress == null -> resume
            resume == null -> localProgress
            localProgress.updatedAt > resume.updatedAt -> localProgress
            else -> resume
        }
    }
    var voiceId by remember(details.id) {
        mutableStateOf(
            savedProgress?.voiceId?.takeIf { id ->
                details.voiceActings.any { it.id == id }
            } ?: details.voiceActings.firstOrNull()?.id ?: "0"
        )
    }
    val initialSeasonId = remember(details.id) {
        savedProgress?.seasonId?.takeIf { id -> details.seasons.any { it.id == id } }
            ?: savedProgress?.seasonNumber?.toIntOrNull()?.let { n ->
                details.seasons.getOrNull(n - 1)?.id
            }
            ?: details.seasons.firstOrNull()?.id
    }
    var seasonId by remember(details.id) { mutableStateOf(initialSeasonId) }
    var episodes by remember(details.id) {
        mutableStateOf(details.episodesFor(initialSeasonId ?: ""))
    }
    var episodeId by remember(details.id) {
        mutableStateOf(
            savedProgress?.episodeId?.takeIf { id -> episodes.any { it.id == id } }
                ?: savedProgress?.episodeNumber?.toIntOrNull()?.let { n ->
                    episodes.getOrNull(n - 1)?.id
                }
                ?: episodes.firstOrNull()?.id
        )
    }
    var startAtMs by remember(details.id) {
        mutableStateOf(savedProgress?.positionMs?.takeIf { it > 5_000L })
    }
    val scope = rememberCoroutineScope()
    val primaryFocus = remember { FocusRequester() }
    var didFocus by remember(details.id) { mutableStateOf(false) }
    var trailerError by remember { mutableStateOf<String?>(null) }
    var favorite by remember(details.id) {
        mutableStateOf(LibraryStore.isFavorite(context, details.id))
    }
    var later by remember(details.id) {
        mutableStateOf(LibraryStore.isWatchLater(context, details.id))
    }
    var similar by remember(details.id) { mutableStateOf<List<MovieItem>>(emptyList()) }
    LaunchedEffect(details.id) {
        val section = if (details.isSeries) CatalogSection.SERIES else CatalogSection.FILMS
        val genreId = details.genres.firstOrNull()?.let { name ->
            catalogGenres.firstOrNull { it.second.equals(name, ignoreCase = true) }?.first
        }
        similar = runCatching {
            HdRezkaClient.catalog(section, 1, genre = genreId)
        }.getOrDefault(emptyList())
            .filter { it.id != details.id }
            .take(12)
    }
    var loadingEpisodes by remember { mutableStateOf(false) }
    var quality by remember(details.id) {
        val saved = SettingsStore.defaultQuality(context)
        mutableStateOf(if (saved == "auto" || saved in details.qualities) saved else "auto")
    }
    var showEpisodePicker by remember(details.id) { mutableStateOf(false) }
    var autoStarted by remember(details.id) { mutableStateOf(false) }
    val progressByEpisode = remember(details.id, episodes) {
        if (!details.isSeries) {
            emptyMap()
        } else {
            ProgressStore.list(context)
                .filter { it.movieId == details.id }
                .associateBy { it.episodeId ?: "" }
        }
    }

    LaunchedEffect(autoPlay, details.id, episodeId, voiceId) {
        if (autoPlay && !autoStarted && (!details.isSeries || episodeId != null)) {
            autoStarted = true
            onPlay(
                PlayRequest(
                    details = details,
                    voiceId = voiceId,
                    seasonId = if (details.isSeries) seasonId else null,
                    episodeId = if (details.isSeries) episodeId else null,
                    quality = quality,
                    episodes = episodes,
                    startAtMs = startAtMs
                )
            )
        }
    }

    LaunchedEffect(seasonId) {
        val id = seasonId ?: return@LaunchedEffect
        var list = details.episodesFor(id)
        if (list.isEmpty()) {
            val season = details.seasons.firstOrNull { it.id == id }
            if (season?.pageUrl != null) {
                loadingEpisodes = true
                list = runCatching {
                    val html = HdRezkaClient.get(season.pageUrl)
                    Parser.parseEpisodes(html, HdRezkaClient.baseUrl, id)
                }.getOrDefault(emptyList())
                loadingEpisodes = false
            }
        }
        episodes = list
        val preferred = resume?.episodeId?.takeIf { id -> list.any { it.id == id } }
            ?: resume?.episodeNumber?.toIntOrNull()?.let { n -> list.getOrNull(n - 1)?.id }
        if (episodeId == null || list.none { it.id == episodeId }) {
            val chosen = preferred ?: list.firstOrNull()?.id
            episodeId = chosen
            startAtMs = chosen?.let { progressByEpisode[it]?.positionMs?.takeIf { p -> p > 5_000L } }
        }
    }

    LaunchedEffect(details.id, autoPlay) {
        if (!autoPlay && !didFocus) {
            repeat(8) {
                withFrameNanos { }
                runCatching { primaryFocus.requestFocus() }
                delay(80)
            }
            didFocus = true
        }
    }

    val playNow: () -> Unit = {
        if (!details.isSeries || episodeId != null) {
            onPlay(
                PlayRequest(
                    details = details,
                    voiceId = voiceId,
                    seasonId = if (details.isSeries) seasonId else null,
                    episodeId = if (details.isSeries) episodeId else null,
                    quality = quality,
                    episodes = episodes,
                    startAtMs = startAtMs
                )
            )
        }
    }
    val chooseSeason: (String) -> Unit = { id ->
        if (id != seasonId) {
            seasonId = id
            startAtMs = null
        }
    }
    val chooseEpisode: (Episode) -> Unit = { episode ->
        episodeId = episode.id
        startAtMs = progressByEpisode[episode.id]?.positionMs?.takeIf { it > 5_000L }
    }
    val poster = details.posterUrl ?: fallbackPoster
    val meta = listOfNotNull(details.year, details.country, details.duration)
        .joinToString(" · ")
    val seasonIndex = details.seasons.indexOfFirst { it.id == seasonId }
    val episodeIndex = episodes.indexOfFirst { it.id == episodeId }
    val voiceName = details.voiceActings.firstOrNull { it.id == voiceId }?.name.orEmpty()

    val minimalBringIntoView = remember {
        object : BringIntoViewSpec {
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

    CompositionLocalProvider(LocalBringIntoViewSpec provides minimalBringIntoView) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item(key = "top") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvButton(text = "‹  Назад", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Text(
                    if (details.isSeries) "СЕРИАЛ" else "ФИЛЬМ",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.weight(1f))
                details.maxQuality?.let { MetaBadge(it, Accent) }
            }
        }

        item(key = "hero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = poster,
                    contentDescription = details.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(178.dp)
                        .height(267.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceStrong)
                )
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    if (!details.originalTitle.isNullOrBlank()) {
                        Text(
                            details.originalTitle!!,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(
                        details.title,
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 31.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (details.genres.isNotEmpty()) {
                        Text(
                            details.genres.take(4).joinToString(" · "),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        details.ratingKp?.let { MetaBadge("КП $it", AccentWarm) }
                        details.ratingImdb?.let { MetaBadge("IMDb $it", Color(0xFFF5C518)) }
                        details.maxQuality?.let { MetaBadge(it, Accent) }
                    }
                    if (details.overview.isNotBlank()) {
                        Text(
                            details.overview,
                            color = Color(0xFFD5D5DB),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvActionButton(
                            text = if (startAtMs != null) "Продолжить" else "Смотреть",
                            icon = Icons.Filled.PlayArrow,
                            style = TvActionStyle.Primary,
                            focusRequester = primaryFocus,
                            onClick = playNow
                        )
                        if (details.isSeries) {
                            TvActionButton(
                                text = "Серия и озвучка",
                                icon = Icons.Filled.List,
                                onClick = { showEpisodePicker = true }
                            )
                        }
                        TvActionButton(
                            text = "Трейлер",
                            icon = Icons.Filled.PlayArrow,
                            onClick = {
                                scope.launch {
                                    runCatching { HdRezkaClient.trailer(details.id) }
                                        .onSuccess { trailer ->
                                            val url = trailer.iframeUrl ?: trailer.pageUrl
                                            if (url != null) {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                )
                                            } else {
                                                trailerError = "Трейлер недоступен"
                                            }
                                        }
                                        .onFailure {
                                            trailerError = it.message ?: "Трейлер недоступен"
                                        }
                                }
                            }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        TvActionButton(
                            text = if (favorite) "В избранном" else "В избранное",
                            icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            style = TvActionStyle.Ghost,
                            compact = true,
                            onClick = {
                                val saved = MovieItem(
                                    id = details.id,
                                    title = details.title,
                                    originalTitle = details.originalTitle,
                                    year = details.year,
                                    pageUrl = details.pageUrl,
                                    posterUrl = details.posterUrl,
                                    upcoming = upcoming
                                )
                                LibraryStore.toggleFavorite(context, saved)
                                val nowFavorite = !favorite
                                favorite = nowFavorite
                                if (upcoming) {
                                    if (nowFavorite) {
                                        ReleaseWatchStore.add(context, saved)
                                        ReleaseCheckScheduler.scheduleNext(context)
                                    } else {
                                        ReleaseWatchStore.remove(context, details.id)
                                    }
                                }
                                scope.launch {
                                    runCatching {
                                        HdRezkaClient.setFavorite(
                                            movieId = details.id,
                                            pageUrl = details.pageUrl,
                                            favorite = favorite
                                        )
                                    }
                                }
                            }
                        )
                        TvActionButton(
                            text = if (later) "В списке" else "Смотреть позже",
                            icon = if (later) Icons.Filled.Check else Icons.Filled.Add,
                            style = TvActionStyle.Ghost,
                            compact = true,
                            onClick = {
                                LibraryStore.toggleWatchLater(
                                    context,
                                    MovieItem(
                                        id = details.id,
                                        title = details.title,
                                        originalTitle = details.originalTitle,
                                        year = details.year,
                                        pageUrl = details.pageUrl,
                                        posterUrl = details.posterUrl
                                    )
                                )
                                later = !later
                            }
                        )
                    }
                    if (upcoming) {
                        Text(
                            "Пришлём уведомление, когда тайтл выйдет",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (details.isSeries) {
                        Text(
                            buildString {
                                if (seasonIndex >= 0) append("Сезон ${seasonIndex + 1}")
                                if (episodeIndex >= 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("Серия ${episodeIndex + 1}")
                                }
                                if (voiceName.isNotBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(voiceName)
                                }
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    if (startAtMs != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(
                                "Продолжить с ${formatProgress(startAtMs!!)}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            TvActionButton(
                                text = "С начала",
                                style = TvActionStyle.Ghost,
                                compact = true,
                                onClick = { startAtMs = null }
                            )
                        }
                    }
                    if (trailerError != null) {
                        Text(
                            trailerError!!,
                            color = AccentWarm,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        if (!details.isSeries && details.voiceActings.size > 1) {
            item(key = "voices") {
                Selector(
                    title = "ОЗВУЧКА",
                    items = details.voiceActings.map {
                        it.id to (if (it.isPremium) "${it.name} 🔒" else it.name)
                    },
                    selected = voiceId,
                    onSelect = { voiceId = it }
                )
            }
        }

        if (details.persons.isNotEmpty()) {
            item(key = "persons") {
                Column {
                    SectionLabel("В РОЛЯХ")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        items(details.persons, key = { it.url }) { person ->
                            TvButton(
                                text = person.name,
                                onClick = { onPerson(person) }
                            )
                        }
                    }
                }
            }
        }

        if (details.overview.isNotBlank()) {
            item(key = "description") {
                Column {
                    SectionLabel("ОПИСАНИЕ")
                    Text(
                        details.overview,
                        color = Color(0xFFD5D5DB),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        if (similar.isNotEmpty()) {
            item(key = "similar") {
                Column {
                    SectionLabel("ПОХОЖЕЕ")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        items(similar, key = { it.id }) { item ->
                            PosterCard(
                                item = item,
                                focusRequester = null,
                                onOpen = { onOpenItem(item) },
                                width = 132.dp,
                                titleLines = 2
                            )
                        }
                    }
                }
            }
        }

        if (details.schedule.isNotEmpty()) {
            item(key = "schedule") {
                Column {
                    SectionLabel("РАСПИСАНИЕ")
                    for (entry in details.schedule.take(12)) {
                        ScheduleRow(
                            movieId = details.id,
                            movieTitle = details.title,
                            pageUrl = details.pageUrl,
                            posterUrl = details.posterUrl,
                            entry = entry
                        )
                    }
                }
            }
        }
    }
    }

    if (showEpisodePicker) {
        EpisodePickerDialog(
            details = details,
            seasonId = seasonId,
            episodeId = episodeId,
            voiceId = voiceId,
            episodes = episodes,
            loading = loadingEpisodes,
            progressByEpisode = progressByEpisode,
            onSeason = { chooseSeason(it) },
            onVoice = { voiceId = it },
            onEpisode = { episode ->
                val start = progressByEpisode[episode.id]
                    ?.positionMs
                    ?.takeIf { it > 5_000L }
                episodeId = episode.id
                startAtMs = start
                showEpisodePicker = false
                onPlay(
                    PlayRequest(
                        details = details,
                        voiceId = voiceId,
                        seasonId = seasonId,
                        episodeId = episode.id,
                        quality = quality,
                        episodes = episodes,
                        startAtMs = start
                    )
                )
            },
            onPlay = {
                showEpisodePicker = false
                playNow()
            },
            onDismiss = { showEpisodePicker = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AccentWarm,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun MetaBadge(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun EpisodePickerDialog(
    details: MovieDetails,
    seasonId: String?,
    episodeId: String?,
    voiceId: String,
    episodes: List<Episode>,
    loading: Boolean,
    progressByEpisode: Map<String, ProgressEntry>,
    onSeason: (String) -> Unit,
    onVoice: (String) -> Unit,
    onEpisode: (Episode) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIndex = episodes.indexOfFirst { it.id == episodeId }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0)
    )
    val selectedFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (selectedIndex >= 0) {
            runCatching { listState.scrollToItem(selectedIndex) }
        }
        var focused = false
        repeat(8) {
            withFrameNanos { }
            if (!focused) {
                focused = runCatching { selectedFocus.requestFocus() }.isSuccess
            }
            if (!focused) delay(40)
        }
        if (!focused) {
            runCatching { playFocus.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xD90B0B0D))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .then(rememberDialogAppear())
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Серия и озвучка",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        details.title,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TvButton(text = "Закрыть", onClick = onDismiss)
                }

                if (details.seasons.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    SectionLabel("СЕЗОН")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        items(details.seasons, key = { it.id }) { season ->
                            TvButton(
                                text = season.title,
                                selected = season.id == seasonId,
                                onClick = { onSeason(season.id) }
                            )
                        }
                    }
                }

                if (details.voiceActings.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("ОЗВУЧКА")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        items(details.voiceActings, key = { it.id }) { voice ->
                            TvButton(
                                text = if (voice.isPremium) "${voice.name} 🔒" else voice.name,
                                selected = voice.id == voiceId,
                                onClick = { onVoice(voice.id) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel("СЕРИИ")

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(18.dp).height(18.dp)
                        )
                        Text(
                            "Загружаем серии…",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                } else if (episodes.isEmpty()) {
                    Text("Серии не найдены", color = TextSecondary, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(episodes, key = { _, it -> it.id }) { index, episode ->
                            EpisodeRow(
                                episode = episode,
                                index = index,
                                selected = episode.id == episodeId,
                                progress = progressByEpisode[episode.id],
                                focusRequester = if (episode.id == episodeId) {
                                    selectedFocus
                                } else null,
                                onClick = { onEpisode(episode) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(
                        text = "▶  Смотреть",
                        primary = true,
                        focusRequester = playFocus,
                        onClick = onPlay
                    )
                    Spacer(Modifier.width(8.dp))
                    TvButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.weight(1f))
                    if (episodeId != null) {
                        val currentIndex = episodes.indexOfFirst { it.id == episodeId }
                        if (currentIndex >= 0) {
                            Text(
                                "Выбрана серия ${currentIndex + 1}",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    index: Int,
    selected: Boolean,
    progress: ProgressEntry?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> Color(0xFF2A2A33)
                    selected -> Accent.copy(alpha = 0.16f)
                    else -> Color(0xFF1A1A20)
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
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index + 1}",
            color = if (selected) Accent else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(34.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                episode.title.ifBlank { "Серия ${index + 1}" },
                color = Color.White,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress != null) {
                Text(
                    if (progress.progress >= 0.95) "просмотрено" else
                        "просмотрено ${(progress.progress * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }
        if (progress != null && progress.progress >= 0.95) {
            Text("✓", color = Accent, fontSize = 14.sp)
        } else if (selected) {
            Text("Выбрана", color = Accent, fontSize = 9.sp)
        }
    }
}

private fun formatProgress(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun episodeLabel(episode: Episode, index: Int): String {
    val title = episode.title.trim()
    return if (title.isBlank() || title == "Серия ${episode.id}") {
        "Серия ${index + 1}"
    } else {
        title
    }
}

@Composable
private fun DetailsSkeleton() {
    val pulse = rememberInfiniteTransition(label = "detailsSkeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "detailsSkeletonAlpha"
    )
    val block = Color(0xFF202028).copy(alpha = alpha)
    Row(
        Modifier
            .fillMaxSize()
            .padding(40.dp)
    ) {
        Box(
            Modifier
                .width(220.dp)
                .height(330.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(block)
        )
        Column(
            Modifier
                .padding(start = 30.dp)
                .weight(1f)
        ) {
            Box(
                Modifier
                    .width(110.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(block)
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(block)
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(block)
            )
            Spacer(Modifier.height(26.dp))
            repeat(3) {
                Box(
                    Modifier
                        .fillMaxWidth(if (it == 2) 0.72f else 0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(block)
                )
                Spacer(Modifier.height(9.dp))
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(140.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(block)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    movieId: String,
    movieTitle: String,
    pageUrl: String,
    posterUrl: String?,
    entry: ScheduleItem
) {
    val context = LocalContext.current
    val id = "$movieId:${entry.label}:${entry.date}"
    var reminded by remember(id) { mutableStateOf(RemindersStore.contains(context, id)) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            entry.label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(160.dp)
        )
        Text(
            entry.date,
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.weight(1f))
        if (entry.isAvailable) {
            Text("вышла", color = Accent, fontSize = 11.sp)
        } else {
            Text("ожидается", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = {
                    val timestamp = RemindersStore.timestamp(entry.date)
                    val reminder = Reminder(
                        id = id,
                        movieId = movieId,
                        title = movieTitle,
                        pageUrl = pageUrl,
                        posterUrl = posterUrl,
                        label = entry.label,
                        dateText = entry.date,
                        timestampMs = timestamp
                    )
                    reminded = RemindersStore.toggle(context, reminder)
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (reminded) Accent else Color.White
                )
            ) {
                Text(if (reminded) "Напомним" else "Напомнить", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun Selector(
    title: String,
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        SectionLabel(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
        ) {
            items(items, key = { it.first }) { (id, label) ->
                TvButton(
                    text = label,
                    selected = id == selected,
                    onClick = { onSelect(id) }
                )
            }
        }
    }
}
