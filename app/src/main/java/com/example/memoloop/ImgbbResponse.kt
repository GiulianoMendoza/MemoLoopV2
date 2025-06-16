package com.example.memoloop

data class ImgbbResponse(
    val data: Data
) {
    data class Data(
        val url: String
    )
}