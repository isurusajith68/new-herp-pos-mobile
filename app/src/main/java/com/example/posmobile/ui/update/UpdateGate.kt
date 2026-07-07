package com.example.posmobile.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.posmobile.data.Container
import com.example.posmobile.data.UpdateInfo

/**
 * Checks GitHub Releases on launch. If a newer build exists, prompts the user and
 * — on accept — opens the APK download in a browser (the browser installs it). The
 * app never installs APKs itself, so it stays off Play Protect's radar.
 * Best-effort: failures are swallowed so they never gate the app.
 */
@Composable
fun UpdateGate() {
    val updater = Container.updater

    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        info = runCatching { updater.checkForUpdate() }.getOrNull()
    }

    val update = info ?: return
    if (dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("Update available") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                Text(
                    "Version ${update.version} is available. Tap Update to download and install it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            update.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                updater.openDownload(update)
                dismissed = true
            }) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text("Later") }
        },
    )
}
