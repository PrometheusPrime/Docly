package com.docly.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object RoomMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE scanned_pages ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'ACCEPTED'"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
