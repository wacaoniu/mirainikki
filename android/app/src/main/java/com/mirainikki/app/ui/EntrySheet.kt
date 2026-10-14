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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 快速记录/编辑弹层：onSubmit(content, tags) 的 content 为对应类型的数据对象。
 * 传入 editing 时为编辑模式（回填原内容），保存由调用方路由到 update。
 * 习惯类型不走此弹层（在 HabitsScreen 一键打卡）。
 *
 * 布局：内容区可滚动，底部【取消/保存】固定在弹层底部 —— 不用滑到底才能存。
 * 草稿：新建记录写到一半关掉，下次打开自动恢复（保存在本地，保存成功后清除）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(type: String, editing: EntryEntity? = null, targetDate: String = todayLocal(), onDismiss: () -> Unit, onSubmit: suspend (Any?, List<String>) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val scope = rememberCoroutineScope()
    val prefs = repo.api.prefs
    val draftKey = "draft_$type"

    // 编辑模式回填
    val eMood = remember { if (editing != null) runCatching { C.decode<MoodContent>(editing.contentJson) }.getOrNull() else null }
    val eText = remember { if (editing != null) runCatching { C.decode<TextContent>(editing.contentJson) }.getOrNull() else null }
    val ePhoto = remember { if (editing != null) runCatching { C.decode<PhotoContent>(editing.contentJson) }.getOrNull() else null }
    val eHealth = remember { if (editing != null) runCatching { C.decode<HealthContent>(editing.contentJson) }.getOrNull() else null }
    val eDream = remember { if (editing != null) runCatching { C.decode<DreamContent>(editing.contentJson) }.getOrNull() else null }

    // 草稿（仅新建记录时启用；编辑已有记录不受影响）
    val draftObj: JsonObject? = remember {
        if (editing != null) null
        else prefs.getString(draftKey, null)?.let { runCatching { C.json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
    fun dStr(k: String): String? = (draftObj?.get(k) as? JsonPrimitive)?.contentOrNull
    fun dInt(k: String): Int? = dStr(k)?.toIntOrNull()
    fun dList(k: String): List<String> = (draftObj?.get(k) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    var score by remember { mutableStateOf(dInt("score") ?: eMood?.score ?: 4) }
    val emotions = remember {
        mutableStateListOf<String>().apply {
            eMood?.emotions?.let { addAll(it) }
            eDream?.emotions?.let { addAll(it) }
            if (editing == null) addAll(dList("emotions"))
        }
    }
    var moodNote by remember { mutableStateOf(dStr("note") ?: eMood?.note ?: "") }

    var title by remember { mutableStateOf(eText?.title ?: "") }
    var body by remember {
        mutableStateOf(
            when {
                editing != null && eDream != null -> eDream.body
                editing != null -> eText?.body ?: ""
                else -> dStr("body") ?: ""
            }
        )
    }

    var caption by remember { mutableStateOf(dStr("caption") ?: ePhoto?.caption ?: "") }
    val existingImageIds = remember { ePhoto?.imageIds ?: emptyList() }
    val existingLocal = remember { ePhoto?.localFiles ?: emptyList() }
    val photos = remember { mutableStateListOf<Uri>() }
    var uploading by remember { mutableStateOf(false) }

    var sleep by remember { mutableStateOf(dStr("sleep") ?: eHealth?.sleepHours?.toString() ?: "") }
    var weight by remember { mutableStateOf(dStr("weight") ?: eHealth?.weightKg?.toString() ?: "") }
    var steps by remember { mutableStateOf(dStr("steps") ?: eHealth?.steps?.toString() ?: "") }

    val selectedTags = remember {
        mutableStateListOf<String>().apply {
            if (editing != null) addAll(C.decodeTags(editing.tagsJson)) else addAll(dList("tags"))
        }
    }
    val tagOptions = remember { mutableStateListOf<String>() }
    var newTag by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { tagOptions.addAll(repo.recentTags()) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var showDraftHint by remember { mutableStateOf(draftObj != null) }

    fun addNewTag() {
        val t = newTag.trim().removePrefix("#")
        if (t.isNotEmpty() && !selectedTags.contains(t)) {
            selectedTags.add(t)
            if (!tagOptions.contains(t)) tagOptions.add(0, t)
        }
        newTag = ""
    }

    /** 放弃草稿：清空草稿与表单 */
    fun clearDraft() {
        prefs.edit().remove(draftKey).apply()
        showDraftHint = false
        score = 4
        emotions.clear()
        moodNote = ""
        body = ""
        caption = ""
        sleep = ""; weight = ""; steps = ""
        selectedTags.clear()
    }

    // 自动存草稿：内容有实质才写（关掉/关 App 都不丢；保存成功后清除）
    LaunchedEffect(score, emotions.toList(), moodNote, body, caption, sleep, weight, steps, selectedTags.toList()) {
        if (editing != null || saved) return@LaunchedEffect
        val meaningful = emotions.isNotEmpty() || moodNote.isNotBlank() || body.isNotBlank() ||
            caption.isNotBlank() || sleep.isNotBlank() || weight.isNotBlank() || steps.isNotBlank() ||
            selectedTags.isNotEmpty() || (type == "mood" && score != 4)
        if (!meaningful) return@LaunchedEffect
        val obj = buildJsonObject {
            put("score", score)
            put("emotions", buildJsonArray { emotions.forEach { add(it) } })
            put("note", moodNote)
            put("body", body)
            put("caption", caption)
            put("sleep", sleep)
            put("weight", weight)
            put("steps", steps)
            put("tags", buildJsonArray { selectedTags.forEach { add(it) } })
        }
        prefs.edit().putString(draftKey, obj.toString()).apply()
    }

    val titleText = if (editing != null) "编辑记录"
        else when (type) { "mood" -> "现在心情怎么样"; "text" -> "写点什么"; "dream" -> "记一个梦"; "photo" -> "记录一个瞬间"; "health" -> "健康数据"; else -> "记录" }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        photos.clear()
        photos.addAll(uris)
    }

    // 打开即完整展开（默认是半屏，按钮会藏在下面，还得手动拖一下）
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (targetDate != todayLocal()) {
                    Spacer(Modifier.height(4.dp))
                    Text("补记到 ${backfillLabel(targetDate)}", fontSize = 12.sp, color = AccentDark)
                }
                if (showDraftHint) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("已恢复上次未写完的内容", fontSize = 12.sp, color = AccentDark)
                        Spacer(Modifier.width(10.dp))
                        Text("清空", fontSize = 12.sp, color = Danger,
                            modifier = Modifier.clickable { clearDraft() })
                    }
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
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                }

                if (type == "photo") {
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("＋ 添加照片")
                    }
                    Spacer(Modifier.height(10.dp))
                    // 缩略图：已保存的本地照片 + 服务端照片 + 刚选的，立即显示
                    val thumbs: List<Any> = buildList {
                        existingLocal.forEach { add(java.io.File(it)) }
                        val localIds = (ePhoto?.localFileIds ?: emptyList()).toSet()
                        existingImageIds.filter { it !in localIds }.forEach { id -> add(repo.api.serverUrl + "/api/images/$id/file") }
                        addAll(photos)
                    }
                    if (thumbs.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            thumbs.take(6).forEach { m ->
                                coil.compose.AsyncImage(
                                    model = m,
                                    contentDescription = "照片",
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("共 ${thumbs.size} 张 · 保存后立即能看，联网后自动同步", fontSize = 12.sp, color = Ink3)
                        if (photos.isNotEmpty()) {
                            Text("清空重选", fontSize = 12.sp, color = Danger,
                                modifier = Modifier.clickable { photos.clear() }.padding(top = 2.dp))
                        }
                    } else {
                        Text("拍一张或从相册选，不联网也能先记下", fontSize = 12.sp, color = Ink3)
                    }
                    Spacer(Modifier.height(12.dp))
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
                        modifier = Modifier.fillMaxWidth().height(140.dp),
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
                Spacer(Modifier.height(12.dp))
            }

            // 固定在底部的操作区：不用滚动就能点到保存
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                                        if (existingImageIds.isEmpty() && existingLocal.isEmpty() && photos.isEmpty()) {
                                            err = "请先选择照片"; busy = false; return@launch
                                        }
                                        uploading = true
                                        // 1) 压缩后存到手机本地 —— 不联网也能记录、立即可看
                                        val newLocalPaths = withContext(Dispatchers.IO) {
                                            photos.map { u -> repo.saveLocalPhoto(compressImage(ctx, u)) }
                                        }
                                        // 2) 已连服务器时顺手上传；失败也没关系，后续同步会自动补传
                                        val newIds = mutableListOf<String>()
                                        val newLocalIds = mutableListOf<String>()
                                        withContext(Dispatchers.IO) {
                                            newLocalPaths.forEach { p ->
                                                if (!repo.api.configured) {
                                                    newLocalIds.add("")
                                                    return@forEach
                                                }
                                                runCatching {
                                                    val (id, _) = repo.api.uploadImage(java.io.File(p).readBytes(), "image/jpeg")
                                                    newIds.add(id)
                                                    newLocalIds.add(id)
                                                }.onFailure { newLocalIds.add("") }
                                            }
                                        }
                                        uploading = false
                                        val content = PhotoContent(
                                            caption = caption.ifBlank { null },
                                            imageIds = (existingImageIds + newIds).distinct(),
                                            localFiles = existingLocal + newLocalPaths,
                                            localFileIds = (ePhoto?.localFileIds ?: emptyList()) + newLocalIds,
                                        )
                                        onSubmit(content, tags)
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
                                // 保存成功：清掉草稿（下次打开是干净的）
                                if (editing == null) {
                                    saved = true
                                    prefs.edit().remove(draftKey).apply()
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

/** 图片压缩：最长边 1600px，JPEG 82% */
private fun compressImage(ctx: android.content.Context, uri: Uri, maxSide: Int = 1600): ByteArray {
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
