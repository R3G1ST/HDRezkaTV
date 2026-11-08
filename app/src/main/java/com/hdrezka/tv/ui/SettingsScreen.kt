package com.hdrezka.tv.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hdrezka.tv.BuildConfig
import com.hdrezka.tv.data.AccountStore
import com.hdrezka.tv.data.GithubProgressSync
import com.hdrezka.tv.data.GithubSyncConfig
import com.hdrezka.tv.data.GithubSyncConfigStore
import com.hdrezka.tv.data.ConnectionResult
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.LibraryStore
import com.hdrezka.tv.data.Mirrors
import com.hdrezka.tv.data.ProgressStore
import com.hdrezka.tv.data.ParentalStore
import com.hdrezka.tv.data.RemindersStore
import com.hdrezka.tv.data.ReleaseCheckScheduler
import com.hdrezka.tv.data.ReleaseWatchStore
import com.hdrezka.tv.data.SettingsStore
import com.hdrezka.tv.data.VimuPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.withFrameNanos

private val qualityOptions = listOf(
    "auto" to "Авто",
    "1080p Ultra" to "1080p Ultra",
    "1080p" to "1080p",
    "720p" to "720p",
    "480p" to "480p",
    "2K" to "2K",
    "4K" to "4K"
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onLogin: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = remember { AccountStore.load(context) }
    var reminderCount by remember { mutableStateOf(RemindersStore.list(context).size) }
    var historyCount by remember { mutableStateOf(ProgressStore.list(context).size) }
    var favoriteCount by remember { mutableStateOf(LibraryStore.favorites(context).size) }
    var laterCount by remember { mutableStateOf(LibraryStore.watchLater(context).size) }
    var clearRequest by remember { mutableStateOf<ClearTarget?>(null) }
    var mirrors by remember { mutableStateOf(Mirrors.all(context)) }
    var activeMirror by remember { mutableStateOf(Mirrors.active(context) ?: HdRezkaClient.baseUrl) }
    var mirrorStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mirrorBusy by remember { mutableStateOf<String?>(null) }
    var testingConnection by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var connectionOk by remember { mutableStateOf(true) }
    var showAddMirror by remember { mutableStateOf(false) }
    var newMirror by remember { mutableStateOf("") }
    var mirrorError by remember { mutableStateOf<String?>(null) }

    var quality by remember { mutableStateOf(SettingsStore.defaultQuality(context)) }
    var autoplay by remember { mutableStateOf(SettingsStore.autoplayNext(context)) }
    var subtitles by remember { mutableStateOf(SettingsStore.subtitlesDefault(context)) }
    var useVimu by remember { mutableStateOf(SettingsStore.useVimu(context)) }
    var parentalEnabled by remember { mutableStateOf(ParentalStore.isEnabled(context)) }
    var parentalPinSet by remember { mutableStateOf(ParentalStore.hasPin(context)) }
    var showPinSetup by remember { mutableStateOf(false) }
    val vimuInstalled = remember { VimuPlayer.isInstalled(context) }
    val syncConfig = remember { GithubSyncConfigStore.load(context) }
    var githubConfig by remember { mutableStateOf(syncConfig) }
    var showGithubConnect by remember { mutableStateOf(false) }
    var syncOwner by remember { mutableStateOf(syncConfig?.owner.orEmpty()) }
    var syncRepo by remember { mutableStateOf(syncConfig?.repo.orEmpty()) }
    var syncToken by remember { mutableStateOf(syncConfig?.token.orEmpty()) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember {
        mutableStateOf(
            GithubSyncConfigStore.lastError(context)?.let { "Ошибка: $it" }
                ?: GithubSyncConfigStore.lastSync(context).takeIf { it > 0 }?.let {
                    "Последняя синхронизация: " +
                        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(it))
                }.orEmpty()
        )
    }
    val startGithubSync: (GithubSyncConfig) -> Unit = { config ->
        syncing = true
        syncMessage = "Синхронизируем…"
        scope.launch {
            val result = GithubProgressSync.sync(context)
            syncMessage = result.fold(
                { summary ->
                    "Синхронизировано: ${summary.mergedCount} записей · " +
                        SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(summary.at))
                },
                { error -> "Ошибка: ${error.message}" }
            )
            syncing = false
        }
    }
    val runGithubSync: () -> Unit = {
        val config = githubConfig
        if (config == null) {
            showGithubConnect = true
        } else if (!syncing) {
            startGithubSync(config)
        }
    }
    val connectGithub: () -> Unit = {
        val config = GithubSyncConfig(
            owner = syncOwner.trim(),
            repo = syncRepo.trim(),
            token = syncToken.trim()
        )
        if (!config.isReady) {
            syncMessage = "Заполни владельца, репозиторий и токен"
        } else {
            GithubSyncConfigStore.save(context, config)
            githubConfig = config
            showGithubConnect = false
            startGithubSync(config)
        }
    }
    val mirrorActionsFocus = remember { FocusRequester() }
    val statusLine: (ConnectionResult) -> String = { result ->
        if (result.ok) "OK · ${result.latencyMs} мс" else "нет связи"
    }
    val testMirror: (String) -> Unit = { mirror ->
        if (mirrorBusy == null) {
            mirrorBusy = mirror
            scope.launch {
                val result = HdRezkaClient.testConnection(mirror)
                mirrorStatus = mirrorStatus + (mirror to statusLine(result))
                mirrorBusy = null
            }
        }
    }
    val testCurrentMirror: () -> Unit = {
        if (!testingConnection) {
            testingConnection = true
            connectionStatus = "Проверяем…"
            connectionOk = true
            scope.launch {
                val result = HdRezkaClient.testConnection()
                connectionOk = result.ok
                connectionStatus = if (result.ok) {
                    "OK · ${Mirrors.host(activeMirror)} · ${result.latencyMs} мс"
                } else {
                    "Нет связи: ${result.status}"
                }
                testingConnection = false
            }
        }
    }
    val testAllMirrors: () -> Unit = {
        if (mirrorBusy == null && !testingConnection) {
            scope.launch {
                testingConnection = true
                connectionStatus = "Проверяем зеркала…"
                var available = 0
                for (mirror in mirrors) {
                    mirrorBusy = mirror
                    val result = HdRezkaClient.testConnection(mirror)
                    mirrorStatus = mirrorStatus + (mirror to statusLine(result))
                    if (result.ok) available++
                }
                connectionOk = available > 0
                connectionStatus = "Доступно зеркал: $available из ${mirrors.size}"
                mirrorBusy = null
                testingConnection = false
            }
        }
    }
    val selectMirror: (String) -> Unit = { mirror ->
        Mirrors.apply(context, mirror)
        activeMirror = Mirrors.normalize(mirror)
        connectionStatus = "Зеркало сохранено · проверяем…"
        connectionOk = true
        if (mirrorBusy == null) {
            mirrorBusy = mirror
            scope.launch {
                val result = HdRezkaClient.testConnection(mirror)
                mirrorStatus = mirrorStatus + (mirror to statusLine(result))
                connectionOk = result.ok
                connectionStatus = if (result.ok) {
                    "OK · ${Mirrors.host(mirror)} · ${result.latencyMs} мс"
                } else {
                    "Нет связи: ${result.status}"
                }
                mirrorBusy = null
            }
        }
    }

    val disconnectGithub: () -> Unit = {
        GithubSyncConfigStore.clear(context)
        githubConfig = null
        syncOwner = ""
        syncRepo = ""
        syncToken = ""
        syncMessage = ""
    }

    val backFocus = remember { FocusRequester() }
    val parentalPinFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(6) {
            withFrameNanos { }
            runCatching { backFocus.requestFocus() }
            kotlinx.coroutines.delay(60)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "Настройки",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Аккаунт · воспроизведение · сеть и данные",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                SettingsButton(
                    text = "Назад",
                    onClick = onBack,
                    icon = Icons.Filled.ArrowBack,
                    focusRequester = backFocus
                )
            }
        }

        item(key = "account") {
            SettingsCard(title = "АККАУНТ", icon = Icons.Filled.Person) {
                if (account == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Вы не вошли в аккаунт.",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.weight(1f))
                        SettingsButton(text = "Войти", onClick = onLogin, primary = true)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                account.name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.name,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (account.isPremium) "PRO · подписка активна" else "Аккаунт HDrezka",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Text(
                                "Если видео не запускается — войдите заново",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        SettingsButton(text = "Войти заново", onClick = onLogin)
                        Spacer(Modifier.width(10.dp))
                        SettingsButton(
                            text = "Выйти",
                            onClick = {
                                scope.launch {
                                    HdRezkaClient.logout()
                                    AccountStore.clear(context)
                                    onLoggedOut()
                                }
                            }
                        )
                    }
                }

                SettingsDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF24292F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "GH",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "GitHub",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            githubConfig?.let { "${it.owner}/${it.repo}" }
                                ?: "Синхронизация позиций Mac ↔ TV",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        if (githubConfig != null) Color(0xFF4CAF50)
                                        else Color(0xFF55555F),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    syncing -> "Синхронизация…"
                                    syncMessage.isNotBlank() -> syncMessage
                                    githubConfig != null -> "Подключено"
                                    else -> "Не подключено"
                                },
                                color = if (syncMessage.startsWith("Ошибка")) Color(0xFFFF8A80)
                                else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (githubConfig != null) {
                        SettingsButton(
                            text = if (syncing) "Синхронизация…" else "Синхронизировать",
                            onClick = { runGithubSync() },
                            primary = true,
                            icon = Icons.Filled.Refresh
                        )
                        Spacer(Modifier.width(10.dp))
                        SettingsButton(text = "Отключить", onClick = disconnectGithub)
                    } else {
                        SettingsButton(
                            text = "Подключить",
                            onClick = { showGithubConnect = true },
                            primary = true
                        )
                    }
                }
            }
        }

        item(key = "playback") {
            SettingsCard(title = "ВОСПРОИЗВЕДЕНИЕ", icon = Icons.Filled.PlayArrow) {
                Column {
                    Text(
                        "Качество по умолчанию",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(qualityOptions, key = { it.first }) { (value, label) ->
                            SettingsButton(
                                text = label,
                                selected = quality == value,
                                onClick = {
                                    quality = value
                                    SettingsStore.setDefaultQuality(context, value)
                                }
                            )
                        }
                    }
                }

                SettingsDivider()

                Column {
                    SettingToggle(
                        title = "Автопереход к следующей серии",
                        value = autoplay,
                        onChange = {
                            autoplay = it
                            SettingsStore.setAutoplayNext(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingToggle(
                        title = "Субтитры включены по умолчанию",
                        value = subtitles,
                        onChange = {
                            subtitles = it
                            SettingsStore.setSubtitlesDefault(context, it)
                        }
                    )
                    SettingsDivider()
                    SettingToggle(
                        title = "Внешний плеер Vimu (необязательно)",
                        value = useVimu,
                        onChange = {
                            useVimu = it
                            SettingsStore.setUseVimu(context, it)
                        }
                    )
                }

                Text(
                    if (vimuInstalled) {
                        "По умолчанию используется встроенный плеер. Vimu можно включить при необходимости."
                    } else {
                        "Используется встроенный плеер. Vimu не установлен."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                if (!vimuInstalled) {
                    SettingsButton(
                        text = "Открыть Vimu в Play Store",
                        onClick = { context.startActivity(VimuPlayer.storeIntent()) }
                    )
                }
            }
        }

        item(key = "parental") {
            SettingsCard(title = "РОДИТЕЛЬСКИЙ КОНТРОЛЬ", icon = Icons.Filled.Lock) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (parentalEnabled) "Включён" else "Выключен",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            if (parentalPinSet) "PIN установлен" else "PIN не задан",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    SettingsButton(
                        text = if (parentalPinSet) "Изменить PIN" else "Установить PIN",
                        focusRequester = parentalPinFocus,
                        onClick = { showPinSetup = true }
                    )
                    Spacer(Modifier.width(10.dp))
                    if (parentalPinSet) {
                        SettingsButton(
                            text = if (parentalEnabled) "Отключить" else "Включить",
                            onClick = {
                                if (parentalEnabled) {
                                    ParentalStore.disable(context)
                                } else {
                                    ParentalStore.enable(context)
                                }
                                parentalEnabled = ParentalStore.isEnabled(context)
                            }
                        )
                    }
                }
                Text(
                    "PIN запрашивается при открытии настроек и взрослого контента.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        item(key = "mirrors") {
            SettingsCard(title = "ЗЕРКАЛА И СВЯЗЬ", icon = Icons.Filled.Place) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Активное зеркало",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            Mirrors.host(activeMirror),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    SettingsButton(
                        text = if (testingConnection) "Проверяем…" else "Проверить связь",
                        primary = true,
                        enabled = !testingConnection,
                        icon = Icons.Filled.Refresh,
                        focusRequester = mirrorActionsFocus,
                        onClick = testCurrentMirror
                    )
                    Spacer(Modifier.width(10.dp))
                    SettingsButton(
                        text = "Проверить все",
                        enabled = mirrorBusy == null && !testingConnection,
                        onClick = testAllMirrors
                    )
                    if (Mirrors.custom(context).contains(activeMirror)) {
                        Spacer(Modifier.width(10.dp))
                        SettingsButton(
                            text = "Удалить",
                            enabled = !testingConnection,
                            onClick = {
                                Mirrors.removeCustom(context, activeMirror)
                                mirrors = Mirrors.all(context)
                                activeMirror = Mirrors.active(context) ?: HdRezkaClient.baseUrl
                                mirrorStatus = mirrorStatus - activeMirror
                            }
                        )
                    }
                }
                if (connectionStatus != null) {
                    Text(
                        connectionStatus!!,
                        color = if (connectionOk) Color(0xFF6FCF8B) else Color(0xFFFF8A80),
                        fontSize = 13.sp
                    )
                }

                SettingsDivider()

                for (mirror in mirrors) {
                    MirrorRow(
                        mirror = mirror,
                        isActive = mirror == activeMirror,
                        status = mirrorStatus[mirror],
                        busy = mirrorBusy == mirror,
                        upFocus = mirrorActionsFocus,
                        onSelect = { selectMirror(mirror) }
                    )
                    if (mirror != mirrors.last()) SettingsDivider()
                }

                SettingsDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsButton(
                        text = "Добавить зеркало",
                        icon = Icons.Filled.Add,
                        onClick = { showAddMirror = true }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Адрес можно вводить без https://",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                }
                Text(
                    "При смене зеркала ссылки истории и избранного обновляются автоматически.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        item(key = "reminders") {
            SettingsCard(title = "НАПОМИНАНИЯ", icon = Icons.Filled.Notifications) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Активных напоминаний: $reminderCount",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    SettingsButton(
                        text = "Очистить",
                        enabled = reminderCount > 0,
                        onClick = {
                            RemindersStore.clear(context)
                            reminderCount = 0
                        }
                    )
                }
            }
        }

        item(key = "data") {
            SettingsCard(title = "ДАННЫЕ", icon = Icons.Filled.Delete) {
                DataRow(
                    title = "История просмотров",
                    subtitle = "$historyCount " + plural(historyCount, "запись", "записи", "записей"),
                    actionEnabled = historyCount > 0,
                    onClear = { clearRequest = ClearTarget.History }
                )
                SettingsDivider()
                DataRow(
                    title = "Избранное",
                    subtitle = "$favoriteCount " + plural(favoriteCount, "тайтл", "тайтла", "тайтлов"),
                    actionEnabled = favoriteCount > 0,
                    onClear = { clearRequest = ClearTarget.Favorites }
                )
                SettingsDivider()
                DataRow(
                    title = "Смотреть позже",
                    subtitle = "$laterCount " + plural(laterCount, "тайтл", "тайтла", "тайтлов"),
                    actionEnabled = laterCount > 0,
                    onClear = { clearRequest = ClearTarget.WatchLater }
                )
            }
        }

        item(key = "about") {
            SettingsCard(title = "О ПРИЛОЖЕНИИ", icon = Icons.Filled.Info) {
                AboutRow("Версия", BuildConfig.VERSION_NAME)
                SettingsDivider()
                AboutRow("Сборка", BuildConfig.VERSION_CODE.toString())
                SettingsDivider()
                AboutRow("Платформа", "Android ${Build.VERSION.RELEASE}")
                SettingsDivider()
                AboutRow("Плеер", if (vimuInstalled) "Vimu + встроенный" else "Встроенный")
            }
        }

        item(key = "footer") {
            Text(
                "HDrezka TV ${BuildConfig.VERSION_NAME} · Google TV",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 30.dp)
            )
        }
    }

    if (showAddMirror) {
        val fieldFocus = rememberInitialFocus()
        val addFocus = remember { FocusRequester() }
        Dialog(
            onDismissRequest = { showAddMirror = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .width(620.dp)
                    .then(rememberDialogAppear())
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF16161A))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Добавить зеркало",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Адрес можно вводить без https://",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                SyncField(
                    label = "Адрес зеркала",
                    value = newMirror,
                    focusRequester = fieldFocus,
                    downFocus = addFocus,
                    onChange = { newMirror = it }
                )
                if (mirrorError != null) {
                    Text(mirrorError!!, color = Color(0xFFFF8A80), fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsButton(
                        text = "Добавить",
                        primary = true,
                        focusRequester = addFocus,
                        onClick = {
                            val normalized = Mirrors.normalize(newMirror)
                            when {
                                !Mirrors.isValid(normalized) ->
                                    mirrorError = "Некорректный адрес"
                                Mirrors.all(context).contains(normalized) ->
                                    mirrorError = "Такое зеркало уже есть"
                                else -> {
                                    Mirrors.addCustom(context, normalized)
                                    mirrors = Mirrors.all(context)
                                    newMirror = ""
                                    mirrorError = null
                                    showAddMirror = false
                                }
                            }
                        }
                    )
                    SettingsButton(
                        text = "Отмена",
                        onClick = { showAddMirror = false }
                    )
                }
            }
        }
    }

    clearRequest?.let { target ->
        val cancelFocus = rememberInitialFocus()
        Dialog(
            onDismissRequest = { clearRequest = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .width(560.dp)
                    .then(rememberDialogAppear())
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF16161A))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    target.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Это действие нельзя отменить.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsButton(
                        text = "Отмена",
                        onClick = { clearRequest = null },
                        focusRequester = cancelFocus
                    )
                    SettingsButton(
                        text = "Очистить",
                        primary = true,
                        onClick = {
                            when (target) {
                                ClearTarget.History -> {
                                    ProgressStore.clear(context)
                                    historyCount = 0
                                }
                                ClearTarget.Favorites -> {
                                    LibraryStore.clearFavorites(context)
                                    ReleaseWatchStore.clear(context)
                                    ReleaseCheckScheduler.cancel(context)
                                    favoriteCount = 0
                                }
                                ClearTarget.WatchLater -> {
                                    LibraryStore.clearWatchLater(context)
                                    laterCount = 0
                                }
                            }
                            clearRequest = null
                        }
                    )
                }
            }
        }
    }

    if (showGithubConnect) {
        Dialog(
            onDismissRequest = { if (!syncing) showGithubConnect = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .width(760.dp)
                    .then(rememberDialogAppear())
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF16161A))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Подключить GitHub",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Приватный репозиторий с файлом progress.json. Токен: Contents — Read and write.",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )
                SyncField(
                    label = "Владелец GitHub",
                    value = syncOwner,
                    onChange = { syncOwner = it }
                )
                SyncField(
                    label = "Репозиторий",
                    value = syncRepo,
                    onChange = { syncRepo = it }
                )
                SyncField(
                    label = "Токен",
                    value = syncToken,
                    password = true,
                    onChange = { syncToken = it }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsButton(
                        text = "Подключить",
                        primary = true,
                        onClick = connectGithub
                    )
                    SettingsButton(
                        text = "Отмена",
                        onClick = { showGithubConnect = false }
                    )
                }
                if (syncMessage.isNotBlank() && syncMessage.startsWith("Ошибка")) {
                    Text(
                        syncMessage,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
    if (showPinSetup) {
        ParentalPinSetupDialog(
            onDone = {
                showPinSetup = false
                parentalEnabled = ParentalStore.isEnabled(context)
                parentalPinSet = ParentalStore.hasPin(context)
            },
            onDismiss = { showPinSetup = false }
        )
    }
}

@Composable
private fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    icon: ImageVector? = null
) {
    TvButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        primary = primary,
        large = true,
        enabled = enabled,
        icon = icon,
        focusRequester = focusRequester
    )
}

