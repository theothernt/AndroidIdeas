package com.neilturner.persistentlist.data

import com.neilturner.persistentlist.data.db.FileDao
import com.neilturner.persistentlist.data.db.FileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileRepository(
    private val smbRepository: SmbRepository,
    private val fileDao: FileDao
) {
    val allFiles: Flow<List<String>> = fileDao.getAllFiles().map { entities ->
        entities.map { it.uri }
    }

    data class ScanMetrics(val sambaDuration: Long, val dbDuration: Long)

    suspend fun scanAndSave(): ScanMetrics {
        val sambaStart = System.currentTimeMillis()
        val uris = smbRepository.listFiles()
        val sambaEnd = System.currentTimeMillis()

        val dbStart = System.currentTimeMillis()
        fileDao.deleteAll()
        val entities = uris.map { FileEntity(it.toString(), it.lastPathSegment ?: "unknown") }
        fileDao.insertAll(entities)
        val dbEnd = System.currentTimeMillis()
        
        return ScanMetrics(sambaEnd - sambaStart, dbEnd - dbStart)
    }

    val viewedCount: Flow<Int> = fileDao.getViewedCount()

    suspend fun markAsViewed(uri: String) {
        fileDao.updateViewedStatus(uri, true)
    }

    suspend fun scanIfEmpty() {
        if (fileDao.getCount() == 0) {
            scanAndSave()
        }
    }

    suspend fun isEmpty(): Boolean {
        return fileDao.getCount() == 0
    }

    suspend fun clearAll() {
        fileDao.deleteAll()
    }
}
