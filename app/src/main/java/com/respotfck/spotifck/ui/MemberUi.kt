package com.respotfck.spotifck.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.respotfck.spotifck.security.MemberAuth
import kotlinx.coroutines.launch

/** Full-screen member-code login shown until the device holds a valid member session. */
@Composable
fun MemberGateOverlay() {
    if (MemberAuth.signedIn.value) return
    val activity = LocalActivity.current
    BackHandler { activity?.moveTaskToBack(true) }
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Members only", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "ReSpotifuck is invite only. Enter the member code you were given by someone you trust.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(64); error = null },
                label = { Text("Member code") },
                placeholder = { Text("RSF-XXXXX-XXXXX-XXXXX-XXXXX") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        error = when (MemberAuth.login(code)) {
                            MemberAuth.LoginResult.Ok -> null
                            MemberAuth.LoginResult.Invalid -> "That code is not valid or has been revoked."
                            MemberAuth.LoginResult.NotSetUp -> "Member login is not set up yet. Try again later."
                            MemberAuth.LoginResult.Offline -> "Couldn't reach the server. Check your connection and try again."
                        }
                        busy = false
                    }
                },
                enabled = !busy && code.trim().length >= 12,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Checking…" else "Log in") }
            Text(
                "We don't exist. Never share your code; each code belongs to one person and can be revoked.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