private enum class ClearTarget(val title: String) {
    History("Очистить историю просмотров?"),
    Favorites("Очистить избранное?"),
    WatchLater("Очистить «Смотреть позже»?")
}

private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    if (mod100 in 11..14) return many
    return when (count % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, Hairline.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentWarm,
                modifier = Modifier.size(17.dp)
            )
            Text(
                title,
                color = TextSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
        }
        content()
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline.copy(alpha = 0.7f))
    )
}

@Composable
private fun DataRow(
    title: String,
    subtitle: String,
    actionEnabled: Boolean,
    onClear: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
        SettingsButton(
            text = "Очистить",
            onClick = { if (actionEnabled) onClear() },
            enabled = actionEnabled
        )
    }
}

@Composable
private fun MirrorRow(
    mirror: String,
    isActive: Boolean,
    status: String?,
    busy: Boolean,
    upFocus: FocusRequester? = null,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.012f else 1f,
        animationSpec = FocusSpring,
        label = "mirrorRowScale"
    )
    val background by animateColorAsState(
        targetValue = if (focused) Color(0xFF202028) else Color.Transparent,
        animationSpec = tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "mirrorRowBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Accent else Color.Transparent,
        animationSpec = tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "mirrorRowBorder"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (upFocus != null) {
                    Modifier.focusProperties { up = upFocus }
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (isActive) Color(0xFF4CAF50) else Color(0xFF55555F),
                    CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            Mirrors.host(mirror),
            color = if (isActive) Color.White else Color(0xFFD5D5DB),
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        if (isActive) {
            Text("текущее", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
        }
        if (busy) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
        } else if (status != null) {
            Text(
                status,
                color = if (status.startsWith("OK")) Color(0xFF6FCF8B) else Color(0xFFFF8A80),
                fontSize = 12.5.sp
            )
            Spacer(Modifier.width(10.dp))
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text(value, color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun SettingToggle(
    title: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    TvToggleRow(
        title = title,
        value = value,
        onChange = onChange,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SyncField(
    label: String,
    value: String,
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    onChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.5.sp) },
        singleLine = true,
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    if (downFocus != null) {
                        downFocus.requestFocus()
                    } else {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                    true
                } else {
                    false
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = SurfaceDark,
            unfocusedContainerColor = SurfaceDark,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Hairline,
            cursorColor = Accent,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextSecondary
        )
    )
}
