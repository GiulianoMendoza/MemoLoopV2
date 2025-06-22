package com.example.memoloop


data class Reminder(
    var id: String = "",
    val userId: String = "",
    val title: String = "",
    val timestamp: Long = 0,
    val type: String = "",
    val category: String = "",
    val sharedWith: List<String> = emptyList(),
    val isShared: Boolean = false,
    val originalCreatorId: String = "",
    val sharedFromUserName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null
)