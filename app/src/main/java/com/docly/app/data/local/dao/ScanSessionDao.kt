package com.docly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docly.app.data.local.entity.ScanSessionEntity

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestByStatus(status: String): ScanSessionEntity?

    @Update
    suspend fun update(session: ScanSessionEntity)

    @Delete
    suspend fun delete(session: ScanSessionEntity)
}
