package com.apkrepo.web.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.AdminSession
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.routing.rememberScreen
import com.apkrepo.web.ui.admin.AdminAppListScreen
import com.apkrepo.web.ui.admin.AdminJobsScreen
import com.apkrepo.web.ui.admin.AdminLoginScreen
import com.apkrepo.web.ui.admin.AdminRepoStatusScreen
import com.apkrepo.web.ui.admin.AppManageScreen
import com.apkrepo.web.ui.admin.DashboardScreen
import com.apkrepo.web.ui.admin.UploadScreen
import com.apkrepo.web.ui.catalog.CatalogScreen
import com.apkrepo.web.ui.detail.AppDetailScreen
import com.apkrepo.web.ui.repo.RepoOnboardingScreen

/**
 * Root composable: top navigation plus the current screen (hash routing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val screen = rememberScreen()
    val isAdminArea = screen is Screen.AdminLogin || screen is Screen.AdminDashboard ||
        screen is Screen.AdminUpload || screen is Screen.AdminAppManage ||
        screen is Screen.AdminAppList || screen is Screen.AdminJobs ||
        screen is Screen.AdminRepoStatus

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { navigate(Screen.Catalog) }) {
                        Text(
                            "APK Repo",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { navigate(Screen.RepoOnboarding) }) { Text("Repository") }
                    if (AdminSession.isSignedIn || isAdminArea) {
                        TextButton(onClick = {
                            if (AdminSession.isSignedIn) navigate(Screen.AdminDashboard) else navigate(Screen.AdminLogin)
                        }) { Text("Admin") }
                    } else {
                        TextButton(onClick = { navigate(Screen.AdminLogin) }) { Text("Admin") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = screen) {
                Screen.Catalog -> CatalogScreen()
                is Screen.AppDetail -> AppDetailScreen(s.packageName)
                Screen.RepoOnboarding -> RepoOnboardingScreen()
                Screen.AdminLogin -> AdminLoginScreen()
                Screen.AdminDashboard -> DashboardScreen()
                Screen.AdminUpload -> UploadScreen()
                is Screen.AdminAppManage -> AppManageScreen(s.packageName)
                Screen.AdminAppList -> AdminAppListScreen()
                Screen.AdminJobs -> AdminJobsScreen()
                Screen.AdminRepoStatus -> AdminRepoStatusScreen()
            }
        }
    }
}
