package com.example.memoloop.network

import com.example.memoloop.ImgbbResponse
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ImgbbService {
    @Multipart
    @POST("1/upload")
    fun uploadImage(
        @Query("key") apiKey: String,
        @Part("image") image: RequestBody
    ): Call<ImgbbResponse>
}