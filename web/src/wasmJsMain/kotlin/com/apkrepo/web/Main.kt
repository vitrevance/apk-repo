@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.apkrepo.web

import androidx.compose.ui.window.ComposeViewport
import com.apkrepo.web.theme.RepoTheme
import com.apkrepo.web.ui.App
import kotlinx.browser.document

/**
 * Entry point of the APK Repo web application (Kotlin/Wasm + Compose
 * Multiplatform). ComposeViewport attaches the UI to the document body;
 * the app itself is assembled in ui/App.kt with hash-based routing.
 */
fun main() {
    ComposeViewport(document.body!!) {
        RepoTheme {
            App()
        }
    }
}
