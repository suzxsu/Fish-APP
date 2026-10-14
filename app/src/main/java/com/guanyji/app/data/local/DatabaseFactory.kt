package com.guanyji.app.data.local

import android.content.Context
import androidx.room.Room

object DatabaseFactory {
    fun create(context: Context): FishDatabase = Room.databaseBuilder(
        context.applicationContext,
        FishDatabase::class.java,
        "guanyji.db",
    ).addMigrations(FishDatabase.MIGRATION_1_2, FishDatabase.MIGRATION_2_3, FishDatabase.MIGRATION_3_4, FishDatabase.MIGRATION_4_5, FishDatabase.MIGRATION_5_6, FishDatabase.MIGRATION_6_7).build()
}
