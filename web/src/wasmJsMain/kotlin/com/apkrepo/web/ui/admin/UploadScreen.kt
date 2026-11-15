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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.api.UploadResultDto
import com.apkrepo.web.api.UnauthorizedException
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.ui.components.formatBytes
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import com.apkrepo.web.api.fileSizeBytes

/**
 * APK upload page: an HTML file input (created via DOM interop — Compose
 * for web has no native file picker), then a multipart POST with FormData.
 */
@Composable
fun UploadScreen() {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UploadResultDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** Opens the browser file picker via a hidden HTML input element. */
    fun pickFile(onPicked: (File) -> Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = ".apk"
        input.style.display = "none"
        input.onchange = { _ ->
            val file = input.files?.item(0)
            if (file != null) onPicked(file)
            document.body?.removeChild(input)
        }
        document.body?.appendChild(input)
        input.click()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Upload APK", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(
                    onClick = { pickFile { selectedFile = it } },
                    enabled = !busy,
                ) {
                    Text(
                        selectedFile?.let { f -> "${f.name} (${formatBytes(fileSizeBytes(f).toLong())})" }
                            ?: "Choose .apk file…",
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    enabled = !busy && selectedFile != null,
                    onClick = {
                        val file = selectedFile ?: return@Button
                        busy = true
                        error = null
                        result = null
                        scope.launch {
                            try {
                                result = ApiClient.uploadApk(file)
                                selectedFile = null
                            } catch (e: UnauthorizedException) {
                                navigate(Screen.AdminLogin)
                            } catch (e: Throwable) {
                                error = e.message
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(if (busy) "Uploading…" else "Upload")
                }
                if (busy) {
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text("Uploading and analyzing… (large APKs take a moment)")
                    }
                }
            }
        }

        result?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (r.duplicate) "Already known — identical APK bytes are idempotent" else "Upload accepted",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (r.duplicate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Package: ${r.release.packageName}")
                    Text("Name: ${r.release.displayName ?: "—"}")
                    Text("Version: ${r.release.versionName} (${r.release.versionCode})")
                    Text("Size: ${formatBytes(r.release.apkSize)}")
                    Text("Min SDK: ${r.release.minSdk ?: "—"} · Target SDK: ${r.release.targetSdk ?: "—"}")
                    Text("Status: ${r.release.status}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A publication job has been queued; the release appears in the catalog " +
                            "and F-Droid repository once it completes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { navigate(Screen.AdminAppManage(r.release.packageName)) }) {
                        Text("Manage this app")
                    }
                }
            }
        }

        error?.let { e ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Upload failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(e, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
