package com.apkrepo.web.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.apkrepo.web.api.UnauthorizedException
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.AppIcon
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane

/**
 * Admin app list: every catalog app with a link to its management page.
 */
@Composable
fun AdminAppListScreen() {
    var reloadKey by remember { mutableStateOf(0) }
    val state: UiState<com.apkrepo.web.api.PageResponseDto<com.apkrepo.web.api.AppSummaryDto>> by produceState(
        UiState.Loading as UiState<com.apkrepo.web.api.PageResponseDto<com.apkrepo.web.api.AppSummaryDto>>,
        reloadKey,
    ) {
        value = try {
            UiState.Ready(ApiClient.listApps(null))
        } catch (e: UnauthorizedException) {
            navigate(Screen.AdminLogin)
            UiState.Failed("signed out")
        } catch (e: Throwable) {
            UiState.Failed(e.message ?: "failed to load apps")
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Manage apps", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        when (val s = state) {
            is UiState.Loading -> LoadingPane(modifier = Modifier.fillMaxSize())
            is UiState.Failed -> ErrorPane(s.message, retry = { reloadKey++ }, modifier = Modifier.fillMaxSize())
            is UiState.Ready<com.apkrepo.web.api.PageResponseDto<com.apkrepo.web.api.AppSummaryDto>> -> {
                val apps = s.value.content
                if (apps.isEmpty()) {
                    Text("No apps yet — upload the first APK.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    LazyColumn {
                        items(apps, key = { it.packageName }) { app ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { navigate(Screen.AdminAppManage(app.packageName)) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(iconUrl = app.iconUrl, name = app.displayName ?: app.packageName, size = 40.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(app.displayName ?: app.packageName, style = MaterialTheme.typography.titleMedium)
                                        Text("${app.packageName} · ${app.releaseCount} release(s)", style = MaterialTheme.typography.bodySmall)
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
