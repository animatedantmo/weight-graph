package org.animatedantmo.weightgraph.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.launch
import org.animatedantmo.weightgraph.backup.BACKUPS_TO_KEEP
import org.animatedantmo.weightgraph.backup.BackupOutcome
import org.animatedantmo.weightgraph.backup.BackupSettings
import org.animatedantmo.weightgraph.backup.BackupWorker
import org.animatedantmo.weightgraph.backup.DriveAuth
import org.animatedantmo.weightgraph.backup.DriveBackup
import org.animatedantmo.weightgraph.backup.runDriveBackup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Both actions need Drive access first; this records which one to resume after consent.
private enum class PendingAction { BACK_UP_NOW, ENABLE_DAILY }

private val LAST_BACKUP_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")

@Composable
fun BackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { BackupSettings(context) }

    var status by remember { mutableStateOf(settings.read()) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    // Set when Drive rejects a cached token, so a fresh authorization runs once on its own. The
    // second flag stops a token that keeps being rejected from looping.
    var retryAfterRejectedToken by remember { mutableStateOf(false) }
    var rejectedTokenRetried by remember { mutableStateOf(false) }

    // Once an attempt starts in this dialog, its own result replaces whatever the last daily run
    // stored, so an old error does not flash up while Google's screen is loading.
    var attempted by remember { mutableStateOf(false) }

    fun refresh() {
        status = settings.read()
    }

    fun proceed(action: PendingAction, token: String?) {
        running = false
        if (token == null) {
            message = "Google did not return Drive access. Try again."
            return
        }
        settings.setAuthorized(true)
        when (action) {
            PendingAction.ENABLE_DAILY -> {
                settings.setDailyEnabled(true)
                BackupWorker.schedule(context)
                refresh()
            }
            PendingAction.BACK_UP_NOW -> scope.launch {
                running = true
                message = null
                val outcome = runDriveBackup(context, token)
                running = false
                if (outcome == BackupOutcome.TokenRejected && !rejectedTokenRetried) {
                    rejectedTokenRetried = true
                    retryAfterRejectedToken = true
                    return@launch
                }
                message = when (outcome) {
                    is BackupOutcome.Success -> "Backed up " + outcome.entryCount + " entries."
                    BackupOutcome.NothingToBackUp -> "There are no entries to back up."
                    is BackupOutcome.Failed -> outcome.message
                    BackupOutcome.TokenRejected ->
                        "Google Drive did not accept the app's access. Tap Back up now to reconnect."
                }
                refresh()
            }
        }
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pending
        pending = null
        if (action == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            // A closed consent screen is not always the user saying no: Google also closes it
            // straight away when it rejects the app, and the reason travels in the result intent.
            message = try {
                Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data)
                "Google Drive access was not granted."
            } catch (e: ApiException) {
                describeAuthFailure(e)
            }
            return@rememberLauncherForActivityResult
        }
        try {
            val auth = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(result.data)
            proceed(action, auth.accessToken)
        } catch (e: ApiException) {
            message = describeAuthFailure(e)
        }
    }

    fun requestAccess(action: PendingAction) {
        message = null
        attempted = true
        // Spinner while Google decides whether to show its screen, which can take a few seconds.
        running = true
        scope.launch {
            try {
                val auth = DriveAuth.authorize(context)
                val intent = auth.pendingIntent
                if (auth.hasResolution() && intent != null) {
                    pending = action
                    running = false
                    consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                } else {
                    proceed(action, auth.accessToken)
                }
            } catch (e: ApiException) {
                running = false
                message = describeAuthFailure(e)
            } catch (e: Exception) {
                running = false
                message = "Could not reach Google. Check your connection and try again."
            }
        }
    }

    LaunchedEffect(retryAfterRejectedToken) {
        if (retryAfterRejectedToken) {
            retryAfterRejectedToken = false
            requestAccess(PendingAction.BACK_UP_NOW)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Google Drive Backup", style = MaterialTheme.typography.labelLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Saves all entries as a CSV in a \"" + DriveBackup.FOLDER_NAME + "\" folder " +
                        "in your Google Drive. The newest " + BACKUPS_TO_KEEP + " backups are kept. " +
                        "To restore, use Import CSV and pick a backup from Drive.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                val last = status.lastSuccessMillis
                if (last == null) {
                    Text("No backups yet", style = MaterialTheme.typography.bodyMedium)
                } else {
                    // The entry count gets its own line; appended to the date it wrapped mid-phrase.
                    Column {
                        Text(
                            "Last backup: " +
                                Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault())
                                    .format(LAST_BACKUP_FORMAT),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        status.lastEntryCount?.let { count ->
                            Text(
                                "$count entries were backed up",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Back up daily", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "On Wi-Fi, once a day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = status.dailyEnabled,
                        enabled = !running,
                        onCheckedChange = { turnOn ->
                            if (turnOn) {
                                requestAccess(PendingAction.ENABLE_DAILY)
                            } else {
                                settings.setDailyEnabled(false)
                                BackupWorker.cancel(context)
                                refresh()
                            }
                        },
                    )
                }

                // The latest outcome of this dialog wins; otherwise show what the last daily run
                // reported, so an overnight failure is not silent.
                val shown = message ?: status.lastError.takeUnless { attempted }
                if (shown != null) {
                    Text(
                        shown,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message != null && status.lastError == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    rejectedTokenRetried = false
                    requestAccess(PendingAction.BACK_UP_NOW)
                },
                enabled = !running,
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Back up now")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text("Close") }
        },
    )
}

// DEVELOPER_ERROR is what an unconfigured or mismatched OAuth client produces, and its raw text
// is unhelpful, so it gets an explanation that points at the actual fix.
private fun describeAuthFailure(e: ApiException): String = when {
    e.statusCode == CommonStatusCodes.CANCELED -> "Backup cancelled."
    e.statusCode == CommonStatusCodes.NETWORK_ERROR ->
        "Could not reach Google. Check your connection and try again."
    e.statusCode == CommonStatusCodes.DEVELOPER_ERROR ||
        e.message.orEmpty().contains("UNREGISTERED", ignoreCase = true) ->
        "Google rejected this build of the app. Check that an Android OAuth client exists in " +
            "Google Cloud with this app's package name and signing SHA-1."
    else -> "Google sign-in failed (error " + e.statusCode + "). Try again."
}
