package com.hdrezka.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.hdrezka.tv.data.EmbeddedBootstrap
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.RoomStores
import com.hdrezka.tv.data.SettingsStore
import com.hdrezka.tv.ui.HDRezkaTheme
import com.hdrezka.tv.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HdRezkaClient.init(this)
        EmbeddedBootstrap.init(this)
        RoomStores.init(this)
        SettingsStore.applyBuiltInPlayerDefault(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(HdRezkaClient.imageHttp)
                .crossfade(false)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.2)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .build()
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            HDRezkaTheme {
                AppRoot()
            }
        }
    }
}
