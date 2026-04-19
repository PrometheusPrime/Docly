package com.docly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docly.app.data.local.entity.ScannedPageEntity

@Dao
interface ScannedPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: ScannedPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<ScannedPageEntity>)

    @Query("SELECT * FROM scanned_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getBySessionId(sessionId: String): List<ScannedPageEntity>

    @Query("SELECT * FROM scanned_pages WHERE id = :pageId")
    suspend fun getById(pageId: String): ScannedPageEntity?

    @Update
    suspend fun update(page: ScannedPageEntity)

    @Delete
    suspend fun delete(page: ScannedPageEntity)

    @Query("DELETE FROM scanned_pages WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
}
