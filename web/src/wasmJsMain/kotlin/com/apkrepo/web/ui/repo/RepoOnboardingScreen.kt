package com.apkrepo.web.ui.repo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.api.RepositoryInfoDto
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.CopyField
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane
import com.apkrepo.web.ui.components.rememberNetworkImage

/**
 * "Add to F-Droid" onboarding page: repository URL, fingerprint, QR code
 * and step-by-step instructions for F-Droid-compatible clients.
 */
@Composable
fun RepoOnboardingScreen() {
    var reloadKey by remember { mutableStateOf(0) }
    val state: UiState<RepositoryInfoDto> by produceState(
        UiState.Loading as UiState<RepositoryInfoDto>,
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
        is UiState.Failed -> ErrorPane(
            s.message,
            retry = { reloadKey++ },
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Ready<RepositoryInfoDto> -> {
            val info = s.value
            val qr = rememberNetworkImage("${info.url.trimEnd('/')}/index.png")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text("Add this repository to F-Droid", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Scan the QR code with the F-Droid client, or add the repository URL manually. " +
                        "Always verify the fingerprint matches the one below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (qr != null) {
                                Image(
                                    bitmap = qr,
                                    contentDescription = "Repository QR code",
                                    modifier = Modifier.size(180.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Text("(QR code appears\nafter the first publish)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(info.name, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            CopyField(label = "Repository URL", value = info.url)
                            Spacer(modifier = Modifier.height(8.dp))
                            CopyField(
                                label = "Fingerprint (SHA-256)",
                                value = info.fingerprint ?: "not yet generated",
                            )
                            if (info.fingerprint != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CopyField(
                                    label = "Shareable link (URL + fingerprint)",
                                    value = "${info.url}?fingerprint=${info.fingerprint}",
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Manual steps", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1. Open the F-Droid client on your Android device", style = MaterialTheme.typography.bodyMedium)
                        Text("2. Settings → Repositories → add (+)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "3. Paste the repository URL (the shareable link includes the fingerprint)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("4. Confirm the fingerprint matches the value above", style = MaterialTheme.typography.bodyMedium)
                        Text("5. Refresh — the apps from this catalog appear and can be installed", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
