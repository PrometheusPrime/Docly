package com.docly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.docly.app.data.local.entity.RecentDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recentDocument: RecentDocumentEntity)

    @Query("SELECT * FROM recent_documents ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentDocumentEntity>>

    @Query("DELETE FROM recent_documents WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)
}
