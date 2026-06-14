package com.tonezen.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal fun getRemoteJson(httpClient: OkHttpClient, url: String, accessToken: String?): JSONObject {
    val builder = Request.Builder().url(url)
    accessToken?.let { builder.header("Authorization", "Bearer $it") }
    httpClient.newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) {
            throw RemoteHttpException(response.code, "Remote GET failed (${response.code})")
        }
        return JSONObject(response.body?.string().orEmpty())
    }
}
