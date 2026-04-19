package com.docly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docly.app.data.local.entity.SavedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: SavedDocumentEntity)

    @Query("SELECT * FROM saved_documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedDocumentEntity>>

    @Query("SELECT * FROM saved_documents WHERE id = :documentId")
    suspend fun getById(documentId: String): SavedDocumentEntity?

    @Update
    suspend fun update(document: SavedDocumentEntity)

    @Delete
    suspend fun delete(document: SavedDocumentEntity)
}
