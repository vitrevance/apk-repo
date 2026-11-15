package com.apkrepo.web.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.AdminSession
import com.apkrepo.web.api.ApiClient
import com.apkrepo.web.routing.Screen
import com.apkrepo.web.routing.navigate
import kotlinx.coroutines.launch

/**
 * Admin login form. Validates the credentials against /api/v1/admin/stats
 * and stores them in the session (HTTP Basic on subsequent requests).
 */
@Composable
fun AdminLoginScreen() {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (AdminSession.isSignedIn) navigate(Screen.AdminDashboard)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Administrator sign in", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row {
            Button(
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    AdminSession.login(username, password)
                    // Validate immediately; drop the session on failure.
                    scope.launch {
                        try {
                            ApiClient.adminStats()
                            navigate(Screen.AdminDashboard)
                        } catch (e: Throwable) {
                            AdminSession.logout()
                            error = "Sign in failed — check username and password."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Signing in…" else "Sign in") }
        }
    }
}
