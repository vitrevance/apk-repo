package com.apkrepo.web.api

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.window

/**
 * Admin session using the backend's HTTP Basic authentication. The
 * credentials are kept in sessionStorage (cleared when the tab closes or
 * on logout) and attached to every admin request as an Authorization
 * header by [ApiClient].
 */
object AdminSession {

    private const val STORAGE_KEY = "apkrepo-admin"

    /** The value of the Authorization header ("Basic ...") or null when signed out. */
    val credentials: MutableState<String?> = mutableStateOf(restore())

    val isSignedIn: Boolean get() = credentials.value != null

    fun login(username: String, password: String) {
        val header = "Basic " + window.btoa("$username:$password")
        window.sessionStorage.setItem(STORAGE_KEY, header)
        credentials.value = header
    }

    fun logout() {
        window.sessionStorage.removeItem(STORAGE_KEY)
        credentials.value = null
    }

    fun authHeader(): String? = credentials.value

    private fun restore(): String? =
        try {
            window.sessionStorage.getItem(STORAGE_KEY)
        } catch (e: Throwable) {
            null
        }
}
