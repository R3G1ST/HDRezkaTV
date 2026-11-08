package com.hdrezka.tv.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object Parser {
    private val yearRegex = Regex("(?:19|20)\\d{2}")
    private val idRegex = Regex("/(\\d+)-[^/]+\\.html")

    fun parseProfileName(html: String): String? {
        val title = Jsoup.parse(html).title().trim()
        if (title.isEmpty() || title.length > 80) return null
        for (separator in listOf(" — ", " - ", " | ", " · ", ": ")) {
            val head = title.substringBefore(separator).trim()
            if (head.isNotEmpty()) return head
        }
        return title
    }

    fun parseAccount(html: String): AccountInfo? {
        val doc = Jsoup.parse(html)
        val id = doc.selectFirst("input#member_user_id")?.attr("value").orEmpty()
        if (id.isBlank() || id == "0") return null

        var name = doc.select("a[href]")
            .firstOrNull { it.attr("href").contains("/users/") }
            ?.text()?.trim().orEmpty()
        if (name.isBlank()) {
            for (selector in listOf(
                ".b-tophead__username",
                ".b-tophead__user a",
                ".b-user__name",
                ".b-tophead-premuser"
            )) {
                val candidate = doc.selectFirst(selector)?.text()?.trim().orEmpty()
                if (candidate.isNotBlank()) {
                    name = candidate
                    break
                }
            }
        }

        val premiumNode = doc.selectFirst(".b-tophead-premuser")
        return AccountInfo(
            id = id,
            name = name.ifBlank { "Пользователь" },
            isPremium = premiumNode != null
        )
    }

    /** Топ-100 на hdrezka.inc: ссылки a.top-content-item. */
    fun parseTop100(html: String, base: String): List<MovieItem> {
        val doc = Jsoup.parse(html, base)
        val result = ArrayList<MovieItem>()
        for (node in doc.select("a.top-content-item")) {
            val href = node.attr("href").trim()
            if (href.isBlank()) continue
            val pageUrl = HdRezkaClient.absoluteUrl(href, base)
            val title = node.selectFirst(".top-content-name")?.text()?.trim().orEmpty()
            if (title.isBlank()) continue
            val id = node.attr("data-id").ifBlank {
                idRegex.find(pageUrl)?.groupValues?.getOrNull(1).orEmpty()
            }
            if (id.isBlank()) continue
            val poster = node.selectFirst("img.lazy")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
                ?.let { HdRezkaClient.absoluteUrl(it, base) }
            result.add(
                MovieItem(
                    id = id,
                    title = title,
                    originalTitle = null,
                    year = null,
                    pageUrl = pageUrl,
                    posterUrl = poster,
                    kind = when {
                        href.contains("/serialy/") -> "Сериал"
                        href.contains("/filmy/") -> "Фильм"
                        else -> null
                    }
                )
            )
        }
        return result.distinctBy { it.id }
    }

    /**
     * Подборки на hdrezka.inc: другой HTML (div.collection + data-link).
     * Используется для автоматического списка стриминговых сервисов.
     */
    fun parseIncCollections(html: String, base: String): List<Collection> {
        val doc = Jsoup.parse(html, base)
        val result = ArrayList<Collection>()
        val seen = HashSet<String>()
        for (item in doc.select("div.collection[data-link]")) {
            val href = item.attr("data-link").trim()
            if (href.isBlank()) continue
            val url = HdRezkaClient.absoluteUrl(href, base)
            if (!seen.add(url)) continue
            var title = item.selectFirst("a.js-collection")?.text()?.trim().orEmpty()
            if (title.isBlank()) {
                title = item.selectFirst("img.collCover")?.attr("alt")?.trim().orEmpty()
                    .removePrefix("Подборка — ")
                    .removePrefix("Подборка - ")
            }
            if (title.isBlank()) continue
            val cover = item.selectFirst("img.collCover")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
                ?.let { HdRezkaClient.absoluteUrl(it, base) }
            val count = item.selectFirst(".collCount")?.text()?.trim()?.toIntOrNull() ?: 0
            result.add(
                Collection(
                    id = url,
                    title = title,
                    url = url,
                    coverUrl = cover,
                    count = count
                )
            )
        }
        return result
    }

    fun parseCollections(html: String, base: String): List<Collection> {
        val doc = Jsoup.parse(html, base)
        val result = ArrayList<Collection>()
        val seen = HashSet<String>()
        for (item in doc.select("div.b-content__collections_item")) {
            var href = item.attr("data-url")
            if (href.isBlank()) href = item.selectFirst("a.title")?.attr("href").orEmpty()
            if (href.isBlank()) continue
            val url = HdRezkaClient.absoluteUrl(href, base)
            if (!seen.add(url)) continue
            var title = item.selectFirst("a.title")?.text()?.trim().orEmpty()
            if (title.isBlank()) {
                title = item.selectFirst("img.cover")?.attr("alt")?.trim().orEmpty()
            }
            if (title.isBlank()) continue
            val cover = item.selectFirst("img.cover")?.attr("src")
                ?.takeIf { it.isNotBlank() }
                ?.let { HdRezkaClient.absoluteUrl(it, base) }
            val count = item.selectFirst(".num")?.text()?.trim()?.toIntOrNull() ?: 0
            result.add(
                Collection(
                    id = url,
                    title = title,
                    url = url,
                    coverUrl = cover,
                    count = count
                )
            )
        }
        return result
    }

    fun parseSchedule(html: String): List<ScheduleItem> {
        val doc = Jsoup.parse(html)
        val result = ArrayList<ScheduleItem>()
        for (row in doc.select("table.b-post__schedule_table tr")) {
            val label = row.selectFirst("td.td-1")?.text()?.trim().orEmpty()
            if (label.isBlank()) continue
            val title = row.selectFirst("td.td-2 b")?.text()?.trim().orEmpty()
            val original = row.selectFirst("td.td-2 span")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            val date = row.selectFirst("td.td-4")?.text()?.trim().orEmpty()
            val available = row.selectFirst("td.td-5 .exists-episode") != null
            result.add(
                ScheduleItem(
                    label = label,
                    title = title,
                    originalTitle = original,
                    date = date,
                    isAvailable = available
                )
            )
        }
        return result
    }

    fun parsePersonPage(html: String, base: String): Pair<String, List<MovieItem>> {
        val doc = Jsoup.parse(html, base)
        var name = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        if (name.isBlank()) {
            name = doc.title().substringBefore(" - ").trim()
        }
        return name to parseItems(html, base)
    }

    fun parsePersons(html: String, base: String): List<PersonLink> {
        val doc = Jsoup.parse(html)
        val result = ArrayList<PersonLink>()
        val seen = HashSet<String>()
        for (anchor in doc.select("a[href]")) {
            val href = anchor.attr("href")
            if (!href.contains("/person/")) continue
            val url = HdRezkaClient.absoluteUrl(href, base)
            if (!seen.add(url)) continue
            val name = anchor.text().trim()
            if (name.isBlank()) continue
            result.add(PersonLink(name, url))
            if (result.size >= 24) break
        }
        return result
    }

    fun parseContinue(html: String, base: String): List<SiteContinueItem> {
        val doc = Jsoup.parse(html, base)
        val result = ArrayList<SiteContinueItem>()
        for (row in doc.select("div.b-videosaves__list_item")) {
            val link = row.selectFirst("div.td.title a") ?: continue
            val href = link.attr("href")
            if (href.isBlank()) continue
            val pageUrl = HdRezkaClient.absoluteUrl(href, base)
            val info = row.selectFirst("div.td.info")?.text().orEmpty()
            val season = Regex("(\\d+)\\s*сезон").find(info)?.groupValues?.getOrNull(1)
            val episode = Regex("(\\d+)\\s*серия").find(info)?.groupValues?.getOrNull(1)
            val translator = Regex("\\(([^)]+)\\)").find(info)?.groupValues?.getOrNull(1)
            val year = Regex("(?:19|20)\\d{2}")
                .find(row.selectFirst("div.td.title small")?.text().orEmpty())
                ?.value
            val saveId = row.id().removePrefix("videosave-")
            if (saveId.isBlank()) continue
            val cover = link.attr("data-cover_url").takeIf { it.isNotBlank() }
                ?.let { HdRezkaClient.absoluteUrl(it, base) }
            val movieId = Regex("/(\\d+)-[^/]+\\.html").find(pageUrl)
                ?.groupValues?.getOrNull(1).orEmpty()
            result.add(
                SiteContinueItem(
                    id = saveId,
                    movieId = movieId,
                    title = link.text().trim(),
                    pageUrl = pageUrl,
                    posterUrl = cover,
                    season = season,
                    episode = episode,
                    translator = translator,
                    dateText = row.selectFirst("div.td.date")?.text()?.trim().orEmpty(),
                    hasNewEpisode = row.selectFirst(".new-episode") != null,
                    year = year
                )
            )
        }
        return result.distinctBy { it.id }
    }

    fun parseFavoriteCategories(html: String): List<Pair<String, String>> {
        val doc = Jsoup.parse(html)
        val result = ArrayList<Pair<String, String>>()
        for (link in doc.select(".b-favorites_content__cats_list_link")) {
            val href = link.attr("href")
            val id = Regex("/favorites/(\\d+)/").find(href)?.groupValues?.getOrNull(1)
                ?: continue
            if (result.any { it.first == id }) continue
            val name = link.selectFirst("span.name")?.text()?.trim()
                ?.takeIf { it.isNotBlank() } ?: "Закладки"
            result.add(id to name)
        }
        return result
    }

    fun parseFavorites(html: String, base: String): List<MovieItem> {
        val doc = Jsoup.parse(html, base)
        val scoped = doc.select(".b-favorites_content__holder .b-content__inline_item")
        val nodes = if (scoped.isNotEmpty()) scoped else doc.select("div.b-content__inline_item")
        return nodes.mapNotNull { parseItem(it, base) }.distinctBy { it.id }
    }

    fun parseItems(html: String, base: String): List<MovieItem> {
        val doc = Jsoup.parse(html, base)
        val result = ArrayList<MovieItem>()
        for (node in doc.select("div.b-content__inline_item")) {
            val item = parseItem(node, base) ?: continue
            result.add(item)
        }
        // Новый шаблон сайта (hdrezka.inc и др.): div.postItem.
        if (result.isEmpty()) {
            for (node in doc.select("div.postItem")) {
                val item = parsePostItem(node, base) ?: continue
                result.add(item)
            }
        }
        return result.distinctBy { it.id }
    }

    private fun parsePostItem(node: Element, base: String): MovieItem? {
        val link = node.selectFirst("h2.postTitle a")
            ?: node.selectFirst("a.pLink")
            ?: node.selectFirst("a")
            ?: return null
        val rawUrl = link.attr("href").trim()
        if (rawUrl.isBlank()) return null
        val pageUrl = HdRezkaClient.absoluteUrl(rawUrl, base)
        val title = link.attr("title").trim().ifBlank { link.text().trim() }
        if (title.isBlank()) return null
        val id = node.attr("data-id").ifBlank {
            idRegex.find(pageUrl)?.groupValues?.getOrNull(1).orEmpty()
        }
        if (id.isBlank()) return null
        val poster = node.selectFirst("img.poster")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            ?.let { HdRezkaClient.absoluteUrl(it, base) }
        val misc = node.selectFirst(".misc")?.text().orEmpty()
        val year = yearRegex.find(misc)?.value
        val kind = when {
            node.selectFirst(".cat.serial") != null -> "Сериал"
            node.selectFirst(".cat.mserial") != null -> "Мультсериал"
            node.selectFirst(".cat.film") != null -> "Фильм"
            node.selectFirst(".cat.mult") != null -> "Мультфильм"
            node.selectFirst(".cat.anime") != null -> {
                if (node.selectFirst(".serialInfo") != null) "Аниме-сериал"
                else "Аниме-фильм"
            }
            rawUrl.contains("/serialy/") -> "Сериал"
            rawUrl.contains("/multserial/") -> "Мультсериал"
            rawUrl.contains("/filmy/") -> "Фильм"
            rawUrl.contains("/multfilmy/") -> "Мультфильм"
            rawUrl.contains("/anime/") -> "Аниме-сериал"
            else -> null
        }
        return MovieItem(
            id = id,
            title = title,
            originalTitle = null,
            year = year,
            pageUrl = pageUrl,
            posterUrl = poster,
            kind = kind,
            tags = emptyList()
        )
    }

    private fun parseItem(node: Element, base: String): MovieItem? {
        val anchor = node.selectFirst("div.b-content__inline_item-link a")
            ?: node.selectFirst("a")
        val rawUrl = node.attr("data-url").ifBlank { anchor?.attr("href").orEmpty() }
        if (rawUrl.isBlank()) return null
        val pageUrl = HdRezkaClient.absoluteUrl(rawUrl, base)
        val cover = node.selectFirst("div.b-content__inline_item-cover img")
            ?: node.selectFirst("img")
        val title = anchor?.text()?.trim().orEmpty().ifBlank {
            cover?.attr("alt")?.trim().orEmpty()
        }
        if (title.isBlank()) return null

        val id = node.attr("data-id").ifBlank {
            idRegex.find(pageUrl)?.groupValues?.getOrNull(1).orEmpty()
        }
        if (id.isBlank()) return null

        val linkBlock = node.selectFirst("div.b-content__inline_item-link")
        val blockText = linkBlock?.text() ?: node.text()
        val year = yearRegex.find(blockText)?.value
        val poster = cover?.attr("src")?.takeIf { it.isNotBlank() }?.let {
            HdRezkaClient.absoluteUrl(it, base)
        }
        val kind = node.selectFirst("span.cat i.entity")?.text()?.trim()

        return MovieItem(
            id = id,
            title = title,
            originalTitle = null,
            year = year,
            pageUrl = pageUrl,
            posterUrl = poster,
            kind = kind,
            tags = emptyList()
        )
    }

    fun parseDetails(html: String, pageUrl: String, base: String): MovieDetails {
        val doc = Jsoup.parse(html, pageUrl)

        val title = (doc.selectFirst("div.b-post__title h1") ?: doc.selectFirst("h1"))
            ?.text()?.trim().orEmpty().ifBlank { "Без названия" }
        val originalTitle = doc.selectFirst("div.b-post__origtitle")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
        val overview = (doc.selectFirst("div.b-post__description_text")
            ?: doc.selectFirst("div.b-post__description"))
            ?.text()?.trim().orEmpty()

        var poster: String? = null
        for (selector in listOf(
            "img[itemprop=image]",
            "div.b-post__infotable_left img",
            "img.b-post__image",
            "div.b-post__description img"
        )) {
            val el = doc.selectFirst(selector) ?: continue
            val src = el.attr("src").ifBlank { el.attr("data-src") }
            if (src.isNotBlank()) {
                poster = HdRezkaClient.absoluteUrl(src, base)
                break
            }
        }

        val imdbRaw = doc.selectFirst("span.b-post__info_rates.imdb, div.b-post__info_rates.imdb")?.text()
        val kpRaw = doc.selectFirst("span.b-post__info_rates.kp, div.b-post__info_rates.kp")?.text()
        val imdb = imdbRaw?.let { Regex("\\d+(?:[.,]\\d+)?").find(it)?.value?.replace(',', '.') }
        val kp = kpRaw?.let { Regex("\\d+(?:[.,]\\d+)?").find(it)?.value?.replace(',', '.') }

        var year: String? = null
        var country: String? = null
        var genres: List<String> = emptyList()
        var duration: String? = null

        for (row in doc.select("table.b-post__info tr")) {
            val cells = row.select("td")
            val label = cells.firstOrNull()?.text()?.trim()?.lowercase() ?: continue
            val values = cells.lastOrNull()?.select("a")?.map { it.text().trim() }
                ?.filter { it.isNotBlank() } ?: emptyList()
            when {
                label.startsWith("дата выхода") || label.startsWith("год") -> {
                    year = values.firstOrNull() ?: yearRegex.find(row.text())?.value
                }
                label.startsWith("страна") -> country = values.firstOrNull()
                label.startsWith("жанр") -> genres = values
                label.startsWith("время") -> {
                    duration = Regex("\\d+\\s*мин").find(row.text())?.value
                }
            }
        }

        if (year == null) year = yearRegex.find(title)?.value
            ?: yearRegex.find(pageUrl)?.value

        val voices = parseVoices(doc)
        val seasons = parseSeasons(doc, base)
        val episodes = parseEpisodes(doc, seasonId = null)
            .groupBy { it.seasonId }
            .filterKeys { it.isNotBlank() }

        val movieId = doc.selectFirst("li[data-translator_id]")?.attr("data-id")
            ?.takeIf { it.isNotBlank() }
            ?: idRegex.find(pageUrl)?.groupValues?.getOrNull(1)
            ?: "0"

        val qualityOptions = embeddedStreams(html)?.let { raw ->
            parseQualityList(raw)
        } ?: emptyList()
        val maxQuality = qualityOptions.maxByOrNull { it.rank }?.label
        val qualities = qualityOptions
            .sortedByDescending { it.rank }
            .map { it.label }
            .distinct()

        val persons = parsePersons(html, base)
        val schedule = parseSchedule(html)
        val familyFriendly = Regex("\"isFamilyFriendly\"\\s*:\\s*(true|false)")
            .find(html)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: true

        return MovieDetails(
            id = movieId,
            title = title,
            originalTitle = originalTitle,
            overview = overview,
            posterUrl = poster,
            year = year,
            country = country,
            genres = genres,
            ratingImdb = imdb,
            ratingKp = kp,
            duration = duration,
            maxQuality = maxQuality,
            qualities = qualities,
            pageUrl = pageUrl,
            voiceActings = voices,
            seasons = seasons,
            episodesBySeason = episodes,
            persons = persons,
            schedule = schedule,
            isFamilyFriendly = familyFriendly
        )
    }

    private fun parseVoices(doc: Document): List<VoiceActing> {
        val result = ArrayList<VoiceActing>()
        val seen = HashSet<String>()
        for (li in doc.select("ul#translators-list li")) {
            val anchor = li.selectFirst("a[data-translator_id]") ?: li.selectFirst("a")
            val id = li.attr("data-translator_id").ifBlank {
                anchor?.attr("data-translator_id").orEmpty()
            }
            val name = (anchor?.text()?.trim().orEmpty().ifBlank { li.text().trim() })
                .ifBlank { anchor?.attr("title").orEmpty() }
            if (name.isBlank()) continue
            val key = id.ifBlank { name }
            if (!seen.add(key)) continue
            val classes = li.className() + " " + (anchor?.className() ?: "")
            val premium = classes.contains("b-prem") || classes.contains("premium")
            result.add(VoiceActing(id = id.ifBlank { "0" }, name = name, isPremium = premium))
        }
        return result
    }

    private fun parseSeasons(doc: Document, base: String): List<Season> {
        var tabs = doc.select("ul#simple-seasons-tabs a.b-simple_season__item")
        if (tabs.isEmpty()) tabs = doc.select("ul#simple-seasons-tabs li")
        val result = ArrayList<Season>()
        for (tab in tabs) {
            var id = tab.attr("data-tab_id")
            if (id.isBlank()) id = tab.attr("data-season_id")
            if (id.isBlank()) id = Regex("\\d+").find(tab.text())?.value.orEmpty()
            if (id.isBlank()) continue
            val title = tab.text().trim().ifBlank { "Сезон $id" }
            val href = tab.attr("href")
            result.add(
                Season(
                    id = id,
                    title = title,
                    pageUrl = href.takeIf { it.isNotBlank() }
                        ?.let { HdRezkaClient.absoluteUrl(it, base) }
                )
            )
        }
        return result
    }

    fun parseEpisodes(html: String, base: String, seasonId: String? = null): List<Episode> {
        return parseEpisodes(Jsoup.parse(html, base), seasonId)
    }

    private fun parseEpisodes(doc: Document, seasonId: String?): List<Episode> {
        val result = ArrayList<Episode>()
        val seen = HashSet<String>()
        for (node in doc.select("a.b-simple_episode__item, li.b-simple_episode__item")) {
            val id = node.attr("data-episode_id")
            if (id.isBlank()) continue
            val nodeSeason = node.attr("data-season_id")
            if (seasonId != null && nodeSeason.isNotBlank() && nodeSeason != seasonId) continue
            val key = "$nodeSeason-$id"
            if (!seen.add(key)) continue
            val title = node.text().trim().ifBlank { "Серия $id" }
            result.add(Episode(id = id, title = title, seasonId = nodeSeason))
        }
        return result
    }

    fun embeddedStreams(html: String): String? {
        val regex = Regex("\"streams\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = regex.find(html)?.groupValues?.getOrNull(1) ?: return null
        return try {
            org.json.JSONObject("{\"s\":\"$raw\"}").getString("s")
        } catch (_: Exception) {
            raw.replace("\\/", "/")
        }
    }

    fun parseQualityList(raw: String): List<QualityOption> {
        val regex = Regex("\\[([^\\]]*)\\]([^\\[]*)")
        val result = ArrayList<QualityOption>()
        for (match in regex.findAll(raw)) {
            val rawLabel = match.groupValues[1]
            val label = rawLabel.replace(Regex("<[^>]+>"), "").trim()
            val urls = match.groupValues[2]
                .split(" or ")
                .map { it.trim(' ', '\t', '\r', '\n', ',') }
                .filter { it.isNotBlank() }
            if (urls.isEmpty()) continue
            val lower = label.lowercase()
            val premium = rawLabel.contains("pjs-prem-quality") ||
                lower.contains("ultra") || lower.contains("2k") || lower.contains("4k")
            result.add(QualityOption(label = label, url = urls.first(), isPremium = premium))
        }
        return result
    }

    fun qualityRank(label: String): Int? {
        val lower = label.lowercase()
        if (lower.contains("8k")) return 4320
        if (lower.contains("4k") || lower.contains("uhd")) return 2160
        if (lower.contains("2k")) return 1440
        val digits = Regex("\\d{3,4}").find(label)?.value?.toIntOrNull() ?: return null
        return if (lower.contains("ultra")) digits + 5 else digits
    }

    fun chooseStream(options: List<QualityOption>, preferred: String): QualityOption? {
        if (options.isEmpty()) return null
        val sorted = options.sortedBy { it.rank }
        if (preferred == "auto") return sorted.last()
        val wanted = qualityRank(preferred) ?: return sorted.last()
        return sorted.firstOrNull { it.rank == wanted }
            ?: sorted.lastOrNull { it.rank < wanted }
            ?: sorted.firstOrNull()
    }
}
