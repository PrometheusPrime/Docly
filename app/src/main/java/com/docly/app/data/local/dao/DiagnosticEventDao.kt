package com.docly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.docly.app.data.local.entity.DiagnosticEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DiagnosticEventEntity)

    @Query("SELECT * FROM diagnostic_events ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DiagnosticEventEntity>>
}
