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

    suspend fun scanAndSave() {
        val uris = smbRepository.listFiles()
        val entities = uris.map { FileEntity(it.toString(), it.lastPathSegment ?: "unknown") }
        fileDao.insertAll(entities)
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
