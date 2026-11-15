package com.apkrepo.web.routing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window

/**
 * Hash-based routing (never sent to the server, so no server-side SPA
 * fallback is needed): "" -> catalog, "app/{pkg}" -> detail, "repo" ->
 * onboarding, "admin..." -> admin area.
 */
sealed interface Screen {
    data object Catalog : Screen
    data class AppDetail(val packageName: String) : Screen
    data object RepoOnboarding : Screen
    data object AdminLogin : Screen
    data object AdminDashboard : Screen
    data object AdminUpload : Screen
    data class AdminAppManage(val packageName: String) : Screen
    data object AdminAppList : Screen
    data object AdminJobs : Screen
    data object AdminRepoStatus : Screen
}

fun parseHash(hash: String): Screen {
    val path = hash.removePrefix("#").trim('/').split('/')
        .filter { it.isNotEmpty() }
    return when {
        path.isEmpty() -> Screen.Catalog
        path[0] == "app" && path.size >= 2 -> Screen.AppDetail(path.subList(1, 2).joinToString("/"))
        path[0] == "repo" -> Screen.RepoOnboarding
        path[0] == "admin" -> when (path.getOrNull(1)) {
            null -> Screen.AdminDashboard
            "login" -> Screen.AdminLogin
            "upload" -> Screen.AdminUpload
            "apps" -> when {
                // #/admin/apps/{pkg} — package name is the 3rd segment.
                path.size >= 3 -> Screen.AdminAppManage(path[2])
                // #/admin/apps (or a malformed/trailing-slash variant) —
                // the list screen. Must not fall through to subList() on a
                // 2-element path: that used to throw IndexOutOfBoundsException
                // inside onhashchange, leaving the UI stuck on the old screen
                // (the "Manage apps" button appeared to do nothing).
                else -> Screen.AdminAppList
            }
            "jobs" -> Screen.AdminJobs
            "repository" -> Screen.AdminRepoStatus
            else -> Screen.AdminDashboard
        }
        else -> Screen.Catalog
    }
}

fun toHash(screen: Screen): String = when (screen) {
    Screen.Catalog -> "#/"
    is Screen.AppDetail -> "#/app/${screen.packageName}"
    Screen.RepoOnboarding -> "#/repo"
    Screen.AdminLogin -> "#/admin/login"
    Screen.AdminDashboard -> "#/admin"
    Screen.AdminUpload -> "#/admin/upload"
    Screen.AdminAppList -> "#/admin/apps"
    is Screen.AdminAppManage -> "#/admin/apps/${screen.packageName}"
    Screen.AdminJobs -> "#/admin/jobs"
    Screen.AdminRepoStatus -> "#/admin/repository"
}

/** Navigates by changing the location hash (no page reload). */
fun navigate(screen: Screen) {
    window.location.hash = toHash(screen)
}

/** Observes the current screen; recomposes on hash changes and back/forward. */
@Composable
fun rememberScreen(): Screen {
    val screen = remember { mutableStateOf(parseHash(window.location.hash)) }
    DisposableEffect(Unit) {
        window.onhashchange = { _ ->
            screen.value = parseHash(window.location.hash)
        }
        onDispose { window.onhashchange = null }
    }
    return screen.value
}
