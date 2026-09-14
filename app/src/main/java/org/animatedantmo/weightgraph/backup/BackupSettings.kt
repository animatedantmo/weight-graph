package org.animatedantmo.weightgraph.backup

import android.content.Context

// What the backup screen needs to show. Written by both the daily worker and a manual backup, so
// the result of an overnight run is visible the next time the dialog opens.
data class BackupStatus(
    val authorized: Boolean,
    val dailyEnabled: Boolean,
    val lastSuccessMillis: Long?,
    val lastEntryCount: Int?,
    val lastError: String?,
)

class BackupSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("drive_backup", Context.MODE_PRIVATE)

    fun read(): BackupStatus = BackupStatus(
        authorized = prefs.getBoolean(KEY_AUTHORIZED, false),
        dailyEnabled = prefs.getBoolean(KEY_DAILY, false),
        lastSuccessMillis = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L },
        lastEntryCount = prefs.getInt(KEY_LAST_COUNT, -1).takeIf { it >= 0 },
        lastError = prefs.getString(KEY_LAST_ERROR, null),
    )

    fun setAuthorized(authorized: Boolean) {
        prefs.edit().putBoolean(KEY_AUTHORIZED, authorized).apply()
    }

    fun setDailyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DAILY, enabled).apply()
    }

    fun recordSuccess(atMillis: Long, entryCount: Int) {
        prefs.edit()
            .putLong(KEY_LAST_SUCCESS, atMillis)
            .putInt(KEY_LAST_COUNT, entryCount)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordError(message: String) {
        prefs.edit().putString(KEY_LAST_ERROR, message).apply()
    }

    private companion object {
        const val KEY_AUTHORIZED = "authorized"
        const val KEY_DAILY = "daily_enabled"
        const val KEY_LAST_SUCCESS = "last_success_millis"
        const val KEY_LAST_COUNT = "last_entry_count"
        const val KEY_LAST_ERROR = "last_error"
    }
}
