package com.hdrezka.tv.data

import org.json.JSONObject

object StreamResolver {
    suspend fun resolve(
        details: MovieDetails,
        voiceId: String,
        seasonId: String?,
        episodeId: String?,
        preferredQuality: String = "1080p"
    ): StreamInfo {
        val actions = if (details.isSeries) {
            listOf("get_stream", "get_movie")
        } else {
            listOf("get_movie", "get_stream")
        }
        var lastError: Exception? = null

        for (action in actions) {
            try {
                val stamp = System.currentTimeMillis()
                val form = linkedMapOf(
                    "id" to details.id,
                    "translator_id" to voiceId,
                    "action" to action
                )
                if (seasonId != null) form["season"] = seasonId
                if (episodeId != null) form["episode"] = episodeId

                val body = HdRezkaClient.postForm(
                    "/ajax/get_cdn_series/?t=$stamp",
                    form,
                    referer = details.pageUrl
                )
                return parseStream(body, preferredQuality)
            } catch (error: Exception) {
                lastError = error
            }
        }

        if (!details.isSeries) {
            val html = HdRezkaClient.get(details.pageUrl)
            val raw = Parser.embeddedStreams(html)
            if (raw != null) {
                val options = Parser.parseQualityList(raw)
                val chosen = Parser.chooseStream(options, preferredQuality)
                if (chosen != null) {
                    return StreamInfo(
                        url = chosen.url,
                        quality = chosen.label,
                        subtitleUrl = null,
                        isHls = chosen.url.contains(".m3u8"),
                        qualities = options
                    )
                }
            }
        }

        throw lastError ?: HdRezkaException("Сервер не отдал поток")
    }

    private fun parseStream(body: String, preferredQuality: String): StreamInfo {
        val json = JSONObject(body)
        val success = json.opt("success")
        val isSuccess = when (success) {
            is Boolean -> success
            is Number -> success.toInt() != 0
            is String -> success.equals("true", ignoreCase = true) || success == "1"
            else -> true
        }
        if (!isSuccess) {
            throw HdRezkaException(
                json.optString("message").ifBlank { "Сервер не отдал поток" }
            )
        }

        val list = json.optString("url")
        if (list.isBlank()) throw HdRezkaException("В ответе нет ссылки на поток")

        val options = Parser.parseQualityList(list)
        val chosen = Parser.chooseStream(options, preferredQuality)
            ?: throw HdRezkaException("Не удалось разобрать качества")

        val subtitleRaw = json.optString("subtitle")
        val subtitle = Regex("(https?://[^,\\s\\]]+\\.vtt)")
            .find(subtitleRaw)?.value

        return StreamInfo(
            url = chosen.url,
            quality = chosen.label.ifBlank { null },
            subtitleUrl = subtitle,
            isHls = chosen.url.contains(".m3u8", ignoreCase = true),
            qualities = options
        )
    }
}
