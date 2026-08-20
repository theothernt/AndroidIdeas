package com.neilturner.persistentlist.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val uri: String,
    val fileName: String,
    val viewed: Boolean = false,
    val randomOrder: Double = Math.random()
)
