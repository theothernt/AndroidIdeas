package com.neilturner.persistentlist.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val uri: String,
    val fileName: String,
    val viewed: Boolean = false
)
