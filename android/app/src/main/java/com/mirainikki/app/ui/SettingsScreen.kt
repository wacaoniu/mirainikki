package com.mirainikki.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirainikki.app.data.Repo
import com.mirainikki.app.data.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import com.mirainikki.app.BuildConfig
import kotlinx.coroutines.withContext

/** 设置页：服务器连接 · 手动同步 · 说明 */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(repo.api.serverUrl) }
    var password by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Pair<List<com.mirainikki.app.data.LegacyImport.Parsed>, List<String>>?>(null) }
    var backupMsg by remember { mutableStateOf("") }
    // 检查更新：只在用户点"检查更新"时才联网，绝不自动/开屏检查
    var updateState by remember { mutableStateOf("") }
    var updateBusy by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf<com.mirainikki.app.data.UpdateChecker.ReleaseInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadPct by remember { mutableStateOf(0) }
    val configured = repo.api.configured

    suspend fun doCheckUpdate() {
        updateBusy = true
        updateState = "检查中…"
        pendingRelease = null
        val r = withContext(Dispatchers.IO) {
            runCatching { com.mirainikki.app.data.UpdateChecker.check(BuildConfig.VERSION_NAME) }
        }
        r.onSuccess { info ->
            if (info == null) updateState = "✓ 已是最新版本（v${BuildConfig.VERSION_NAME}）"
            else { pendingRelease = info; updateState = "发现新版本 v${info.version}" }
        }.onFailure {
            updateState = "✗ 检查失败：${it.message ?: "网络异常"}"
        }
        updateBusy = false
    }

    suspend fun doDownloadAndInstall(info: com.mirainikki.app.data.UpdateChecker.ReleaseInfo) {
        val url = info.apkUrl ?: run { updateState = "✗ 该版本没有提供 APK"; return }
        downloading = true
        downloadPct = 0
        updateState = "下载中 0%"
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val dest = java.io.File(ctx.cacheDir, "update/MiraiNikki-${info.version}.apk")
                com.mirainikki.app.data.UpdateChecker.downloadApk(url, dest) { pct -> downloadPct = pct }
                dest
            }
        }
        downloading = false
        r.onSuccess { file ->
            updateState = "✓ 下载完成，请按系统提示安装"
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "com.mirainikki.app.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } catch (e: Exception) {
                updateState = "✗ 无法启动安装：${e.message}"
            }
        }.onFailure {
            updateState = "✗ 下载失败：${it.message ?: "网络异常"}"
        }
    }


    // 导出备份：CreateDocument 生成 json 到用户选的位置（下载/网盘均可）
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val json = repo.exportBackupJson()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: throw Exception("无法写入文件")
                withContext(Dispatchers.Main) { backupMsg = "✓ 备份已导出（包含全部记录与习惯，妥善保存此文件）" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { backupMsg = "✗ 导出失败：${e.message}" }
            }
        }
    }

    // 恢复备份 / 导入：同一个选择器，json 走完整恢复，md/txt 走旧日记导入
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true; importResult = null; backupMsg = ""
        scope.launch(Dispatchers.IO) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
                    ?: throw Exception("无法读取文件")
                val trimmed = text.trim()
                if (trimmed.startsWith("{") && trimmed.contains("\"entries\"")) {
                    // 全量备份 → 完整恢复
                    val (e, h) = repo.restoreBackupJson(text)
                    withContext(Dispatchers.Main) {
                        backupMsg = "✓ 恢复完成：记录 $e 条，习惯 $h 个（与备份中相同的旧数据自动跳过）"
                    }
                } else {
                    val (parsed, unparsed) = com.mirainikki.app.data.LegacyImport.parse(text, com.mirainikki.app.data.todayLocal())
                    for (p in parsed) {
                        val hashTags = Regex("#([\\w\\u4e00-\\u9fa5-]+)").findAll(p.body).map { it.groupValues[1] }.toList()
                        repo.addText(null, p.body, hashTags, p.entryDate)
                    }
                    withContext(Dispatchers.Main) { importResult = parsed to unparsed }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupMsg = "✗ 处理失败：${e.message}"
                    importResult = emptyList<com.mirainikki.app.data.LegacyImport.Parsed>() to listOf(e.message ?: "读取失败")
                }
            } finally {
                withContext(Dispatchers.Main) { importing = false }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text("⚙️ 设置", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("服务器连接", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (configured) "已连接：${repo.api.serverUrl}" else "未连接。填入自建后端地址和密码登录。",
                    fontSize = 12.sp, color = if (configured) AccentDark else Ink3,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("如 http://192.168.1.10:8787") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = {
                            busy = true; msg = ""
                            scope.launch(Dispatchers.IO) {
                                try {
                                    repo.api.serverUrl = serverUrl.trim().trimEnd('/')
                                    val token = repo.api.login(password)
                                    repo.api.token = token
                                    SyncWorker.schedule(ctx)
                                    withContext(Dispatchers.Main) { msg = "✓ 连接成功，已开启自动同步" }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { msg = "✗ ${e.message}" }
                                } finally {
                                    withContext(Dispatchers.Main) { busy = false }
                                }
                            }
                        },
                        enabled = !busy && serverUrl.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentDark),
                    ) {
                        Text(if (configured) "重新连接" else "连接")
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            syncing = true; msg = ""
                            scope.launch {
                                val r = repo.syncOnce()
                                msg = if (r.error != null) "✗ 同步失败：${r.error}" else "✓ 同步完成：推送 ${r.pushed} 条，拉取 ${r.pulled} 条"
                                syncing = false
                            }
                        },
                        enabled = configured && !syncing,
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("立即同步")
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(msg, fontSize = 13.sp, color = if (msg.startsWith("✗")) Danger else AccentDark)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 数据：导入 / 备份（低频操作，收成两行小入口）
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                DataRow(
                    icon = "📥", title = "导入旧日记 / 恢复备份",
                    subtitle = "支持 .md / .txt / .json（备份 json 可完整恢复）",
                    onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 0.dp).background(Line))
                DataRow(
                    icon = "📤", title = "导出备份",
                    subtitle = "全部记录存成一个 json，建议每周一次",
                    onClick = { backupSaver.launch("未来日记备份-${com.mirainikki.app.data.todayLocal()}.json") },
                )
            }
        }
        if (importing) {
            Spacer(Modifier.height(6.dp))
            Text("解析导入中…", fontSize = 13.sp, color = Ink3, modifier = Modifier.padding(horizontal = 4.dp))
        }
        importResult?.let { r ->
            Spacer(Modifier.height(6.dp))
            Text(
                if (r.second.isEmpty()) "✓ 导入 ${r.first.size} 条（${r.first.minOfOrNull { it.entryDate }} ~ ${r.first.maxOfOrNull { it.entryDate }}）"
                else "✓ 导入 ${r.first.size} 条，${r.second.size} 段无法识别（无日期行）",
                fontSize = 13.sp, color = AccentDark, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (backupMsg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(backupMsg, fontSize = 13.sp, color = if (backupMsg.startsWith("✗")) Danger else AccentDark,
                modifier = Modifier.padding(horizontal = 4.dp))
        }

        Spacer(Modifier.height(14.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("关于「未来日记」MiraiNikki", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 随手记的都在手机里，连上服务器就自动同步（每 15 分钟）\n" +
                        "· 电脑打开服务器地址，回看、统计、AI 档案都有\n" +
                        "· 数据是你的：服务器上就俩文件夹，拷走就是备份",
                    fontSize = 13.sp, color = Ink2, lineHeight = 21.sp,
                )

                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                DataRow(
                    icon = "🔄",
                    title = "检查更新",
                    subtitle = when {
                        downloading -> "下载中 $downloadPct%"
                        updateState.isNotEmpty() -> updateState
                        else -> "当前版本 v${BuildConfig.VERSION_NAME}（只在你手点时才联网检查）"
                    },
                    onClick = { if (!updateBusy && !downloading) scope.launch { doCheckUpdate() } },
                )
                pendingRelease?.let { info ->
                    Spacer(Modifier.height(8.dp))
                    Text("更新说明：", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        info.notes.ifBlank { "（该版本没有写更新说明）" }.take(600),
                        fontSize = 12.5.sp, color = Ink2, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { scope.launch { doDownloadAndInstall(info) } },
                            enabled = !downloading,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentDark),
                        ) { Text(if (downloading) "下载中 $downloadPct%" else "下载并安装") }
                        OutlinedButton(onClick = {
                            runCatching {
                                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.pageUrl)))
                            }
                        }) { Text("去网页看") }
                        OutlinedButton(onClick = { pendingRelease = null; updateState = "" }) { Text("忽略") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 紧凑设置行：图标 + 标题/副标题，整行可点 */
@Composable
private fun DataRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 0.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 17.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.5.sp, color = Ink3)
        }
        Text("›", fontSize = 16.sp, color = Ink3)
    }
}
