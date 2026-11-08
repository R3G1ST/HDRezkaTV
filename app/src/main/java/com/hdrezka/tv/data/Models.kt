package com.hdrezka.tv.data

data class MovieItem(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val year: String?,
    val pageUrl: String,
    val posterUrl: String?,
    val kind: String? = null,
    val tags: List<String> = emptyList(),
    /** Тайтл открыт из раздела «В ожидании». */
    val upcoming: Boolean = false
) {
    val subtitle: String
        get() = listOfNotNull(year, originalTitle)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}

data class VoiceActing(
    val id: String,
    val name: String,
    val isPremium: Boolean
)

data class Season(
    val id: String,
    val title: String,
    val pageUrl: String?
)

data class Episode(
    val id: String,
    val title: String,
    val seasonId: String
)

data class MovieDetails(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val overview: String,
    val posterUrl: String?,
    val year: String?,
    val country: String?,
    val genres: List<String>,
    val ratingImdb: String?,
    val ratingKp: String?,
    val duration: String?,
    val maxQuality: String?,
    val qualities: List<String> = emptyList(),
    val pageUrl: String,
    val voiceActings: List<VoiceActing>,
    val seasons: List<Season>,
    val episodesBySeason: Map<String, List<Episode>>,
    val persons: List<PersonLink> = emptyList(),
    val schedule: List<ScheduleItem> = emptyList(),
    val isFamilyFriendly: Boolean = true
) {
    val isSeries: Boolean get() = seasons.isNotEmpty()

    fun episodesFor(seasonId: String): List<Episode> =
        episodesBySeason[seasonId] ?: emptyList()
}

data class QualityOption(
    val label: String,
    val url: String,
    val isPremium: Boolean
) {
    val rank: Int get() = Parser.qualityRank(label) ?: 0
}

data class StreamInfo(
    val url: String,
    val quality: String?,
    val subtitleUrl: String?,
    val isHls: Boolean,
    val qualities: List<QualityOption> = emptyList()
)


data class AccountInfo(
    val id: String,
    val name: String,
    val isPremium: Boolean
)


data class SiteContinueItem(
    val id: String,
    val movieId: String,
    val title: String,
    val pageUrl: String,
    val posterUrl: String?,
    val season: String?,
    val episode: String?,
    val translator: String?,
    val dateText: String,
    val hasNewEpisode: Boolean,
    val year: String? = null
)


data class Collection(
    val id: String,
    val title: String,
    val url: String,
    val coverUrl: String?,
    val count: Int
)

data class PersonLink(
    val name: String,
    val url: String
)


data class ScheduleItem(
    val label: String,
    val title: String,
    val originalTitle: String?,
    val date: String,
    val isAvailable: Boolean
)


data class TrailerInfo(
    val title: String,
    val iframeUrl: String?,
    val pageUrl: String?
)
