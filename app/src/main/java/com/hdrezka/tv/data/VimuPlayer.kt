package com.hdrezka.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri

object VimuPlayer {
    const val PACKAGE = "net.gtvbox.videoplayer"

    fun isInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun playbackIntent(
        context: Context,
        url: String,
        title: String,
        startAtMs: Long,
        subtitleUrl: String? = null
    ): Intent {
        val mime = if (url.contains(".m3u8", ignoreCase = true)) {
            "application/x-mpegURL"
        } else {
            "video/*"
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setPackage(PACKAGE)
            setDataAndType(Uri.parse(url), mime)
            putExtra("forcename", title)
            putExtra("startfrom", startAtMs.coerceAtLeast(0L).toInt())
            // false — чтобы Vimu не перебивал нашу позицию своей историей.
            putExtra("forceresume", false)
            if (!subtitleUrl.isNullOrBlank()) {
                putExtra("forcedsrt", subtitleUrl)
            }
        }
    }

    fun storeIntent(): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
