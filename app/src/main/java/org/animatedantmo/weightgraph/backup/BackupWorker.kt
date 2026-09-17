package org.animatedantmo.weightgraph.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.animatedantmo.weightgraph.data.WeightDatabase
import org.animatedantmo.weightgraph.data.buildWeightCsv
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

// Enough history to step back a week past a mistake, without the folder growing forever.
const val BACKUPS_TO_KEEP = 7

private const val TAG = "DriveBackup"

// Locale.US so the marker is always AM/PM, whatever language the phone is set to.
private val BACKUP_NAME_TIME = DateTimeFormatter.ofPattern("yyyy_MM_dd_hhmma", Locale.US)
private const val HTTP_UNAUTHORIZED = 401
private const val RECONNECT_MESSAGE =
    "Google Drive needs reconnecting. Open the app and tap Back up now."

sealed interface BackupOutcome {
    data class Success(val entryCount: Int) : BackupOutcome
    data object NothingToBackUp : BackupOutcome
    data class Failed(val message: String) : BackupOutcome

    // Drive refused the token. It has already been cleared, so the caller should authorize again
    // and retry once rather than report an error.
    data object TokenRejected : BackupOutcome
}

/**
 * The backup itself, shared by the daily worker and the Back up now button so both behave the
 * same way and record their result in the same place.
 */
suspend fun runDriveBackup(context: Context, accessToken: String): BackupOutcome =
    withContext(Dispatchers.IO) {
        val settings = BackupSettings(context)
        val entries = WeightDatabase.get(context).weightDao().observeAll().first()

        // An empty database is never uploaded. Doing so would push the real backups out of the
        // kept window one day at a time, which is exactly the situation a backup exists for:
        // an accidental Delete All would otherwise erase its own recovery copies within a week.
        if (entries.isEmpty()) return@withContext BackupOutcome.NothingToBackUp

        try {
            // e.g. weights_2026_09_16_1030PM.csv. Pruning goes by Drive's createdTime, so the
            // 12-hour time not sorting by name does not affect which backups are kept.
            val name = "weights_" + LocalDateTime.now().format(BACKUP_NAME_TIME) + ".csv"
            DriveBackup(accessToken).upload(name, buildWeightCsv(entries), BACKUPS_TO_KEEP)
            settings.recordSuccess(System.currentTimeMillis(), entries.size)
            BackupOutcome.Success(entries.size)
        } catch (e: DriveHttpException) {
            if (e.code == HTTP_UNAUTHORIZED) {
                // Best effort: if clearing fails, the retry just gets the same token and fails again.
                runCatching { DriveAuth.clearToken(context, accessToken) }
                BackupOutcome.TokenRejected
            } else {
                // Drive's JSON error body is kept for logcat; the screen gets a readable line.
                Log.w(TAG, "Drive backup failed", e)
                val message = "Google Drive returned an error (HTTP " + e.code + "). Try again later."
                settings.recordError(message)
                BackupOutcome.Failed(message)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Drive backup failed", e)
            val message = "Could not reach Google Drive. Check your connection and try again."
            settings.recordError(message)
            BackupOutcome.Failed(message)
        }
    }

class BackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = BackupSettings(applicationContext)
        // A second pass only happens when Drive rejected the first token, which has been cleared
        // by then, so the fresh authorize call returns a new token or reports that access is gone.
        repeat(2) {
            val token = try {
                DriveAuth.silentToken(applicationContext)
            } catch (e: Exception) {
                settings.recordError("Could not reach Google: " + (e.message ?: "unknown error"))
                return Result.retry()
            }
            if (token == null) {
                // Access was revoked or has lapsed. A worker cannot show the consent screen, so
                // say so and wait for the user to reconnect instead of retrying forever.
                settings.recordError(RECONNECT_MESSAGE)
                return Result.success()
            }
            when (runDriveBackup(applicationContext, token)) {
                BackupOutcome.TokenRejected -> Unit
                is BackupOutcome.Failed ->
                    return if (runAttemptCount < 3) Result.retry() else Result.success()
                else -> return Result.success()
            }
        }
        settings.recordError(RECONNECT_MESSAGE)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "drive-backup-daily"

        // Unmetered network so a daily upload never spends mobile data, and a healthy battery
        // so it never competes with the phone when it is running low.
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
