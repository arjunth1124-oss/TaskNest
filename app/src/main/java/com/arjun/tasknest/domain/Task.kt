package com.arjun.tasknest.domain

/** UI-facing task model. Free of Room / network annotations. */
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val updatedAt: Long,
    val isPendingSync: Boolean,
)
