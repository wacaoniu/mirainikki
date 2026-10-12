package com.zhiji.app.ui

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
import com.zhiji.app.data.Repo
import com.zhiji.app.data.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val configured = repo.api.configured

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
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("关于「知己」", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 所有记录先保存在手机本地，联网后自动同步到你的服务器（每 15 分钟）\n" +
                        "· 电脑打开 服务器地址 即可回看日记、查看统计、生成 AI 记忆档案\n" +
                        "· 数据完全属于你：服务器上就是 SQLite 文件 + 图片目录，拷走即备份",
                    fontSize = 13.sp, color = Ink2, lineHeight = 21.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
