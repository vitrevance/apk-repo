package com.apkrepo.web.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.api.UnauthorizedException
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.CopyField
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane
import kotlinx.coroutines.launch

/**
 * Repository status page: F-Droid repository identity and a manual rebuild
 * trigger.
 */
@Composable
fun AdminRepoStatusScreen() {
    var reloadKey by remember { mutableStateOf(0) }
    var rebuilding by remember { mutableStateOf(false) }
    var rebuildNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val state: UiState<com.apkrepo.web.api.RepositoryInfoDto> by produceState(
        UiState.Loading as UiState<com.apkrepo.web.api.RepositoryInfoDto>,
        reloadKey,
    ) {
        value = try {
            UiState.Ready(ApiClient.repositoryInfo())
        } catch (e: Throwable) {
            UiState.Failed(e.message ?: "failed to load repository info")
        }
    }

    when (val s = state) {
        is UiState.Loading -> LoadingPane(modifier = Modifier.fillMaxSize())
        is UiState.Failed -> ErrorPane(s.message, retry = { reloadKey++ }, modifier = Modifier.fillMaxSize())
        is UiState.Ready<com.apkrepo.web.api.RepositoryInfoDto> -> {
            val info = s.value
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text("Repository status", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Name: ${info.name}")
                        CopyField(label = "Repository URL", value = info.url)
                        Spacer(modifier = Modifier.height(8.dp))
                        CopyField(label = "Fingerprint", value = info.fingerprint ?: "not yet generated")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Manual rebuild", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Regenerates and re-signs the entire F-Droid repository from the database. " +
                                "Normally unnecessary — uploads, edits and unpublishes queue jobs automatically.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            enabled = !rebuilding,
                            onClick = {
                                rebuilding = true
                                rebuildNote = null
                                scope.launch {
                                    try {
                                        val result = ApiClient.rebuildRepository()
                                        rebuildNote = if (result.enqueued) {
                                            "Rebuild queued (${result.job?.id?.take(8)}…)."
                                        } else {
                                            "A job was already queued — nothing new to do."
                                        }
                                    } catch (e: UnauthorizedException) {
                                        navigate(Screen.AdminLogin)
                                    } catch (e: Throwable) {
                                        rebuildNote = "Rebuild failed: ${e.message}"
                                    } finally {
                                        rebuilding = false
                                    }
                                }
                            },
                        ) { Text(if (rebuilding) "Queuing…" else "Rebuild repository") }
                        rebuildNote?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.TextButton(onClick = { navigate(Screen.AdminJobs) }) {
                            Text("Watch jobs →")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
