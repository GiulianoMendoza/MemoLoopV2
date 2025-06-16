package com.example.memoloop

data class Reminder(
    var id: String = "",
    var userId: String = "",
    var title: String = "",
    var timestamp: Long = 0L,
    var type: String = "",
    var category: String = "",
    var sharedWith: List<String> = emptyList(),
    var isShared: Boolean = false,
    var originalCreatorId: String = "",
    var sharedFromUserName: String = ""
)
