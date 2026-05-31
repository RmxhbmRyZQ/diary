package com.secretdiary.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.GlobalErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    errorHandler: GlobalErrorHandler
) : ViewModel() {

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private val _timeSkew = MutableStateFlow<String?>(null)
    val timeSkew: StateFlow<String?> = _timeSkew.asStateFlow()

    private val _rateLimited = MutableStateFlow<Int?>(null)
    val rateLimited: StateFlow<Int?> = _rateLimited.asStateFlow()

    init {
        viewModelScope.launch {
            errorHandler.sessionExpired.collect {
                _sessionExpired.value = true
            }
        }
        viewModelScope.launch {
            errorHandler.timeSkew.collect { _timeSkew.value = it }
        }
        viewModelScope.launch {
            errorHandler.rateLimited.collect { _rateLimited.value = it }
        }
    }

    fun dismissSessionExpired() { _sessionExpired.value = false }
    fun dismissTimeSkew() { _timeSkew.value = null }
    fun dismissRateLimit() { _rateLimited.value = null }
}
