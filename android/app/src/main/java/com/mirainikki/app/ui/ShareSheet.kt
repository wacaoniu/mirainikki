package com.mirainikki.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mirainikki.app.data.C
import com.mirainikki.app.data.DreamContent
import com.mirainikki.app.data.EntryEntity
import com.mirainikki.app.data.HealthContent
import com.mirainikki.app.data.HabitCheckinContent
import com.mirainikki.app.data.MoodContent
import com.mirainikki.app.data.PhotoContent
import com.mirainikki.app.data.Repo
import com.mirainikki.app.data.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** 分享请求：整天或单条 */
sealed class ShareRequest {
    data class Day(val date: String, val entries: List<EntryEntity>) : ShareRequest()
    data class Single(val entry: EntryEntity) : ShareRequest()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(req: ShareRequest, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val scope = rememberCoroutineScope()
    val plain = remember { buildShareText(req) }
    val md = remember { buildShareMarkdown(req) }
    val title = when (req) {
        is ShareRequest.Day -> "分享 ${friendlyDate(req.date)} 的日记"
        is ShareRequest.Single -> "分享这条记录"
    }
    val photoSources = remember(req) {
        reqEntries(req).filter { it.type == C.TYPE_PHOTO }.flatMap { e ->
            val c = runCatching { C.decode<PhotoContent>(e.contentJson) }.getOrNull() ?: return@flatMap emptyList()
            val locals = c.localFiles.filter { java.io.File(it).exists() }
            val localIds = c.localFileIds.toSet()
            locals.map { PhotoSource.Local(it) } +
                c.imageIds.filter { it !in localIds }.map { PhotoSource.Server(it) }
        }
    }
    var photoBusy by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            // 预览（可手动选择复制）
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFCFA)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        plain,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Ink2,
                        modifier = Modifier
                            .padding(12.dp)
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("diary", plain))
                    Toast.makeText(ctx, "已复制，可直接粘贴", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentDark),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📋  一键复制文本") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { shareText(ctx, plain, title) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("💬  分享文本（微信 / QQ …）") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { shareMarkdownFile(ctx, req, md) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📄  导出 / 分享 .md 文件") }

            if (photoSources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        photoBusy = true
                        scope.launch {
                            try {
                                val uris = withContext(Dispatchers.IO) {
                                    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                                    photoSources.map { src ->
                                        when (src) {
                                            is PhotoSource.Local -> {
                                                // 本地文件直接复制一份到分享目录（保留原始文件名后缀）
                                                val srcFile = File(src.path)
                                                val f = File(dir, srcFile.name)
                                                srcFile.copyTo(f, overwrite = true)
                                                FileProvider.getUriForFile(ctx, "com.mirainikki.app.fileprovider", f)
                                            }
                                            is PhotoSource.Server -> {
                                                val bytes = repo.api.downloadImage(src.id)
                                                val ext = if (bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) "png" else "jpg"
                                                val f = File(dir, "${src.id}.$ext")
                                                f.writeBytes(bytes)
                                                FileProvider.getUriForFile(ctx, "com.mirainikki.app.fileprovider", f)
                                            }
                                        }
                                    }
                                }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "image/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(intent, "分享 ${uris.size} 张照片"))
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "照片下载失败：${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                photoBusy = false
                            }
                        }
                    },
                    enabled = !photoBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (photoBusy) "准备照片中…" else "🖼  分享照片原图（${photoSources.size} 张）") }
            }
        }
    }
}

/** 分享照片的来源：优先本地文件，其次服务端 */
sealed class PhotoSource {
    data class Local(val path: String) : PhotoSource()
    data class Server(val id: String) : PhotoSource()
}

private fun shareText(ctx: Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(intent, title))
}

