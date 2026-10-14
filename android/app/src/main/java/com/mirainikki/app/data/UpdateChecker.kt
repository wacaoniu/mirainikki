package com.mirainikki.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 检查 GitHub Releases 是否有新版本（只在用户手动点击"检查更新"时调用，绝不开屏自动检查）。
 *
 * 版本约定：Release 的 tag 形如 v0.3.0，且附带的 APK 资产文件名含 "MiraiNikki"
 * 或后缀为 .apk。比较 tag 与当前 versionName（如 0.2.0）。
 */
object UpdateChecker {

    private const val OWNER_REPO = "wacaoniu/mirainikki"
    private const val API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"

    data class ReleaseInfo(
        val tag: String,          // 例：v0.3.0
        val version: String,      // 去掉 v 前缀
        val notes: String,        // release 正文（更新说明）
        val apkUrl: String?,      // APK 下载直链（没有则 null）
        val pageUrl: String,      // release 页面
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 返回 null 表示已是最新；抛异常表示检查失败（网络/解析） */
    fun check(currentVersion: String): ReleaseInfo? {
        val req = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MiraiNikki-Android")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null // 还没有 release
            if (!resp.isSuccessful) throw Exception("GitHub 返回 HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("响应为空")
            val json = Json.parseToJsonElement(body).jsonObject
            val tag = json["tag_name"]?.jsonPrimitive?.content ?: return null
            val version = tag.trim().removePrefix("v").removePrefix("V")
            if (!isNewer(version, currentVersion)) return null
            val apkUrl = json["assets"]?.jsonArray?.firstOrNull { asset ->
                val name = asset.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                name.endsWith(".apk", ignoreCase = true)
            }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
            return ReleaseInfo(
                tag = tag,
                version = version,
                notes = json["body"]?.jsonPrimitive?.content ?: "",
                apkUrl = apkUrl,
                pageUrl = json["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$OWNER_REPO/releases",
            )
        }
    }

    /** 下载 APK 到指定文件，onProgress 回调 0..100 */
    fun downloadApk(url: String, dest: java.io.File, onProgress: (Int) -> Unit) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "MiraiNikki-Android").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("下载失败 HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("下载响应为空")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Long
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        read = input.read(buf).toLong()
                        if (read <= 0) break
                        output.write(buf, 0, read.toInt())
                        done += read
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
        }
    }

    /** 语义化版本比较：a 比 b 新返回 true（支持 1.2.3 / 1.2.3-beta） */
    internal fun isNewer(a: String, b: String): Boolean {
        fun parse(v: String): List<Int> =
            v.split('-', '+').first().split('.').map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val pa = parse(a); val pb = parse(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
