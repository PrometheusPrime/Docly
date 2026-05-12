package com.docly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docly.app.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE name LIKE :queryLike
        ORDER BY updatedAt DESC
        """
    )
    fun searchByName(queryLike: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getById(documentId: String): DocumentEntity?

    @Query("UPDATE documents SET name = :name, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun rename(documentId: String, name: String, updatedAt: Long)

    @Query("UPDATE documents SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updateFavorite(documentId: String, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE documents SET lastOpenedAt = :openedAt, updatedAt = :openedAt WHERE id = :documentId")
    suspend fun updateLastOpened(documentId: String, openedAt: Long)

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)
}
