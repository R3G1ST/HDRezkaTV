package com.hdrezka.tv.data

enum class CatalogSection(
    val title: String,
    val path: String
) {
    FRESH("Новинки", "new"),
    FILMS("Фильмы", "films"),
    SERIES("Сериалы", "series"),
    CARTOONS("Мультфильмы", "cartoons"),
    ANIME("Аниме", "animation"),
    DOCUMENTARY("Документальные", "films/documentary")
}

enum class CatalogSort(val title: String, val filter: String) {
    FRESH("Последние", "last"),
    POPULAR("Популярные", "popular"),
    WATCHING("Сейчас смотрят", "watching"),
    SOON("В ожидании", "soon")
}

val catalogGenres = listOf(
    "action" to "Боевики",
    "comedy" to "Комедии",
    "drama" to "Драмы",
    "thriller" to "Триллеры",
    "horror" to "Ужасы",
    "fantasy" to "Фэнтези",
    "fiction" to "Фантастика",
    "adventure" to "Приключения",
    "detective" to "Детективы",
    "family" to "Семейные",
    "military" to "Военные",
    "biographical" to "Биографические",
    "documentary" to "Документальные",
    "russian" to "Русские",
    "foreign" to "Зарубежные"
)

val catalogYears = (2026 downTo 1990).map { it.toString() }
