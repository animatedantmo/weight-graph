package org.animatedantmo.weightgraph.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema export is off while the app is pre-release and migrations do not exist yet. Turn it on
 * (and commit the generated `app/schemas/` JSON) before shipping a version 2 that needs to
 * migrate real user data.
 */
@Database(entities = [WeightEntry::class], version = 1, exportSchema = false)
abstract class WeightDatabase : RoomDatabase() {

    abstract fun weightDao(): WeightDao

    companion object {
        @Volatile
        private var instance: WeightDatabase? = null

        fun get(context: Context): WeightDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WeightDatabase::class.java,
                    "weight.db",
                ).build().also { instance = it }
            }
    }
}
