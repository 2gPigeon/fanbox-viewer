package com.example.fanboxviewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CreatorEntity::class, PostEntity::class, TagEntity::class, PostTagEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun creatorDao(): CreatorDao
    abstract fun postDao(): PostDao
    abstract fun tagDao(): TagDao
}
