package com.example.fanboxviewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CreatorEntity::class, PostEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun creatorDao(): CreatorDao
    abstract fun postDao(): PostDao
}
