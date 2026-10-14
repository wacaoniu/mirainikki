package com.mirainikki.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/** 服务端 API 客户端：OkHttp + kotlinx.serialization */
class Api(private val ctx: Context) {
    val prefs: SharedPreferences = ctx.getSharedPreferences("mirainikki", Context.MODE_PRIVATE)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(v) = prefs.edit().putString("server_url", v.trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v).apply()

    var lastPull: String
        get() = prefs.getString("last_pull", "1970-01-01T00:00:00.000Z") ?: "1970-01-01T00:00:00.000Z"
        set(v) = prefs.edit().putString("last_pull", v).apply()

    val configured: Boolean get() = serverUrl.isNotEmpty() && token.isNotEmpty()

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    class ApiException(val code: Int, message: String) : Exception(message)

    private fun request(method: String, path: String, body: String? = null): String {
        val rb = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(serverUrl + path)
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .method(method, rb)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = try {
                    (json.parseToJsonElement(text) as? JsonObject)?.get("error")?.toString()?.trim('"')
                } catch (_: Exception) { null } ?: "HTTP ${resp.code}"
                throw ApiException(resp.code, msg)
            }
            return text
        }
    }

    fun login(password: String): String {
        val resp = request("POST", "/api/auth/login", """{"password":"${password.replace("\"", "\\\"")}"}""")
        return json.parseToJsonElement(resp).let { (it as JsonObject)["token"]!!.toString().trim('"') }
    }

    fun uploadImage(bytes: ByteArray, mime: String): Pair<String, String> {
        val boundary = "mirainikki-" + System.currentTimeMillis()
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n")
            append("Content-Type: $mime\r\n\r\n")
        }.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        val req = Request.Builder()
            .url(serverUrl + "/api/images")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody("multipart/form-data; boundary=$boundary".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw ApiException(resp.code, "图片上传失败 HTTP ${resp.code}")
            val obj = json.parseToJsonElement(text) as JsonObject
            val id = obj["id"]!!.toString().trim('"')
            val url = obj["url"]!!.toString().trim('"')
            return id to url
        }
    }

    fun push(entries: List<EntryEntity>, habits: List<HabitEntity>): JsonObject {
        val payload = buildJsonObject {
            put("entries", buildJsonArray {
                entries.forEach { e ->
                    add(buildJsonObject {
                        put("clientId", e.clientId)
                        put("type", e.type)
                        put("content", json.parseToJsonElement(e.contentJson))
                        put("tags", json.parseToJsonElement(e.tagsJson))
                        put("entryDate", e.entryDate)
                        put("createdAt", e.createdAt)
                        put("updatedAt", e.updatedAt)
                        put("deleted", e.deleted)
                    })
                }
            })
            put("habits", buildJsonArray {
                habits.forEach { h ->
                    add(buildJsonObject {
                        put("clientId", h.clientId)
                        put("name", h.name)
                        put("emoji", h.emoji)
                        put("color", h.color)
                        put("archived", h.archived)
                        put("sortOrder", h.sortOrder)
                        put("createdAt", h.createdAt)
                        put("updatedAt", h.updatedAt)
                        put("deleted", h.deleted)
                    })
                }
            })
        }
        return json.parseToJsonElement(request("POST", "/api/sync/push", payload.toString())) as JsonObject
    }

    fun pull(since: String): JsonObject {
        return json.parseToJsonElement(request("GET", "/api/sync/pull?since=$since")) as JsonObject
    }

    /** 下载图片字节（/api/images/:id/file 为免鉴权直读路由） */
    fun downloadImage(imageId: String): ByteArray {
        val req = Request.Builder().url("$serverUrl/api/images/$imageId/file").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, "图片下载失败 HTTP ${resp.code}")
            return resp.body?.bytes() ?: throw ApiException(resp.code, "图片内容为空")
        }
    }
}
