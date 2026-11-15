package com.apkrepo.web.state

/**
 * Generic loading state used by every screen that fetches data.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
    data class Failed(val message: String) : UiState<Nothing>
}
