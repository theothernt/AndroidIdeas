package com.neilturner.persistentlist.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [FileEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
}
