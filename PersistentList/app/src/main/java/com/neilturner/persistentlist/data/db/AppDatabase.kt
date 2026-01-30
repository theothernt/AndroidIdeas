package com.neilturner.persistentlist.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FileEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
}
