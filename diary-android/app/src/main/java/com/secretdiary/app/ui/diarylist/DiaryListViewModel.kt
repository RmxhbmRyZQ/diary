package com.secretdiary.app.ui.diarylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.domain.model.Diary
import com.secretdiary.app.sync.SyncManager
import com.secretdiary.app.sync.SyncState
import com.secretdiary.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiaryListUiState(
    val diaries: List<Diary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val syncState: SyncState = SyncState.Idle,
    val filterMood: String? = null,
    val filterWeather: String? = null,
    val filterFavorites: Boolean = false,
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryListViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryListUiState())
    val uiState: StateFlow<DiaryListUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init {
        observeDiaries(repository.observeAllDiaries())
        observeSyncState()
        // 同步由 LoginViewModel 或手动刷新触发，避免重复调用
    }

    private fun observeDiaries(flow: Flow<List<Diary>>) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            flow.collect { diaries ->
                _uiState.value = _uiState.value.copy(diaries = diaries, isLoading = false)
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.value = _uiState.value.copy(syncState = state, isRefreshing = state is SyncState.InProgress)
            }
        }
    }

    fun refresh() { syncManager.performSync() }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(filterMood = null, filterWeather = null, filterFavorites = false, isLoading = true)
        observeDiaries(repository.observeAllDiaries())
    }

    fun toggleFavorite(entryId: String) {
        viewModelScope.launch {
            val diary = repository.getById(entryId) ?: return@launch
            repository.updateMeta(
                id = diary.id, mood = diary.mood, weather = diary.weather,
                favorite = !diary.favorite, diaryDate = diary.diaryDate,
                serverVersion = diary.serverVersion
            )
        }
    }

    fun setFilterMood(mood: String?) {
        _uiState.value = _uiState.value.copy(filterMood = mood, isLoading = true)
        val flow = if (mood != null) repository.observeByMood(mood)
        else repository.observeAllDiaries()
        observeDiaries(flow)
    }

    fun setFilterWeather(weather: String?) {
        _uiState.value = _uiState.value.copy(filterWeather = weather, isLoading = true)
        val flow = if (weather != null) repository.observeByWeather(weather)
        else repository.observeAllDiaries()
        observeDiaries(flow)
    }

    fun setFilterFavorites(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(filterFavorites = enabled, isLoading = true)
        val flow = if (enabled) repository.observeFavorites()
        else repository.observeAllDiaries()
        observeDiaries(flow)
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        val flow = if (query.isBlank()) repository.observeAllDiaries()
        else repository.search(query)
        observeDiaries(flow)
    }

    /** 日记按年月分组 */
    fun groupByMonth(diaries: List<Diary>): Map<String, List<Diary>> =
        diaries.groupBy { it.diaryDate.take(7) } // yyyy-MM
}
