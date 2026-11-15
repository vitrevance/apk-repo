package com.apkrepo.web.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.api.ReleaseDto
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.AppIcon
import com.apkrepo.web.ui.components.CopyField
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane
import com.apkrepo.web.ui.components.StatusChip
import com.apkrepo.web.ui.components.formatBytes
import com.apkrepo.web.ui.components.formatInstant
import kotlinx.browser.window

/**
 * Public app detail page: metadata, version history and download actions.
 */
@Composable
fun AppDetailScreen(packageName: String) {
    var reloadKey by remember { mutableStateOf(0) }
    val state: UiState<Pair<com.apkrepo.web.api.AppDetailDto, List<ReleaseDto>>> by produceState(
        UiState.Loading as UiState<Pair<com.apkrepo.web.api.AppDetailDto, List<ReleaseDto>>>,
        packageName,
        reloadKey,
    ) {
        value = try {
            val detail = ApiClient.getApp(packageName)
            val releases = ApiClient.listReleases(packageName)
            UiState.Ready(detail to releases)
        } catch (e: Throwable) {
            UiState.Failed(e.message ?: "failed to load app")
        }
    }

    when (val s = state) {
        is UiState.Loading -> LoadingPane(modifier = Modifier.fillMaxSize())
        is UiState.Failed -> ErrorPane(
            s.message,
            retry = { reloadKey++ },
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Ready<Pair<com.apkrepo.web.api.AppDetailDto, List<ReleaseDto>>> -> {
            val (detail, releases) = s.value
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(iconUrl = detail.iconUrl, name = detail.displayName ?: packageName, size = 72.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            detail.displayName ?: packageName,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(packageName, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (!detail.description.isNullOrBlank()) {
                    Text(detail.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                detail.latestRelease?.let { latest ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Latest version ${latest.versionName} (${latest.versionCode})",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "${formatBytes(latest.apkSize)} · uploaded ${formatInstant(latest.uploadedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Button(onClick = { window.open(latest.downloadUrl, "_blank") }) {
                                    Text("Download APK")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            CopyField(label = "SHA-256", value = latest.apkSha256)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("Versions (${releases.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                releases.forEach { release ->
                    ReleaseRow(release)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReleaseRow(release: ReleaseDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${release.versionName} (${release.versionCode})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${formatBytes(release.apkSize)} · uploaded ${formatInstant(release.uploadedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusChip(release.status)
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = { window.open(release.downloadUrl, "_blank") }) {
                    Text("Download")
                }
            }
            if (release.minSdk != null || release.targetSdk != null) {
                Text(
                    buildString {
                        release.minSdk?.let { append("minSdk $it") }
                        release.targetSdk?.let {
                            if (isNotEmpty()) append(" · ")
                            append("targetSdk $it")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
