package com.apkrepo.web.ui.admin

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.AdminReleaseDto
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.api.UnauthorizedException
import com.apkrepo.web.api.UpdateAppRequest
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane
import com.apkrepo.web.ui.components.StatusChip
import com.apkrepo.web.ui.components.formatBytes
import com.apkrepo.web.ui.components.formatInstant
import kotlinx.coroutines.launch

/**
 * Management page for one app: edit catalog metadata, inspect the release
 * history and unpublish releases.
 */
@Composable
fun AppManageScreen(packageName: String) {
    var reloadKey by remember { mutableStateOf(0) }
    val state: UiState<Pair<com.apkrepo.web.api.AppDetailDto, List<AdminReleaseDto>>> by produceState(
        UiState.Loading as UiState<Pair<com.apkrepo.web.api.AppDetailDto, List<AdminReleaseDto>>>,
        packageName,
        reloadKey,
    ) {
        value = try {
            val detail = ApiClient.getApp(packageName)
            // Admin releases include all statuses; fall back to the public
            // history when the admin session is unavailable.
            val releases = try {
                ApiClient.adminListReleases().content.filter { it.packageName == packageName }
            } catch (e: UnauthorizedException) {
                navigate(Screen.AdminLogin)
                emptyList()
            }
            UiState.Ready(detail to releases)
        } catch (e: Throwable) {
            UiState.Failed(e.message ?: "failed to load app")
        }
    }

    when (val s = state) {
        is UiState.Loading -> LoadingPane(modifier = Modifier.fillMaxSize())
        is UiState.Failed -> ErrorPane(s.message, retry = { reloadKey++ }, modifier = Modifier.fillMaxSize())
        is UiState.Ready<Pair<com.apkrepo.web.api.AppDetailDto, List<AdminReleaseDto>>> -> {
            val (detail, releases) = s.value
            AppManageContent(packageName, detail.displayName, detail.description, releases) { reloadKey++ }
        }
    }
}

@Composable
private fun AppManageContent(
    packageName: String,
    currentName: String?,
    currentDescription: String?,
    releases: List<AdminReleaseDto>,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(currentName) { mutableStateOf(currentName ?: "") }
    var description by remember(currentDescription) { mutableStateOf(currentDescription ?: "") }
    var saving by remember { mutableStateOf(false) }
    var savedNote by remember { mutableStateOf<String?>(null) }
    var confirmUnpublish by remember { mutableStateOf<AdminReleaseDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Manage $packageName", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // --- metadata editor ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Catalog metadata", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            savedNote = null
                            scope.launch {
                                try {
                                    ApiClient.updateApp(
                                        packageName,
                                        UpdateAppRequest(
                                            displayName = name.takeIf { it.isNotBlank() },
                                            description = description.takeIf { it.isNotBlank() },
                                        ),
                                    )
                                    savedNote = "Saved — the F-Droid metadata regenerates on the next publication job."
                                    onReload()
                                } catch (e: UnauthorizedException) {
                                    navigate(Screen.AdminLogin)
                                } catch (e: Throwable) {
                                    savedNote = "Save failed: ${e.message}"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    ) { Text(if (saving) "Saving…" else "Save") }
                    Spacer(modifier = Modifier.width(12.dp))
                    savedNote?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- releases ---
        Text("Releases (${releases.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (releases.isEmpty()) {
            Text("No releases found.", style = MaterialTheme.typography.bodyMedium)
        }
        releases.forEach { release ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${release.versionName} (${release.versionCode})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${formatBytes(release.apkSize)} · uploaded ${formatInstant(release.uploadedAt)} · published ${formatInstant(release.publishedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("Signer: ${release.apkSignerSha256 ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusChip(release.status)
                    }
                    release.errorMessage?.let { err ->
                        Text("Error: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (release.status == "PUBLISHED" || release.status == "READY") {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { confirmUnpublish = release }) { Text("Unpublish") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    confirmUnpublish?.let { release ->
        AlertDialog(
            onDismissRequest = { confirmUnpublish = null },
            title = { Text("Unpublish release?") },
            text = {
                Text(
                    "This removes ${release.versionName} (${release.versionCode}) from the catalog and " +
                        "the F-Droid repository (a regeneration job is queued). The stored APK bytes are retained.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = release
                    confirmUnpublish = null
                    scope.launch {
                        try {
                            ApiClient.unpublishRelease(target.id)
                            onReload()
                        } catch (e: UnauthorizedException) {
                            navigate(Screen.AdminLogin)
                        } catch (e: Throwable) {
                            // surfaced via the next reload; keep simple
                        }
                    }
                }) { Text("Unpublish") }
            },
            dismissButton = { TextButton(onClick = { confirmUnpublish = null }) { Text("Cancel") } },
        )
    }
}
