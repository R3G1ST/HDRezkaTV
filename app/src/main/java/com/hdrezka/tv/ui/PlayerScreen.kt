package com.hdrezka.tv.ui

import android.net.Uri
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hdrezka.tv.data.Episode
import com.hdrezka.tv.data.GithubProgressSync
import com.hdrezka.tv.data.GithubSyncConfigStore
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.MovieDetails
import com.hdrezka.tv.data.PendingPlayback
import com.hdrezka.tv.data.PendingPlaybackStore
import com.hdrezka.tv.data.Parser
import com.hdrezka.tv.data.ProgressStore
import com.hdrezka.tv.data.SettingsStore
import com.hdrezka.tv.data.StreamInfo
import com.hdrezka.tv.data.StreamResolver
import com.hdrezka.tv.data.VimuPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.max
import kotlin.math.min

data class PlayRequest(
    val details: MovieDetails,
    val voiceId: String,
    val seasonId: String?,
    val episodeId: String?,
    val quality: String = "1080p",
    val episodes: List<Episode> = emptyList(),
    val startAtMs: Long? = null
) {
    val title: String get() = details.title

    val progressKey: String
        get() = ProgressStore.key(details.id, seasonId, episodeId)
}

@Composable
fun PlayerScreen(
    request: PlayRequest,
    onBack: () -> Unit,
    onLogin: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var stream by remember { mutableStateOf<StreamInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var activeRequest by remember { mutableStateOf(request) }
    var forceInternal by remember { mutableStateOf(false) }
    var loadingSeason by remember { mutableStateOf(false) }
    var seasonError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val useVimu = SettingsStore.useVimu(context) &&
        VimuPlayer.isInstalled(context) &&
        !forceInternal
    val startAtMs = remember(activeRequest.progressKey, activeRequest.startAtMs) {
        activeRequest.startAtMs ?: ProgressStore.load(context, activeRequest.progressKey)
    }

    LaunchedEffect(activeRequest, attempt) {
        loading = true
        error = null
        try {
            stream = StreamResolver.resolve(
                details = activeRequest.details,
                voiceId = activeRequest.voiceId,
                seasonId = activeRequest.seasonId,
                episodeId = activeRequest.episodeId,
                preferredQuality = activeRequest.quality
            )
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val currentStream = stream
        if (currentStream != null && useVimu) {
            VimuPlayback(
                stream = currentStream,
                request = activeRequest,
                startAtMs = startAtMs,
                onBack = onBack,
                onFallback = { forceInternal = true }
            )
        } else if (currentStream != null) {
            VideoPlayer(
                stream = currentStream,
                request = activeRequest,
                startAtMs = startAtMs,
                onBack = onBack,
                onSwitch = { direction ->
                    val index = activeRequest.episodes.indexOfFirst {
                        it.id == activeRequest.episodeId
                    }
                    val target = activeRequest.episodes.getOrNull(
                        if (direction == "next") index + 1 else index - 1
                    ) ?: return@VideoPlayer
                    activeRequest = activeRequest.copy(
                        episodeId = target.id,
                        startAtMs = null
                    )
                },
                loadingSeason = loadingSeason,
                seasonError = seasonError,
                onSelectQuality = { quality, position ->
                    activeRequest = activeRequest.copy(
                        quality = quality,
                        startAtMs = position
                    )
                },
                onSelectVoice = { voice, position ->
                    activeRequest = activeRequest.copy(
                        voiceId = voice,
                        startAtMs = position
                    )
                },
                onSelectSeason = { id ->
                    val known = activeRequest.details.episodesFor(id)
                    if (known.isNotEmpty()) {
                        activeRequest = activeRequest.copy(
                            seasonId = id,
                            episodeId = known.first().id,
                            episodes = known,
                            startAtMs = null
                        )
                    } else {
                        val season = activeRequest.details.seasons.firstOrNull { it.id == id }
                        if (season?.pageUrl == null) {
                            seasonError = "Не удалось загрузить серии"
                        } else {
                            scope.launch {
                                loadingSeason = true
                                seasonError = null
                                val list = runCatching {
                                    Parser.parseEpisodes(
                                        HdRezkaClient.get(season.pageUrl),
                                        HdRezkaClient.baseUrl,
                                        id
                                    )
                                }.getOrDefault(emptyList())
                                loadingSeason = false
                                if (list.isNotEmpty()) {
                                    activeRequest = activeRequest.copy(
                                        seasonId = id,
                                        episodeId = list.first().id,
                                        episodes = list,
                                        startAtMs = null
                                    )
                                } else {
                                    seasonError = "Не удалось загрузить серии"
                                }
                            }
                        }
                    }
                },
                onSelectEpisode = { episode ->
                    activeRequest = activeRequest.copy(
                        episodeId = episode,
                        startAtMs = null
                    )
                },
                onError = { message -> error = message }
            )
        }

        if (loading) {
            CircularProgressIndicator(
                color = Accent,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (error != null && !loading) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Не удалось запустить видео",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                val message = error.orEmpty()
                val sessionExpired = message.contains("405") ||
                    message.contains("сесси", ignoreCase = true) ||
                    message.contains("session", ignoreCase = true)
                Text(
                    if (sessionExpired) {
                        "Сессия HDrezka истекла. Войдите заново, чтобы продолжить просмотр."
                    } else {
                        message
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
                )
                if (sessionExpired) {
                    Button(
                        onClick = onLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("Войти заново", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { attempt++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Повторить", color = Color.White)
                }
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceStrong),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Назад", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VimuPlayback(
    stream: StreamInfo,
    request: PlayRequest,
    startAtMs: Long,
    onBack: () -> Unit,
    onFallback: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var launched by remember { mutableStateOf(false) }
    var resultReceived by remember { mutableStateOf(false) }
    var pausedForPlayback by remember { mutableStateOf(false) }
    var launchStartedAt by remember { mutableStateOf(0L) }

    val basePending = remember(request.progressKey, startAtMs) {
        PendingPlayback(
            key = request.progressKey,
            movieId = request.details.id,
            title = request.details.title,
            posterUrl = request.details.posterUrl,
            pageUrl = request.details.pageUrl,
            voiceId = request.voiceId,
            seasonId = request.seasonId,
            episodeId = request.episodeId,
            startAtMs = startAtMs.coerceAtLeast(0L),
            durationMs = 0L,
            launchedAt = System.currentTimeMillis()
        )
    }

    fun save(position: Long, duration: Long) {
        if (position <= 0L) return
        ProgressStore.save(
            context = context,
            key = request.progressKey,
            movieId = request.details.id,
            title = request.details.title,
            posterUrl = request.details.posterUrl,
            pageUrl = request.details.pageUrl,
            voiceId = request.voiceId,
            seasonId = request.seasonId,
            episodeId = request.episodeId,
            positionMs = position,
            durationMs = duration
        )
    }

    fun syncProgress(position: Long, duration: Long) {
        if (position <= 0L) return
        if (duration > 0L) {
            scope.launch {
                HdRezkaClient.reportProgress(
                    movieId = request.details.id,
                    translator = request.voiceId,
                    season = request.seasonId,
                    episode = request.episodeId,
                    positionSec = position / 1000,
                    durationSec = duration / 1000
                )
            }
        }
        if (GithubSyncConfigStore.load(context)?.isReady == true) {
            scope.launch { GithubProgressSync.sync(context) }
        }
    }

    fun finish(position: Long, duration: Long) {
        val safeDuration = duration.coerceAtLeast(0L)
        val safePosition = if (safeDuration > 0L) {
            position.coerceIn(0L, safeDuration)
        } else {
            position.coerceAtLeast(0L)
        }
        if (safePosition > 5_000L) {
            save(safePosition, safeDuration)
            syncProgress(safePosition, safeDuration)
        }
        PendingPlaybackStore.clear(context)
        resultReceived = true
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val rawPosition = data?.getIntExtra("position", 0)?.toLong() ?: 0L
        val rawDuration = data?.getIntExtra("duration", 0)?.toLong() ?: 0L
        if (result.resultCode == 4) {
            PendingPlaybackStore.clear(context)
            resultReceived = true
            onFallback()
            return@rememberLauncherForActivityResult
        }
        val stored = PendingPlaybackStore.load(context)
        val duration = rawDuration.takeIf { it > 0L }
            ?: stored?.durationMs?.takeIf { it > 0L }
            ?: 0L
        val estimated = stored?.estimatedPosition()
            ?: if (launchStartedAt > 0L) {
                startAtMs.coerceAtLeast(0L) +
                    (System.currentTimeMillis() - launchStartedAt).coerceAtLeast(0L)
            } else {
                startAtMs.coerceAtLeast(0L)
            }
        val position = when {
            rawPosition > 0L -> rawPosition
            result.resultCode == 1 && duration > 0L -> duration
            result.resultCode == 1 -> estimated.coerceAtLeast(1L)
            else -> estimated
        }
        // Если Vimu сообщил о завершении без длительности, считаем
        // позицию финальной, чтобы фильм не висел в «Продолжить».
        val finalDuration = if (result.resultCode == 1 && duration <= 0L) {
            position.coerceAtLeast(1L)
        } else {
            duration
        }
        finish(position, finalDuration)
        onBack()
    }

    // Страховка на случай, если Vimu не вернул ActivityResult:
    // пользователь вышел через Home, плеер закрылся системой и т.п.
    DisposableEffect(lifecycleOwner, launched) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    if (launched) pausedForPlayback = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (launched && pausedForPlayback && !resultReceived) {
                        scope.launch {
                            delay(1_500)
                            if (!resultReceived) {
                                val pending = PendingPlaybackStore.load(context)
                                if (pending != null) {
                                    val position = pending.estimatedPosition()
                                    if (position > 5_000L) {
                                        save(position, pending.durationMs)
                                        syncProgress(position, pending.durationMs)
                                    }
                                    PendingPlaybackStore.clear(context)
                                }
                                pausedForPlayback = false
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(stream.url) {
        val title = buildString {
            append(request.details.title)
            request.episodes.firstOrNull { it.id == request.episodeId }?.let {
                append(" · ")
                append(it.title)
            }
        }
        launchStartedAt = System.currentTimeMillis()
        PendingPlaybackStore.save(
            context,
            basePending.copy(launchedAt = launchStartedAt)
        )
        launched = true
        try {
            launcher.launch(
                VimuPlayer.playbackIntent(
                    context = context,
                    url = stream.url,
                    title = title,
                    startAtMs = startAtMs,
                    subtitleUrl = stream.subtitleUrl
                )
            )
        } catch (_: ActivityNotFoundException) {
            PendingPlaybackStore.clear(context)
            onFallback()
        }
    }

    // Пока Vimu открывается, не показываем промежуточное окно с текстом:
    // просто чёрный экран, чтобы переход был бесшовным.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

@Composable
private fun VideoPlayer(
    stream: StreamInfo,
    request: PlayRequest,
    startAtMs: Long,
    onBack: () -> Unit,
    onSwitch: (String) -> Unit,
    onError: (String) -> Unit,
    loadingSeason: Boolean,
    seasonError: String?,
    onSelectQuality: (String, Long) -> Unit,
    onSelectVoice: (String, Long) -> Unit,
    onSelectSeason: (String) -> Unit,
    onSelectEpisode: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastSiteReport by remember { mutableStateOf(0L) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var textEnabled by remember { mutableStateOf(SettingsStore.subtitlesDefault(context)) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepDeadline by remember { mutableStateOf(0L) }
    var showStats by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var toast by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var nextCountdown by remember { mutableIntStateOf(0) }
    var pendingNext by remember { mutableStateOf<Episode?>(null) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun save() {
        ProgressStore.save(
            context = context,
            key = request.progressKey,
            movieId = request.details.id,
            title = request.details.title,
            posterUrl = request.details.posterUrl,
            pageUrl = request.details.pageUrl,
            voiceId = request.voiceId,
            seasonId = request.seasonId,
            episodeId = request.episodeId,
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0)
        )
        val now = System.currentTimeMillis()
        if (now - lastSiteReport > 20_000L) {
            lastSiteReport = now
            scope.launch {
                HdRezkaClient.reportProgress(
                    movieId = request.details.id,
                    translator = request.voiceId,
                    season = request.seasonId,
                    episode = request.episodeId,
                    positionSec = player.currentPosition / 1000,
                    durationSec = player.duration.coerceAtLeast(0) / 1000
                )
            }
        }
    }

    fun showToast(value: String) {
        toast = value
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
            showToast("Пауза")
        } else {
            player.play()
            showToast("Смотрю")
        }
        controlsVisible = true
    }

    fun seekBy(delta: Long) {
        player.seekTo(max(0L, player.currentPosition + delta))
        showToast(if (delta > 0) "+10 с" else "−10 с")
        controlsVisible = true
    }

    LaunchedEffect(nextCountdown, pendingNext) {
        if (nextCountdown <= 0) return@LaunchedEffect
        delay(1_000)
        nextCountdown -= 1
        if (nextCountdown <= 0) {
            pendingNext = null
            save()
            onSwitch("next")
        }
    }

    LaunchedEffect(Unit) {
        if (startAtMs > 5_000L) {
            delay(700)
            showToast("Продолжаем с ${formatTime(startAtMs)}")
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1100)
            toast = null
        }
    }

    LaunchedEffect(controlsVisible, playing, settingsOpen) {
        if (controlsVisible && playing && !settingsOpen) {
            delay(4200)
            if (player.isPlaying) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            runCatching { playFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    LaunchedEffect(sleepDeadline) {
        if (sleepDeadline > 0L) {
            val wait = (sleepDeadline - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(wait)
            player.pause()
            sleepMinutes = 0
            sleepDeadline = 0L
            showToast("Таймер сна: пауза")
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError(error.errorCodeName + ": " + (error.message ?: "ошибка плеера"))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) save()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    save()
                    val index = request.episodes.indexOfFirst { it.id == request.episodeId }
                    val next = request.episodes.getOrNull(index + 1)
                    if (SettingsStore.autoplayNext(context) && next != null) {
                        pendingNext = next
                        nextCountdown = 10
                        controlsVisible = false
                    } else {
                        controlsVisible = true
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                save()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(stream.url) {
        val dataSource = OkHttpDataSource.Factory(HdRezkaClient.playerHttp)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to HdRezkaClient.userAgent,
                    "Referer" to request.details.pageUrl
                )
            )
        val subtitle = stream.subtitleUrl?.let { url ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("ru")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        val mediaItem = MediaItem.Builder()
            .setUri(stream.url)
            .apply {
                if (subtitle != null) setSubtitleConfigurations(listOf(subtitle))
            }
            .build()
        val mediaSource = DefaultMediaSourceFactory(dataSource)
            .createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !textEnabled)
            .build()
        if (startAtMs > 5_000L) {
            player.seekTo(startAtMs)
        }
        player.playWhenReady = true
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            playing = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            videoWidth = player.videoFormat?.width ?: 0
            videoHeight = player.videoFormat?.height ?: 0
            bufferedMs = player.totalBufferedDuration
            if (positionMs > 5_000L && positionMs % 2_000L < 400L) {
                save()
            }
            delay(400)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (controlsVisible) return@onKeyEvent false

                controlsVisible = true
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                        togglePlay()
                        true
                    }
                    Key.DirectionLeft -> {
                        seekBy(-10_000)
                        true
                    }
                    Key.DirectionRight -> {
                        seekBy(10_000)
                        true
                    }
                    else -> true
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (buffering) {
            CircularProgressIndicator(
                color = Accent,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showStats) {
            StatsPanel(
                positionMs = positionMs,
                durationMs = durationMs,
                width = videoWidth,
                height = videoHeight,
                bufferedMs = bufferedMs,
                isHls = stream.isHls,
                onClose = { showStats = false }
            )
        }

        if (controlsVisible) {
            ControlsOverlay(
                request = request,
                positionMs = positionMs,
                durationMs = durationMs,
                playing = playing,
                hasSubtitles = stream.subtitleUrl != null,
                subtitlesOn = textEnabled,
                playFocus = playFocus,
                onPlayPause = { togglePlay() },
                onSeekBack = { seekBy(-10_000) },
                onSeekForward = { seekBy(10_000) },
                onPrevious = {
                    save()
                    onSwitch("previous")
                },
                onNext = {
                    save()
                    onSwitch("next")
                },
                onSubtitles = {
                    textEnabled = !textEnabled
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !textEnabled)
                        .build()
                    showToast(if (textEnabled) "Субтитры включены" else "Субтитры выключены")
                },
                onSettings = {
                    settingsOpen = true
                    controlsVisible = true
                },
                onBack = onBack,
                onInteract = { controlsVisible = true }
            )
        }

        val next = pendingNext
        if (nextCountdown > 0 && next != null) {
            NextEpisodeOverlay(
                countdown = nextCountdown,
                episode = next,
                onPlayNow = {
                    nextCountdown = 0
                    pendingNext = null
                    save()
                    onSwitch("next")
                },
                onCancel = {
                    nextCountdown = 0
                    pendingNext = null
                    controlsVisible = true
                }
            )
        }

        if (settingsOpen) {
            PlayerSettingsDialog(
                request = request,
                stream = stream,
                speed = speed,
                subtitlesOn = textEnabled,
                hasSubtitles = stream.subtitleUrl != null,
                loadingSeason = loadingSeason,
                seasonError = seasonError,
                sleepMinutes = sleepMinutes,
                onQuality = { quality ->
                    settingsOpen = false
                    onSelectQuality(quality, player.currentPosition)
                },
                onVoice = { voice ->
                    settingsOpen = false
                    onSelectVoice(voice, player.currentPosition)
                },
                onSeason = { id -> onSelectSeason(id) },
                onEpisode = { id ->
                    settingsOpen = false
                    onSelectEpisode(id)
                },
                onSubtitles = { enabled ->
                    textEnabled = enabled
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                        .build()
                },
                onSpeed = { value ->
                    speed = value
                    player.setPlaybackSpeed(value)
                },
                onSleep = {
                    sleepMinutes = when (sleepMinutes) {
                        0 -> 15
                        15 -> 30
                        30 -> 45
                        45 -> 60
                        else -> 0
                    }
                    sleepDeadline = if (sleepMinutes == 0) 0L else
                        System.currentTimeMillis() + sleepMinutes * 60_000L
                    showToast(if (sleepMinutes == 0) "Таймер сна выключен" else "Сон $sleepMinutes мин")
                },
                onStats = {
                    settingsOpen = false
                    showStats = !showStats
                },
                onClose = { settingsOpen = false }
            )
        }

        toast?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 150.dp else 60.dp)
                    .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun NextEpisodeOverlay(
    countdown: Int,
    episode: Episode,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    val buttonFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { buttonFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            Modifier
                .padding(42.dp)
                .width(430.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF01A1A20))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(22.dp)
        ) {
            Text(
                "Следующая серия",
                color = AccentWarm,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                episode.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Автозапуск через $countdown сек",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TvButton(
                    text = "Смотреть сейчас",
                    onClick = onPlayNow,
                    selected = true,
                    modifier = Modifier.focusRequester(buttonFocus)
                )
                TvButton(text = "Отмена", onClick = onCancel)
            }
        }
    }
}

@Composable
private fun ControlsOverlay(
    request: PlayRequest,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    hasSubtitles: Boolean,
    subtitlesOn: Boolean,
    playFocus: FocusRequester,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSubtitles: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onInteract: () -> Unit
) {
    val index = request.episodes.indexOfFirst { it.id == request.episodeId }
    val hasPrevious = index > 0
    val hasNext = index >= 0 && index + 1 < request.episodes.size

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = 42.dp, vertical = 24.dp)
        ) {
            Column {
                Text(
                    request.details.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                val episode = request.episodes.firstOrNull {
                    it.id == request.episodeId
                }
                Text(
                    if (episode != null) {
                        "Сезон ${request.seasonId ?: "—"} · ${episode.title}"
                    } else {
                        request.details.originalTitle ?: ""
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    buildString {
                        append(
                            if (request.quality == "auto") "Авто"
                            else request.quality
                        )
                        val voice = request.details.voiceActings
                            .firstOrNull { it.id == request.voiceId }?.name
                        if (!voice.isNullOrBlank()) {
                            append(" · ")
                            append(voice)
                        }
                    },
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                    )
                )
                .padding(horizontal = 42.dp, vertical = 22.dp)
        ) {
            Timeline(
                positionMs = positionMs,
                durationMs = durationMs
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    request.quality.replace("auto", "Авто"),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))

                if (hasPrevious) {
                    TvButton(text = "‹ Серия", onClick = onPrevious)
                    Spacer(Modifier.width(8.dp))
                }

                TvButton(text = "−10", onClick = onSeekBack)
                Spacer(Modifier.width(8.dp))

                var playFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(54.dp)
                        .focusRequester(playFocus)
                        .onFocusChanged { playFocused = it.isFocused }
                        .clip(CircleShape)
                        .background(Accent)
                        .border(
                            width = if (playFocused) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (playing) "❚❚" else "▶",
                        color = Color.White,
                        fontSize = 17.sp
                    )
                }

                Spacer(Modifier.width(8.dp))
                TvButton(text = "+10", onClick = onSeekForward)

                if (hasNext) {
                    Spacer(Modifier.width(8.dp))
                    TvButton(text = "Серия ›", onClick = onNext)
                }

                Spacer(Modifier.width(14.dp))

                if (hasSubtitles) {
                    TvButton(
                        text = if (subtitlesOn) "Субтитры" else "Без субтитров",
                        onClick = onSubtitles
                    )
                    Spacer(Modifier.width(8.dp))
                }

                TvButton(text = "Настройки", onClick = onSettings)
                Spacer(Modifier.width(8.dp))
                TvButton(text = "Назад", onClick = onBack)
            }
        }
    }

    LaunchedEffect(Unit) {
        onInteract()
    }
}

@Composable
private fun PlayerSettingsDialog(
    request: PlayRequest,
    stream: StreamInfo,
    speed: Float,
    subtitlesOn: Boolean,
    hasSubtitles: Boolean,
    loadingSeason: Boolean,
    seasonError: String?,
    sleepMinutes: Int,
    onQuality: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSeason: (String) -> Unit,
    onEpisode: (String) -> Unit,
    onSubtitles: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit,
    onSleep: () -> Unit,
    onStats: () -> Unit,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    val qualities = (listOf("auto" to "Авто") +
        stream.qualities.map { it.label to it.label })
        .distinctBy { it.first }
    val selectedQuality = if (request.quality == "auto") "auto" else request.quality
    val speedOptions = listOf(
        0.75f to "0.75×",
        1f to "1×",
        1.25f to "1.25×",
        1.5f to "1.5×",
        2f to "2×"
    )

    Dialog(
        onDismissRequest = onClose,
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
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Настройки плеера",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        request.details.title,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TvButton(text = "Закрыть", onClick = onClose)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    item {
                        PlayerSectionLabel("КАЧЕСТВО")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            items(qualities, key = { it.first }) { (id, label) ->
                                TvButton(
                                    text = label,
                                    selected = id == selectedQuality,
                                    focusRequester = if (id == qualities.first().first) {
                                        firstFocus
                                    } else null,
                                    onClick = { onQuality(id) }
                                )
                            }
                        }
                    }

                    if (request.details.voiceActings.size > 1) {
                        item {
                            PlayerSectionLabel("ОЗВУЧКА")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                items(
                                    request.details.voiceActings,
                                    key = { it.id }
                                ) { voice ->
                                    TvButton(
                                        text = if (voice.isPremium) "${voice.name} 🔒" else voice.name,
                                        selected = voice.id == request.voiceId,
                                        onClick = { onVoice(voice.id) }
                                    )
                                }
                            }
                        }
                    }

                    if (request.details.isSeries) {
                        if (request.details.seasons.size > 1) {
                            item {
                                PlayerSectionLabel("СЕЗОН")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    items(request.details.seasons, key = { it.id }) { season ->
                                        TvButton(
                                            text = season.title,
                                            selected = season.id == request.seasonId,
                                            onClick = { onSeason(season.id) }
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            PlayerSectionLabel("СЕРИЯ")
                            when {
                                loadingSeason -> Row(verticalAlignment = Alignment.CenterVertically) {
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
                                seasonError != null -> Text(
                                    seasonError,
                                    color = AccentWarm,
                                    fontSize = 11.sp
                                )
                                request.episodes.isEmpty() -> Text(
                                    "Серии не найдены",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                else -> LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    items(request.episodes, key = { it.id }) { episode ->
                                        val index = request.episodes.indexOfFirst { it.id == episode.id }
                                        TvButton(
                                            text = "${index + 1}. " +
                                                episode.title.ifBlank { "Серия ${index + 1}" },
                                            selected = episode.id == request.episodeId,
                                            onClick = { onEpisode(episode.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (hasSubtitles) {
                        item {
                            PlayerSectionLabel("СУБТИТРЫ")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TvButton(
                                    text = "Включены",
                                    selected = subtitlesOn,
                                    onClick = { onSubtitles(true) }
                                )
                                TvButton(
                                    text = "Выключены",
                                    selected = !subtitlesOn,
                                    onClick = { onSubtitles(false) }
                                )
                            }
                        }
                    }

                    item {
                        PlayerSectionLabel("СКОРОСТЬ")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            items(speedOptions, key = { it.first }) { (value, label) ->
                                TvButton(
                                    text = label,
                                    selected = kotlin.math.abs(speed - value) < 0.01f,
                                    onClick = { onSpeed(value) }
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvButton(
                                text = if (sleepMinutes > 0) "Сон ${sleepMinutes} мин" else "Таймер сна",
                                onClick = onSleep
                            )
                            TvButton(text = "Статистика", onClick = onStats)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSectionLabel(text: String) {
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
private fun PillButton(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceStrong)
    ) {
        Text(title, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun Timeline(positionMs: Long, durationMs: Long) {
    val progress = if (durationMs > 0) {
        (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
    } else 0f

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val width = maxWidth
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
        )
        Box(
            Modifier
                .width(width * progress)
                .height(4.dp)
                .clip(CircleShape)
                .background(Accent)
        )
        Box(
            Modifier
                .offset(x = (width * progress) - 6.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun StatsPanel(
    positionMs: Long,
    durationMs: Long,
    width: Int,
    height: Int,
    bufferedMs: Long,
    isHls: Boolean,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 88.dp, end = 30.dp),
        horizontalAlignment = Alignment.End
    ) {
        Column(
            Modifier
                .background(Color.Black.copy(alpha = 0.86f), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Статистика",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    "✕",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(4.dp)
                )
            }
            Text("${width}×${height}", color = TextSecondary, fontSize = 11.sp)
            Text("Буфер: ${bufferedMs / 1000} с", color = TextSecondary, fontSize = 11.sp)
            Text(
                "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Text("Поток: ${if (isHls) "HLS" else "MP4"}", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

private fun formatTime(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}