private fun shareMarkdownFile(ctx: Context, req: ShareRequest, md: String) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val date = when (req) {
        is ShareRequest.Day -> req.date
        is ShareRequest.Single -> req.entry.entryDate
    }
    val file = File(dir, "未来日记-$date.md")
    file.writeText(md, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(ctx, "com.mirainikki.app.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "分享 Markdown 文件"))
}

// ---------- 格式化 ----------

private fun friendlyDate(d: String): String {
    val ld = LocalDate.parse(d)
    return "${ld.monthValue}月${ld.dayOfMonth}日"
}

private fun fullDate(d: String): String {
    val ld = LocalDate.parse(d)
    val week = listOf("一", "二", "三", "四", "五", "六", "日")[ld.dayOfWeek.value - 1]
    return "${ld.year}年${ld.monthValue}月${ld.dayOfMonth}日 · 周$week"
}

private fun moodWord(score: Int?): String =
    MoodLabels.getOrElse((score ?: 3) - 1) { "一般" }

/** 单条记录 → head（含时间图标标签）+ 正文行 */
private fun describe(e: EntryEntity): Pair<String, List<String>> {
    val time = e.createdAt.timeOf()
    val icon = entryEmoji(e.type)
    val tags = C.decodeTags(e.tagsJson)
    val tagStr = if (tags.isEmpty()) "" else "（${tags.joinToString(" ") { "#$it" }}）"
    return when (e.type) {
        C.TYPE_MOOD -> {
            val m = runCatching { C.decode<MoodContent>(e.contentJson) }.getOrNull()
            val em = m?.emotions?.takeIf { it.isNotEmpty() }?.joinToString("、")
            "$time $icon 心情 ${m?.score ?: "?"}/5 · ${moodWord(m?.score)}${em?.let { "（$it）" } ?: ""}$tagStr" to
                listOfNotNull(m?.note?.takeIf { it.isNotBlank() })
        }
        C.TYPE_TEXT -> {
            val t = runCatching { C.decode<TextContent>(e.contentJson) }.getOrNull()
            "$time $icon ${t?.title?.takeIf { it.isNotBlank() } ?: "日记"}$tagStr" to
                (t?.body?.lines()?.filter { it.isNotBlank() } ?: emptyList())
        }
        C.TYPE_PHOTO -> {
            val p = runCatching { C.decode<PhotoContent>(e.contentJson) }.getOrNull()
            "$time $icon 照片 ×${p?.imageIds?.size ?: 0}$tagStr" to
                listOfNotNull(p?.caption?.takeIf { it.isNotBlank() })
        }
        C.TYPE_HABIT -> {
            val h = runCatching { C.decode<HabitCheckinContent>(e.contentJson) }.getOrNull()
            "$time $icon 习惯打卡 · ${h?.habitName ?: "习惯"}$tagStr" to emptyList()
        }
        C.TYPE_HEALTH -> {
            val h = runCatching { C.decode<HealthContent>(e.contentJson) }.getOrNull()
            val segs = buildList {
                h?.sleepHours?.let { add("睡眠 $it 小时") }
                h?.weightKg?.let { add("体重 $it kg") }
                h?.steps?.let { add("$it 步") }
            }
            "$time $icon 健康${segs.joinToString(" · ").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}$tagStr" to emptyList()
        }
        C.TYPE_DREAM -> {
            val d = runCatching { C.decode<DreamContent>(e.contentJson) }.getOrNull()
            val em = d?.emotions?.takeIf { it.isNotEmpty() }?.joinToString("、")
            "$time $icon 梦${em?.let { "（醒来：$it）" } ?: ""}$tagStr" to
                listOfNotNull(d?.body?.takeIf { it.isNotBlank() })
        }
        else -> "$time 📌 记录$tagStr" to emptyList()
    }
}

private fun reqEntries(req: ShareRequest): List<EntryEntity> = when (req) {
    is ShareRequest.Day -> req.entries.sortedBy { it.createdAt }
    is ShareRequest.Single -> listOf(req.entry)
}

fun buildShareText(req: ShareRequest): String {
    val sb = StringBuilder()
    if (req is ShareRequest.Day) {
        sb.append("📔 ${fullDate(req.date)}\n\n")
    }
    for (e in reqEntries(req)) {
        val (head, body) = describe(e)
        sb.append(head).append('\n')
        for (line in body) sb.append("　").append(line).append('\n')
        sb.append('\n')
    }
    sb.append("—— 来自「未来日记」")
    return sb.toString().trimEnd('\n').replace("\n\n\n", "\n\n")
}

fun buildShareMarkdown(req: ShareRequest): String {
    val sb = StringBuilder()
    if (req is ShareRequest.Day) {
        sb.append("# ${fullDate(req.date)}\n\n")
    }
    for (e in reqEntries(req)) {
        val (head, body) = describe(e)
        // head 里的 #标签 转义避免被当标题
        sb.append("- ").append(head).append('\n')
        for (line in body) sb.append("  ").append(line).append('\n')
    }
    sb.append("\n*—— 来自「未来日记」*")
    return sb.toString()
}
