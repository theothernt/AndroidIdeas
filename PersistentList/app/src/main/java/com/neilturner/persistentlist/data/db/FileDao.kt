package com.neilturner.persistentlist.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE viewed = 0 ORDER BY randomOrder LIMIT :limit OFFSET :offset")
    suspend fun getBatch(limit: Int, offset: Int): List<FileEntity>

    @Query("SELECT COUNT(*) FROM files")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM files WHERE viewed = 1")
    fun getViewedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Query("DELETE FROM files")
    suspend fun deleteAll()

    @Query("UPDATE files SET viewed = :viewed WHERE uri = :uri")
    suspend fun updateViewedStatus(uri: String, viewed: Boolean)
}
