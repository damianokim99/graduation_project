package com.example.myapplication

// WebSocketManager.kt

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object WebSocketManager {
    private var webSocket: WebSocket? = null
    var onUserListUpdate: ((List<Pair<String, Double>>) -> Unit)? = null
    private var currentUsers: List<Pair<String, Double>> = emptyList()
    private var myName: String = ""

    fun connect(userName: String) {
        myName = userName
        val client = OkHttpClient.Builder()
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("wss://67e2-125-179-99-25.ngrok-free.app/ws")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("WebSocket", "연결됨")
                ws.send("JOIN:$userName")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("WebSocket", "받은 메시지: $text")
                val json = JSONArray(text)
                val users = mutableListOf<Pair<String, Double>>()
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    val name = obj.getString("name")
                    val dist = obj.getDouble("distance")
                    users.add(name to dist)
                }
                currentUsers = users
                onUserListUpdate?.invoke(users)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "에러: ${t.message}")
            }
        })
    }

    fun sendDistance(distance: Double) {
        webSocket?.send("DISTANCE:$distance")
    }

    fun getMyCurrentRank(): Int {
        val sorted = currentUsers.sortedByDescending { it.second }
        return sorted.indexOfFirst { it.first == myName } + 1
    }
}
