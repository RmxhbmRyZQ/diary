package com.secretdiary.app.ui.diarydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.domain.model.Diary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiaryDetailUiState(
    val diary: Diary? = null,
    val isLoading: Boolean = true,
    val deleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DiaryDetailViewModel @Inject constructor(
    private val repository: DiaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryDetailUiState())
    val uiState: StateFlow<DiaryDetailUiState> = _uiState.asStateFlow()

    fun loadDiary(entryId: String) {
        viewModelScope.launch {
            val diary = repository.getById(entryId)
            _uiState.value = _uiState.value.copy(diary = diary, isLoading = false)
        }
    }

    fun deleteDiary(entryId: String) {
        viewModelScope.launch {
            repository.deleteDiary(entryId)
            _uiState.value = _uiState.value.copy(deleted = true)
        }
    }

    fun toggleFavorite() {
        val diary = _uiState.value.diary ?: return
        viewModelScope.launch {
            val result = repository.updateMeta(
                id = diary.id, mood = diary.mood, weather = diary.weather,
                favorite = !diary.favorite, diaryDate = diary.diaryDate,
                serverVersion = diary.serverVersion
            )
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(diary = it) },
                onFailure = { _uiState.value = _uiState.value.copy(error = "内容已被其他设备修改，请刷新后重试") }
            )
        }
    }

    fun updateMood(mood: String) {
        val diary = _uiState.value.diary ?: return
        val newMood = if (diary.mood == mood) null else mood
        viewModelScope.launch {
            val result = repository.updateMeta(
                id = diary.id, mood = newMood, weather = diary.weather,
                favorite = diary.favorite, diaryDate = diary.diaryDate,
                serverVersion = diary.serverVersion
            )
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(diary = it) },
                onFailure = { _uiState.value = _uiState.value.copy(error = "内容已被其他设备修改，请刷新后重试") }
            )
        }
    }

    fun updateWeather(weather: String) {
        val diary = _uiState.value.diary ?: return
        val newWeather = if (diary.weather == weather) null else weather
        viewModelScope.launch {
            val result = repository.updateMeta(
                id = diary.id, mood = diary.mood, weather = newWeather,
                favorite = diary.favorite, diaryDate = diary.diaryDate,
                serverVersion = diary.serverVersion
            )
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(diary = it) },
                onFailure = { _uiState.value = _uiState.value.copy(error = "内容已被其他设备修改，请刷新后重试") }
            )
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
