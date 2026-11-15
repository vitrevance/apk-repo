package com.apkrepo.web.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.api.PageResponseDto
import com.apkrepo.web.api.PublicationJobDto
import com.apkrepo.web.api.UnauthorizedException
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane
import com.apkrepo.web.ui.components.StatusChip
import com.apkrepo.web.ui.components.formatInstant
import kotlinx.coroutines.delay

/**
 * Publication job monitor. While any job is PENDING or RUNNING the list
 * auto-refreshes every three seconds.
 */
@Composable
fun AdminJobsScreen() {
    var reloadKey by remember { mutableStateOf(0) }
    val state: UiState<PageResponseDto<PublicationJobDto>> by produceState(
        UiState.Loading as UiState<PageResponseDto<PublicationJobDto>>,
        reloadKey,
    ) {
        value = try {
            UiState.Ready(ApiClient.listJobs())
        } catch (e: UnauthorizedException) {
            navigate(Screen.AdminLogin)
            UiState.Failed("signed out")
        } catch (e: Throwable) {
            UiState.Failed(e.message ?: "failed to load jobs")
        }
    }

    val hasActive = (state as? UiState.Ready)?.value?.content
        ?.any { it.state == "PENDING" || it.state == "RUNNING" } == true
    if (hasActive) {
        LaunchedEffect(reloadKey) {
            delay(3000)
            reloadKey++
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Publication jobs", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        when (val s = state) {
            is UiState.Loading -> LoadingPane(modifier = Modifier.fillMaxSize())
            is UiState.Failed -> ErrorPane(s.message, retry = { reloadKey++ }, modifier = Modifier.fillMaxSize())
            is UiState.Ready<PageResponseDto<PublicationJobDto>> -> {
                val jobs = s.value.content
                if (jobs.isEmpty()) {
                    Text("No publication jobs yet.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    LazyColumn {
                        items(jobs, key = { it.id }) { job ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${job.action} · ${job.id.take(8)}…",
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                "created ${formatInstant(job.createdAt)} · finished ${formatInstant(job.finishedAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        StatusChip(job.state)
                                        if (job.retryCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("retry ${job.retryCount}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    job.generationId?.let { Text("generation ${it.take(24)}", style = MaterialTheme.typography.bodySmall) }
                                    job.errorMessage?.let {
                                        Text(
                                            "Error: ${it.take(300)}",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
