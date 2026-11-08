package com.hdrezka.tv.data

data class StreamingService(
    val id: String,
    val title: String,
    val url: String,
    val count: Int
)

/**
 * Список стриминговых сервисов берётся из подборок hdrezka.inc.
 * Никаких зашитых URL для конкретных платформ: только распознавание
 * названий и предпочтительный порядок карточек.
 */
object StreamingServices {
    private const val INC_BASE = "https://hdrezka.inc"

    // Порядок карточек. Сами ссылки/названия/количество приходят с сайта.
    private val preferredOrder = listOf(
        "netflix", "hbo", "amazon", "disney", "appletv",
        "hbomax", "hulu", "peacock", "premier", "start", "okko", "wink",
        "moretv", "primevideo", "paramountplus", "showtime", "starz", "amcplus"
    )

    private val known = preferredOrder.toSet()

    private val displayNames = mapOf(
        "disney" to "Disney+",
        "appletv" to "Apple TV+",
        "hbomax" to "HBO Max",
        "moretv" to "more.tv",
        "primevideo" to "Prime Video",
        "paramountplus" to "Paramount+",
        "amcplus" to "AMC+"
    )

    @Volatile
    var cache: List<StreamingService>? = null
        private set

    fun fallback(): List<StreamingService> = listOf(
        StreamingService(
            "netflix", "Netflix",
            "$INC_BASE/xfsearch/podborki/Netflix/", 0
        ),
        StreamingService(
            "hbo", "HBO",
            "$INC_BASE/xfsearch/podborki/HBO/", 0
        ),
        StreamingService(
            "amazon", "Amazon",
            "$INC_BASE/xfsearch/podborki/Amazon/", 0
        ),
        StreamingService(
            "disney", "Disney+",
            "$INC_BASE/xfsearch/podborki/Disney/", 0
        ),
        StreamingService(
            "appletv", "Apple TV+",
            "$INC_BASE/xfsearch/podborki/Apple%20TV%2B/", 0
        )
    )

    suspend fun load(): List<StreamingService> {
        cache?.let { return it }
        val html = HdRezkaClient.get("$INC_BASE/collections.html")
        val collections = withContextDefault { Parser.parseIncCollections(html, INC_BASE) }
        val byKey = LinkedHashMap<String, Collection>()
        for (collection in collections) {
            val key = canonicalKey(collection.title) ?: continue
            if (key !in known) continue
            val existing = byKey[key]
            // На сайте бывают дубли (Netflix и netflix). Берём самый полный.
            if (existing == null || collection.count > existing.count) {
                byKey[key] = collection
            }
        }
        if (byKey.isEmpty()) return fallback()
        val list = byKey.map { (key, collection) ->
            StreamingService(
                id = key,
                title = displayNames[key] ?: collection.title,
                url = collection.url,
                count = collection.count
            )
        }.sortedWith(
            compareBy<StreamingService> {
                preferredOrder.indexOf(it.id).let { index ->
                    if (index < 0) Int.MAX_VALUE else index
                }
            }.thenByDescending { it.count }
        )
        cache = list
        return list
    }

    private suspend fun <T> withContextDefault(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { block() }

    private fun canonicalKey(title: String): String? {
        val norm = normalize(title)
        if (norm.isBlank()) return null
        return when {
            norm == "disney" || norm == "disneyplus" || norm.startsWith("disney") -> "disney"
            norm == "appletv" || norm == "appletvplus" -> "appletv"
            norm == "hbomax" -> "hbomax"
            norm == "primevideo" || norm == "amazonprime" -> "primevideo"
            else -> norm
        }
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace("ё", "е")
            .filter { it.isLetterOrDigit() }
}
