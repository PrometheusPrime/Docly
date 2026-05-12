package com.docly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docly.app.data.local.entity.ConversionJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: ConversionJobEntity)

    @Query("SELECT * FROM conversion_jobs WHERE id = :jobId")
    fun observeJob(jobId: String): Flow<ConversionJobEntity?>

    @Query("SELECT * FROM conversion_jobs ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ConversionJobEntity>>

    @Update
    suspend fun update(job: ConversionJobEntity)
}
