package com.example.memoloop

data class ImgbbResponse(
    val data: ImgbbData?,
    val success: Boolean,
    val status: Int
)

data class ImgbbData(
    val id: String,
    val title: String?,
    val url_viewer: String,
    val url: String,
    val display_url: String,
    val width: String,
    val height: String,
    val size: String,
    val time: String,
    val expiration: String,
    val image: ImgbbImage,
    val thumb: ImgbbImage,
    val medium: ImgbbImage,
    val delete_url: String
)

data class ImgbbImage(
    val filename: String,
    val name: String,
    val mime: String,
    val extension: String,
    val url: String
)