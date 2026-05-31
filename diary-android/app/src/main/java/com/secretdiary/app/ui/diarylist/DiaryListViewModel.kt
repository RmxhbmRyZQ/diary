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
    val searchQuery: String = "",
    // 日期级联筛选
    val selectedYear: Int? = null,
    val selectedMonth: Int? = null,
    val selectedDay: Int? = null,
    val availableYears: List<Int> = emptyList(),
    val availableMonths: List<Int> = emptyList(),
    val availableDays: List<Int> = emptyList()
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
    private val filterTrigger = MutableStateFlow(0)  // 日期筛选变化时递增，强制重新计算

    init {
        observeDiaries(repository.observeAllDiaries())
        observeSyncState()
    }

    private fun observeDiaries(flow: Flow<List<Diary>>) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            flow.combine(filterTrigger) { base, _ -> base }
                .collect { base ->
                    val state = _uiState.value
                    val filtered = applyFilters(base, state)
                    val years = base.map { it.diaryDate.take(4).toIntOrNull() }.filterNotNull().distinct().sortedDescending()
                    val months = if (state.selectedYear != null)
                        base.filter { it.diaryDate.startsWith("${state.selectedYear}-") }
                            .map { it.diaryDate.substring(5, 7).toIntOrNull() }.filterNotNull().distinct().sortedDescending()
                    else emptyList()
                    val days = if (state.selectedYear != null && state.selectedMonth != null)
                        base.filter { it.diaryDate.startsWith("${state.selectedYear}-${state.selectedMonth.toString().padStart(2, '0')}-") }
                            .map { it.diaryDate.takeLast(2).toIntOrNull() }.filterNotNull().distinct().sortedDescending()
                    else emptyList()
                    _uiState.value = state.copy(
                        diaries = filtered,
                        isLoading = false,
                        availableYears = years,
                        availableMonths = months,
                        availableDays = days
                    )
                }
        }
    }

    private fun triggerRecompute() {
        filterTrigger.value = filterTrigger.value + 1
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.value = _uiState.value.copy(syncState = state, isRefreshing = state is SyncState.InProgress)
            }
        }
    }

    fun refresh() { syncManager.performSync() }

    // ---------- 筛选 ----------

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            filterMood = null, filterWeather = null, filterFavorites = false,
            selectedYear = null, selectedMonth = null, selectedDay = null,
            availableMonths = emptyList(), availableDays = emptyList()
        )
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

    // ---------- 日期级联筛选 ----------

    fun setFilterYear(year: Int?) {
        val state = _uiState.value
        if (state.selectedYear == year) {
            _uiState.value = state.copy(selectedYear = null, selectedMonth = null, selectedDay = null,
                availableMonths = emptyList(), availableDays = emptyList())
        } else {
            _uiState.value = state.copy(selectedYear = year, selectedMonth = null, selectedDay = null, availableDays = emptyList())
        }
        triggerRecompute()
    }

    fun setFilterMonth(month: Int?) {
        val state = _uiState.value
        if (state.selectedMonth == month) {
            _uiState.value = state.copy(selectedMonth = null, selectedDay = null, availableDays = emptyList())
        } else {
            _uiState.value = state.copy(selectedMonth = month, selectedDay = null, availableDays = emptyList())
        }
        triggerRecompute()
    }

    fun setFilterDay(day: Int?) {
        val state = _uiState.value
        _uiState.value = state.copy(selectedDay = if (state.selectedDay == day) null else day)
        triggerRecompute()
    }

    /** 日记按年月分组 */
    fun groupByMonth(diaries: List<Diary>): Map<String, List<Diary>> =
        diaries.groupBy { it.diaryDate.take(7) } // yyyy-MM

    // -------------------- 私有 --------------------

    private fun applyFilters(base: List<Diary>, state: DiaryListUiState): List<Diary> {
        var result = base
        if (state.selectedYear != null) {
            result = result.filter { it.diaryDate.startsWith("${state.selectedYear}-") }
        }
        if (state.selectedMonth != null) {
            result = result.filter { it.diaryDate.substring(5, 7).toIntOrNull() == state.selectedMonth }
        }
        if (state.selectedDay != null) {
            result = result.filter { it.diaryDate.takeLast(2).toIntOrNull() == state.selectedDay }
        }
        return result
    }
}
