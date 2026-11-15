package com.apkrepo.web.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.AdminSession
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.api.UnauthorizedException
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane

/**
 * Admin dashboard: catalog totals, pending jobs and entry points into the
 * management pages.
 */
@Composable
fun DashboardScreen() {
    var reloadKey by remember { mutableStateOf(0) }
    val state: UiState<com.apkrepo.web.api.AdminStatsDto> by produceState(
        UiState.Loading as UiState<com.apkrepo.web.api.AdminStatsDto>,
        reloadKey,
    ) {
        value = try {
            UiState.Ready(ApiClient.adminStats())
        } catch (e: UnauthorizedException) {
            navigate(Screen.AdminLogin)
            UiState.Failed("signed out")
        } catch (e: Throwable) {
            UiState.Failed(e.message ?: "failed to load stats")
        }
    }

    when (val s = state) {
        is UiState.Loading -> LoadingPane(modifier = Modifier.fillMaxSize())
        is UiState.Failed -> ErrorPane(s.message, retry = { reloadKey++ }, modifier = Modifier.fillMaxSize())
        is UiState.Ready<com.apkrepo.web.api.AdminStatsDto> -> {
            val stats = s.value
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${stats.appCount} apps · ${stats.releaseCount} releases", style = MaterialTheme.typography.titleLarge)
                        if (stats.pendingJobCount > 0) {
                            Text(
                                "${stats.pendingJobCount} publication job(s) pending",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text("No pending publication jobs", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navigate(Screen.AdminUpload) }) { Text("Upload APK") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navigate(Screen.AdminAppList) }) { Text("Manage apps") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navigate(Screen.AdminJobs) }) { Text("Publication jobs") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navigate(Screen.AdminRepoStatus) }) { Text("Repository status") }
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = {
                    AdminSession.logout()
                    navigate(Screen.Catalog)
                }) { Text("Sign out") }
            }
        }
    }
}
