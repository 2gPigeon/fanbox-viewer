package com.example.fanboxviewer

import android.content.Context
import androidx.room.Room
import com.example.fanboxviewer.data.local.AppDatabase
import com.example.fanboxviewer.data.repo.CreatorRepository
import com.example.fanboxviewer.data.repo.PostRepository
import com.example.fanboxviewer.data.repo.TagRepository

class AppContainer(context: Context) {
    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "fanboxviewer.db"
    ).fallbackToDestructiveMigration()
        .build()

    val creatorRepository = CreatorRepository(db.creatorDao())
    val postRepository = PostRepository(db.postDao())
    val tagRepository = TagRepository(db.tagDao())
}
