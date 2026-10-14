package com.mirainikki.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirainikki.app.data.C
import com.mirainikki.app.data.todayLocal
import com.mirainikki.app.data.DreamContent
import com.mirainikki.app.data.EntryEntity
import com.mirainikki.app.data.HealthContent
import com.mirainikki.app.data.MoodContent
import com.mirainikki.app.data.PhotoContent
import com.mirainikki.app.data.Repo
import com.mirainikki.app.data.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 快速记录/编辑弹层：onSubmit(content, tags) 的 content 为对应类型的数据对象。
 * 传入 editing 时为编辑模式（回填原内容），保存由调用方路由到 update。
 * 习惯类型不走此弹层（在 HabitsScreen 一键打卡）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(type: String, editing: EntryEntity? = null, targetDate: String = todayLocal(), onDismiss: () -> Unit, onSubmit: suspend (Any?, List<String>) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val scope = rememberCoroutineScope()

    // 编辑模式回填
    val eMood = remember { if (editing != null) runCatching { C.decode<MoodContent>(editing.contentJson) }.getOrNull() else null }
    val eText = remember { if (editing != null) runCatching { C.decode<TextContent>(editing.contentJson) }.getOrNull() else null }
    val ePhoto = remember { if (editing != null) runCatching { C.decode<PhotoContent>(editing.contentJson) }.getOrNull() else null }
    val eHealth = remember { if (editing != null) runCatching { C.decode<HealthContent>(editing.contentJson) }.getOrNull() else null }
    val eDream = remember { if (editing != null) runCatching { C.decode<DreamContent>(editing.contentJson) }.getOrNull() else null }

    var score by remember { mutableStateOf(eMood?.score ?: 4) }
    val emotions = remember {
        mutableStateListOf<String>().apply {
            eMood?.emotions?.let { addAll(it) }
            eDream?.emotions?.let { addAll(it) }
        }
    }
    var moodNote by remember { mutableStateOf(eMood?.note ?: "") }

    var title by remember { mutableStateOf(eText?.title ?: "") }
    var body by remember { mutableStateOf(when { editing != null && eDream != null -> eDream.body; else -> eText?.body ?: "" }) }

    var caption by remember { mutableStateOf(ePhoto?.caption ?: "") }
    val imageIds = remember { mutableStateListOf<String>().apply { ePhoto?.imageIds?.let { addAll(it) } } }
    val photos = remember { mutableStateListOf<Uri>() }
    var uploading by remember { mutableStateOf(false) }

    var sleep by remember { mutableStateOf(eHealth?.sleepHours?.toString() ?: "") }
    var weight by remember { mutableStateOf(eHealth?.weightKg?.toString() ?: "") }
    var steps by remember { mutableStateOf(eHealth?.steps?.toString() ?: "") }

    val selectedTags = remember { mutableStateListOf<String>().apply { addAll(C.decodeTags(editing?.tagsJson ?: "[]")) } }
    val tagOptions = remember { mutableStateListOf<String>() }
    var newTag by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) { tagOptions.addAll(repo.recentTags()) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun addNewTag() {
        val t = newTag.trim().removePrefix("#")
        if (t.isNotEmpty() && !selectedTags.contains(t)) {
            selectedTags.add(t)
            if (!tagOptions.contains(t)) tagOptions.add(0, t)
        }
        newTag = ""
    }

    val titleText = if (editing != null) "编辑记录"
        else when (type) { "mood" -> "现在心情怎么样"; "text" -> "写点什么"; "dream" -> "记一个梦"; "photo" -> "记录一个瞬间"; "health" -> "健康数据"; else -> "记录" }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        photos.clear()
        photos.addAll(uris)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (targetDate != todayLocal()) {
                Spacer(Modifier.height(4.dp))
                Text("补记到 ${backfillLabel(targetDate)}", fontSize = 12.sp, color = AccentDark)
            }
            Spacer(Modifier.height(14.dp))

            if (type == "mood") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MoodEmojis.forEachIndexed { i, e ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (score == i + 1) AccentBg else BoxBg)
                                .border(
                                    width = if (score == i + 1) 1.5.dp else 0.dp,
                                    color = if (score == i + 1) Accent else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { score = i + 1 }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(e, fontSize = 24.sp)
                            Text(MoodLabels[i], fontSize = 11.sp, color = Ink2)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("情绪词（可多选）", fontSize = 12.sp, color = Ink2)
                Spacer(Modifier.height(6.dp))
                FlowChips(Emotions, emotions)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = moodNote, onValueChange = { moodNote = it },
                    label = { Text("补充一句（可选）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }

            if (type == "text") {
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    label = { Text("想写什么写什么（#标签 自动识别）") },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }

            if (type == "photo") {
                if (!repo.api.configured && imageIds.isEmpty()) {
                    Text("📷 照片记录需要联网上传：请先在「设置」里连接服务器", fontSize = 13.sp, color = Danger)
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text("＋ 添加照片")
                }
                Spacer(Modifier.height(8.dp))
                if (imageIds.isNotEmpty() || photos.isNotEmpty()) {
                    val parts = buildList {
                        if (imageIds.isNotEmpty()) add("已有 ${imageIds.size} 张")
                        if (photos.isNotEmpty()) add("新选 ${photos.size} 张（保存时自动压缩上传）")
                    }
                    Text(parts.joinToString("，"), fontSize = 12.sp, color = Ink3)
                    if (photos.isNotEmpty()) {
                        Text("清空重选", fontSize = 12.sp, color = Danger,
                            modifier = Modifier.clickable { photos.clear() }.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = caption, onValueChange = { caption = it },
                    label = { Text("配一句话（可选）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }

            if (type == "dream") {
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    label = { Text("梦见什么了？趁还记得写下来") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("醒来情绪（可多选）", fontSize = 12.sp, color = Ink2)
                Spacer(Modifier.height(6.dp))
                FlowChips(Emotions, emotions)
            }

            if (type == "health") {
                OutlinedTextField(
                    value = sleep, onValueChange = { sleep = it },
                    label = { Text("睡眠时长（小时，如 7.5）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it },
                    label = { Text("体重（kg，可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = steps, onValueChange = { steps = it },
                    label = { Text("步数（可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("标签（可选，点选即可）", fontSize = 12.sp, color = Ink2)
            Spacer(Modifier.height(6.dp))
            if (tagOptions.isEmpty() && selectedTags.isEmpty()) {
                Text("还没有常用标签，在下面输入第一个吧", fontSize = 12.sp, color = Ink3)
            } else {
                FlowChips(tagOptions, selectedTags)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text("新标签，回车添加") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addNewTag() }),
            )

            if (err.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(err, color = Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        err = ""
                        val tags = selectedTags.toList()
                        scope.launch {
                            busy = true
                            try {
                                when (type) {
                                    "mood" -> onSubmit(MoodContent(score, emotions.toList(), moodNote.ifBlank { null }), tags)
                                    "text" -> {
                                        if (title.isBlank() && body.isBlank()) { err = "标题和正文至少填一项"; busy = false; return@launch }
                                        onSubmit(TextContent(title.ifBlank { null }, body), tags)
                                    }
                                    "dream" -> {
                                        if (body.isBlank() && emotions.isEmpty()) { err = "梦境描述和醒来情绪至少填一项"; busy = false; return@launch }
                                        onSubmit(DreamContent(body, emotions.toList()), tags)
                                    }
                                    "photo" -> {
                                        if (imageIds.isEmpty() && photos.isEmpty()) { err = "请先选择照片"; busy = false; return@launch }
                                        if (photos.isNotEmpty() && !repo.api.configured) { err = "请先在设置里连接服务器"; busy = false; return@launch }
                                        uploading = true
                                        val newIds = if (photos.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
                                            photos.map { u -> repo.api.uploadImage(compressImage(ctx, u), "image/jpeg").first }
                                        }
                                        uploading = false
                                        onSubmit(PhotoContent(caption.ifBlank { null }, imageIds.toList() + newIds), tags)
                                    }
                                    "health" -> {
                                        val h = HealthContent(
                                            sleepHours = sleep.toDoubleOrNull(),
                                            weightKg = weight.toDoubleOrNull(),
                                            steps = steps.toIntOrNull(),
                                        )
                                        if (h.sleepHours == null && h.weightKg == null && h.steps == null) {
                                            err = "至少填写一项"; busy = false; return@launch
                                        }
                                        onSubmit(h, tags)
                                    }
                                }
                            } catch (ex: Exception) {
                                err = ex.message ?: "保存失败"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDark),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy || uploading) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (uploading) "上传照片中…" else if (editing != null) "保存修改" else "保存")
                }
            }
        }
    }
}

/** 情绪词流式多选 */
@Composable
private fun FlowChips(options: List<String>, selected: MutableList<String>) {
    Column {
        options.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                rowItems.forEach { opt ->
                    val on = opt in selected
                    Text(
                        opt,
                        fontSize = 13.sp,
                        color = if (on) SelectedText else Ink2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) SelectedBg else BoxBg)
                            .clickable { if (on) selected.remove(opt) else selected.add(opt) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

private val BoxBg = androidx.compose.ui.graphics.Color(0xFFFDFCFA)
private val SelectedBg = androidx.compose.ui.graphics.Color(0xFFFDF3E3)
private val SelectedText = androidx.compose.ui.graphics.Color(0xFF9A7B3F)

/** 图片压缩：最长边 1600px，JPEG 82% */private fun compressImage(ctx: android.content.Context, uri: Uri, maxSide: Int = 1600): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    var w = bounds.outWidth; var h = bounds.outHeight
    while (w / 2 >= maxSide || h / 2 >= maxSide) { sample *= 2; w /= 2; h /= 2 }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: throw Exception("无法读取图片")
    val scaled = if (maxOf(bmp.width, bmp.height) > maxSide) {
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    } else bmp
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray()
}

/** 补记日期的短标签：昨天 或 M月d日 */
private fun backfillLabel(d: String): String {
    val today = java.time.LocalDate.now()
    val ld = java.time.LocalDate.parse(d)
    return if (ld == today.minusDays(1)) "昨天" else "${ld.monthValue}月${ld.dayOfMonth}日"
}
