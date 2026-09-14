package org.animatedantmo.weightgraph.backup

import android.content.Context
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
import java.util.concurrent.TimeUnit

// Enough history to step back a week past a mistake, without the folder growing forever.
const val BACKUPS_TO_KEEP = 7

sealed interface BackupOutcome {
    data class Success(val entryCount: Int) : BackupOutcome
    data object NothingToBackUp : BackupOutcome
    data class Failed(val message: String) : BackupOutcome
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
            val name = "weights_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".csv"
            DriveBackup(accessToken).upload(name, buildWeightCsv(entries), BACKUPS_TO_KEEP)
            settings.recordSuccess(System.currentTimeMillis(), entries.size)
            BackupOutcome.Success(entries.size)
        } catch (e: IOException) {
            val message = e.message ?: "Backup failed"
            settings.recordError(message)
            BackupOutcome.Failed(message)
        }
    }

class BackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = BackupSettings(applicationContext)
        val token = try {
            DriveAuth.silentToken(applicationContext)
        } catch (e: Exception) {
            settings.recordError("Could not reach Google: " + (e.message ?: "unknown error"))
            return Result.retry()
        }
        if (token == null) {
            // Access was revoked or has lapsed. A worker cannot show the consent screen, so say
            // so and wait for the user to reconnect from the app instead of retrying forever.
            settings.recordError("Google Drive needs reconnecting. Open the app and tap Back up now.")
            return Result.success()
        }
        return when (runDriveBackup(applicationContext, token)) {
            is BackupOutcome.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
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
