package com.hdrezka.tv.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdrezka.tv.data.CatalogSection
import com.hdrezka.tv.data.CatalogSort
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.MovieItem
import kotlinx.coroutines.launch

data class CatalogRow(
    val section: CatalogSection,
    val title: String,
    val items: List<MovieItem>,
    val page: Int = 1,
    val ended: Boolean = false,
    val loadingMore: Boolean = false
)

data class CatalogUiState(
    val loading: Boolean = true,
    val rows: List<CatalogRow> = emptyList(),
    val error: String? = null
)

class CatalogViewModel : ViewModel() {
    var state by mutableStateOf(CatalogUiState())
        private set

    var genre by mutableStateOf<String?>(null)
        private set
    var year by mutableStateOf<String?>(null)
        private set
    var sort by mutableStateOf(CatalogSort.FRESH)
        private set

    private val sections = listOf(
        CatalogSection.FRESH,
        CatalogSection.FILMS,
        CatalogSection.SERIES,
        CatalogSection.CARTOONS,
        CatalogSection.ANIME,
        CatalogSection.DOCUMENTARY
    )

    init {
        refresh()
    }

    fun refresh() {
        state = CatalogUiState(loading = true)
        viewModelScope.launch {
            val rows = ArrayList<CatalogRow>()
            try {
                for (section in sections) {
                    Log.d("HDRezkaTV", "Загрузка раздела ${section.title}")
                    val items = HdRezkaClient.catalog(
                        section = section,
                        page = 1,
                        genre = genre,
                        year = year,
                        sort = sort
                    )
                    if (items.isNotEmpty()) {
                        rows.add(CatalogRow(section, section.title, items))
                        // Показываем первую полку сразу, не ждём все разделы.
                        state = CatalogUiState(
                            loading = false,
                            rows = rows.toList(),
                            error = null
                        )
                    }
                }
                state = CatalogUiState(
                    loading = false,
                    rows = rows,
                    error = if (rows.isEmpty()) "Сайт не вернул ни одного тайтла" else null
                )
            } catch (error: Exception) {
                Log.e("HDRezkaTV", "Каталог не загрузился", error)
                state = CatalogUiState(
                    loading = false,
                    rows = rows,
                    error = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    fun applyFilters(genre: String?, year: String?, sort: CatalogSort) {
        this.genre = genre
        this.year = year
        this.sort = sort
        refresh()
    }

    fun resetFilters() {
        genre = null
        year = null
        sort = CatalogSort.FRESH
        refresh()
    }

    fun loadMore(section: CatalogSection) {
        val index = state.rows.indexOfFirst { it.section == section }
        if (index < 0) return
        val row = state.rows[index]
        if (row.ended || row.loadingMore) return

        val updated = state.rows.toMutableList()
        updated[index] = row.copy(loadingMore = true)
        state = state.copy(rows = updated)

        viewModelScope.launch {
            try {
                val next = HdRezkaClient.catalog(
                    section = section,
                    page = row.page + 1,
                    genre = genre,
                    year = year,
                    sort = sort
                )
                val current = state.rows.getOrNull(index) ?: return@launch
                val items = current.items + next
                val newRows = state.rows.toMutableList()
                newRows[index] = current.copy(
                    items = items,
                    page = current.page + 1,
                    ended = next.isEmpty(),
                    loadingMore = false
                )
                state = state.copy(rows = newRows)
            } catch (_: Exception) {
                val current = state.rows.getOrNull(index) ?: return@launch
                val newRows = state.rows.toMutableList()
                newRows[index] = current.copy(loadingMore = false, ended = true)
                state = state.copy(rows = newRows)
            }
        }
    }
}
