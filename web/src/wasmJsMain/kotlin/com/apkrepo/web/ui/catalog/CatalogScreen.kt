package com.apkrepo.web.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
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
import com.apkrepo.web.routing.navigate
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.state.UiState
import com.apkrepo.web.ui.components.AppIcon
import com.apkrepo.web.ui.components.ErrorPane
import com.apkrepo.web.ui.components.LoadingPane

/**
 * Public catalog: search box and a responsive list of apps. Reaching the
 * end of the list loads the next page.
 */
@Composable
fun CatalogScreen() {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }

    // Debounced reload whenever the query or page changes.
    val state: UiState<PageResponseDto<com.apkrepo.web.api.AppSummaryDto>> by produceState(
        UiState.Loading as UiState<PageResponseDto<com.apkrepo.web.api.AppSummaryDto>>,
        query,
        page,
    ) {
        value = try {
            val result = ApiClient.listApps(query.ifBlank { null }, page)
            UiState.Ready(result)
        } catch (e: Throwable) {
            UiState.Failed(e.message ?: "failed to load catalog")
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                page = 0
            },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        when (val s = state) {
            is UiState.Loading -> LoadingPane(modifier = Modifier.fillMaxSize())
            is UiState.Failed -> ErrorPane(
                s.message,
                retry = { page = 0 },
                modifier = Modifier.fillMaxSize(),
            )
            is UiState.Ready<PageResponseDto<com.apkrepo.web.api.AppSummaryDto>> -> {
                val result = s.value
                if (result.content.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) "No apps published yet" else "No apps match \"$query\"",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(result.content, key = { it.packageName }) { app ->
                            AppRow(app.packageName, app.displayName, app.iconUrl, app.latestVersionName)
                        }
                        if (page + 1 < result.totalPages) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.TextButton(onClick = { page += 1 }) {
                                        Text("Load more (${result.totalElements} total)")
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(packageName: String, displayName: String?, iconUrl: String?, version: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { navigate(Screen.AppDetail(packageName)) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(iconUrl = iconUrl, name = displayName ?: packageName, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(displayName ?: packageName, style = MaterialTheme.typography.titleMedium)
                Text(packageName, style = MaterialTheme.typography.bodySmall)
                if (version != null) {
                    Text("Latest: $version", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
