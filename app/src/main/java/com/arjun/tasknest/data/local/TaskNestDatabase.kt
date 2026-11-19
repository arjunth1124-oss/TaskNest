package com.arjun.tasknest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
abstract class TaskNestDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
