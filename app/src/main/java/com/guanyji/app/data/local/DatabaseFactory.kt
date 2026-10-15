package com.guanyji.app.data.local

import android.content.Context
import androidx.room.Room

object DatabaseFactory {
    @Volatile private var instance: FishDatabase? = null

    fun create(context: Context): FishDatabase = instance?.takeIf { it.isOpen } ?: synchronized(this) {
        instance?.takeIf { it.isOpen } ?: build(context).also { instance = it }
    }

    private fun build(context: Context): FishDatabase {
        com.guanyji.app.data.backup.LocalBackupManager.recoverInterruptedRestore(context.applicationContext)
        return Room.databaseBuilder(
        context.applicationContext,
        FishDatabase::class.java,
        "guanyji.db",
        ).addMigrations(FishDatabase.MIGRATION_1_2, FishDatabase.MIGRATION_2_3, FishDatabase.MIGRATION_3_4, FishDatabase.MIGRATION_4_5, FishDatabase.MIGRATION_5_6, FishDatabase.MIGRATION_6_7, FishDatabase.MIGRATION_7_8).build()
    }

    fun closeForRestore() = synchronized(this) { instance?.close(); instance = null }
}
