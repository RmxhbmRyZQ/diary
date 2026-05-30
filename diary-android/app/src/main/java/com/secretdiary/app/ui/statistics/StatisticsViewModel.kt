package com.secretdiary.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.domain.model.Diary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class StatisticsUiState(
    val totalDays: Int = 0,
    val totalWords: Int = 0,
    val consecutiveDays: Int = 0,
    val moodDistribution: Map<String, Int> = emptyMap(),
    val weatherDistribution: Map<String, Int> = emptyMap(),
    val monthlyCounts: List<Pair<String, Int>> = emptyList(),
    val heatmapData: Map<String, Int> = emptyMap(), // yyyy-MM-dd → count
    val isLoading: Boolean = true
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: DiaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllDiaries().collect { diaries ->
                _uiState.value = computeStats(diaries)
            }
        }
    }

    private fun computeStats(diaries: List<Diary>): StatisticsUiState {
        val moodMap = mutableMapOf<String, Int>()
        val weatherMap = mutableMapOf<String, Int>()
        val monthMap = linkedMapOf<String, Int>()
        val heatmapMap = mutableMapOf<String, Int>()
        var words = 0

        val sorted = diaries.sortedBy { it.diaryDate }
        for (d in sorted) {
            d.mood?.let { moodMap[it] = (moodMap[it] ?: 0) + 1 }
            d.weather?.let { weatherMap[it] = (weatherMap[it] ?: 0) + 1 }
            monthMap[d.diaryDate.take(7)] = (monthMap[d.diaryDate.take(7)] ?: 0) + 1
            heatmapMap[d.diaryDate] = (heatmapMap[d.diaryDate] ?: 0) + 1
            words += d.content.length
        }

        val consecutiveDays = computeConsecutiveDays(sorted.map { it.diaryDate }.toSet())

        return StatisticsUiState(
            totalDays = diaries.size,
            totalWords = words,
            consecutiveDays = consecutiveDays,
            moodDistribution = moodMap,
            weatherDistribution = weatherMap,
            monthlyCounts = monthMap.toList(),
            heatmapData = heatmapMap,
            isLoading = false
        )
    }

    private fun computeConsecutiveDays(dates: Set<String>): Int {
        if (dates.isEmpty()) return 0
        var maxStreak = 0
        var currentStreak = 0
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dateList = dates.map { LocalDate.parse(it, formatter) }.sorted()
        var prev: LocalDate? = null
        for (date in dateList) {
            if (prev == null || date.minusDays(1) == prev) {
                currentStreak++
            } else {
                currentStreak = 1
            }
            if (currentStreak > maxStreak) maxStreak = currentStreak
            prev = date
        }
        return maxStreak
    }
}
