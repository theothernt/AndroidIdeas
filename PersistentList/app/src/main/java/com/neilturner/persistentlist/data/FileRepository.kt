package com.neilturner.persistentlist.data

import com.neilturner.persistentlist.data.db.FileDao
import com.neilturner.persistentlist.data.db.FileEntity
import kotlinx.coroutines.flow.Flow

class FileRepository(
    private val smbRepository: SmbRepository,
    private val fileDao: FileDao
) {
    suspend fun getUnviewedBatch(limit: Int, offset: Int): List<String> {
        return fileDao.getBatch(limit, offset).map { it.uri }
    }

    data class ScanMetrics(val sambaDuration: Long, val dbDuration: Long)

    suspend fun scanAndSave(): ScanMetrics {
        val start = System.currentTimeMillis()
        var dbTime = 0L
        val deleteStart = System.currentTimeMillis()
        fileDao.deleteAll()
        dbTime += (System.currentTimeMillis() - deleteStart)

        val batch = mutableListOf<FileEntity>()
        smbRepository.listFiles().collect { uriString ->
            val fileName = uriString.substringAfterLast('/')
            batch.add(FileEntity(uriString, fileName))
            if (batch.size >= 500) {
                val dbStart = System.currentTimeMillis()
                fileDao.insertAll(batch)
                dbTime += (System.currentTimeMillis() - dbStart)
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            val dbStart = System.currentTimeMillis()
            fileDao.insertAll(batch)
            dbTime += (System.currentTimeMillis() - dbStart)
        }
        val end = System.currentTimeMillis()
        
        return ScanMetrics(end - start - dbTime, dbTime)
    }

    val viewedCount: Flow<Int> = fileDao.getViewedCount()

    suspend fun markAsViewed(uri: String) {
        fileDao.updateViewedStatus(uri, true)
    }

    suspend fun clearAll() {
        fileDao.deleteAll()
    }
}
