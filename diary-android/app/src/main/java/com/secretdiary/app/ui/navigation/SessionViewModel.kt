package com.secretdiary.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.GlobalErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    errorHandler: GlobalErrorHandler
) : ViewModel() {

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        viewModelScope.launch {
            errorHandler.sessionExpired.collect {
                _sessionExpired.value = true
            }
        }
    }

    fun dismissSessionExpired() {
        _sessionExpired.value = false
    }
}
