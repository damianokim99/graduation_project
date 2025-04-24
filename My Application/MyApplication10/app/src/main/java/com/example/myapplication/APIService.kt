package com.example.myapplication

//class APIService {
//}

// ApiService.kt

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull

object ApiService {

    private val client = OkHttpClient()
    private const val URL = "https://9ac2-125-179-99-25.ngrok-free.app"  // ngrok 주소

    fun sendEcho(text: String) {
        val json = JSONObject()
        json.put("text", text)

        val body = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url(URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("서버응답", "실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let {
                    Log.d("서버응답", "성공: ${it.string()}")
                }
            }
        })
    }
}
