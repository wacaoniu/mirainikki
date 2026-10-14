package com.mirainikki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirainikki.app.data.C
import com.mirainikki.app.data.DreamContent
import com.mirainikki.app.data.EntryEntity
import com.mirainikki.app.data.HabitCheckinContent
import com.mirainikki.app.data.HealthContent
import com.mirainikki.app.data.MoodContent
import com.mirainikki.app.data.PhotoContent
import com.mirainikki.app.data.Repo
import com.mirainikki.app.data.TextContent
import com.mirainikki.app.data.todayLocal
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(todayLocal()) }
    val entries by remember(selectedDate) { repo.byDateFlow(selectedDate) }.collectAsState(initial = emptyList())
    var sheet by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EntryEntity?>(null) }
    var shareReq by remember { mutableStateOf<ShareRequest?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📔 未来日记", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // 快速记一条：不用选类型，想写什么写什么，#标签 自动识别
        var quickText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = quickText,
            onValueChange = { quickText = it },
            placeholder = { Text("想记就记：一句话、一件事、一个念头…（#标签 会自动识别）", fontSize = 13.sp, color = Ink3) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Line,
                focusedContainerColor = Card,
                unfocusedContainerColor = Card,
            ),
            trailingIcon = {
                Text(
                    "记",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (quickText.isBlank()) Line else AccentDark)
                        .clickable(enabled = quickText.isNotBlank()) {
                            scope.launch {
                                val hashTags = Regex("#([\\w\\u4e00-\\u9fa5-]+)").findAll(quickText).map { it.groupValues[1] }.toList()
                                repo.addText(null, quickText.trim(), hashTags, selectedDate)
                                quickText = ""
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            },
        )

        // 结构化快捷入口（想分类时再用；习惯打卡在「习惯」页）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAction("💭", "心情", Modifier.weight(1f)) { sheet = "mood" }
            QuickAction("🌙", "梦境", Modifier.weight(1f)) { sheet = "dream" }
            QuickAction("📷", "照片", Modifier.weight(1f)) { sheet = "photo" }
            QuickAction("💪", "健康", Modifier.weight(1f)) { sheet = "health" }
        }

        // 日期导航 + 整天分享
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 22.sp, color = Ink2,
                modifier = Modifier.clickable { selectedDate = shiftDay(selectedDate, -1) }.padding(horizontal = 10.dp, vertical = 4.dp))
            Text(
                dayLabel(selectedDate),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            val canNext = selectedDate < todayLocal()
            Text("›", fontSize = 22.sp, color = if (canNext) Ink2 else Line,
                modifier = Modifier.clickable(enabled = canNext) { selectedDate = shiftDay(selectedDate, 1) }.padding(horizontal = 10.dp, vertical = 4.dp))
            Text("↗ 分享", fontSize = 13.sp, color = AccentDark,
                modifier = Modifier
                    .clickable { shareReq = ShareRequest.Day(selectedDate, entries) }
                    .padding(horizontal = 6.dp, vertical = 6.dp))
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (selectedDate == todayLocal()) "今天还没有记录\n从上面选一个开始吧 🌱"
                    else "这一天没有记录\n用 ‹ › 可以翻看其他日子",
                    color = Ink3, fontSize = 14.sp,
                    lineHeight = 22.sp, modifier = Modifier.padding(bottom = 80.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.clientId }) { e ->
                    EntryCardMobile(
                        e,
                        onEdit = { editing = e; sheet = e.type },
                        onShare = { shareReq = ShareRequest.Single(e) },
                        onDelete = {
                            scope.launch { repo.deleteEntry(e.clientId) }
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    shareReq?.let { req ->
        ShareSheet(req, onDismiss = { shareReq = null })
    }

    sheet?.let { type ->
        if (type == "habit") {
            HabitQuickPick(date = selectedDate, onDismiss = { sheet = null })
        } else {
            EntrySheet(
                type = type,
                editing = editing,
                targetDate = selectedDate,
                onDismiss = { sheet = null; editing = null },
                onSubmit = { content, tags ->
                    scope.launch {
                        val ed = editing
                        if (ed != null) {
                            val json = when (type) {
                                "mood" -> C.encode(content as MoodContent)
                                "text" -> C.encode(content as TextContent)
                                "dream" -> C.encode(content as DreamContent)
                                "photo" -> C.encode(content as PhotoContent)
                                else -> C.encode(content as HealthContent)
                            }
                            repo.updateEntry(ed, json, tags, ed.entryDate)
                        } else {
                            // 新记录写入当前浏览的日期（支持补记）
                            when (type) {
                                "mood" -> repo.addMood((content as MoodContent).score, content.emotions, content.note, tags, selectedDate)
                                "text" -> repo.addText((content as TextContent).title, content.body ?: "", tags, selectedDate)
                                "dream" -> repo.addDream((content as DreamContent).body, content.emotions, tags, selectedDate)
                                "photo" -> repo.addPhoto(content as PhotoContent, tags, selectedDate)
                                "health" -> {
                                    val h = content as HealthContent
                                    repo.addHealth(h.sleepHours, h.weightKg, h.steps, tags, selectedDate)
                                }
                            }
                        }
                        editing = null
                        sheet = null
                    }
                },
            )
        }
    }
}

private fun shiftDay(d: String, days: Int): String =
    LocalDate.parse(d).plusDays(days.toLong()).toString()

private fun dayLabel(d: String): String {
    val today = LocalDate.now()
    val ld = LocalDate.parse(d)
    val week = listOf("一", "二", "三", "四", "五", "六", "日")[ld.dayOfWeek.value - 1]
    val core = if (ld.year == today.year) "${ld.monthValue}月${ld.dayOfMonth}日 · 周$week"
    else "${ld.year}年${ld.monthValue}月${ld.dayOfMonth}日 · 周$week"
    return when (d) {
        today.toString() -> "今天 · $core"
        today.minusDays(1).toString() -> "昨天 · $core"
        else -> core
    }
}

@Composable
private fun QuickAction(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = Ink2)
        }
    }
}

/** 单条记录卡片（移动端） */
@Composable
fun EntryCardMobile(e: EntryEntity, onEdit: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entryEmoji(e.type), fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    e.createdAt.timeOf(),
                    fontSize = 11.sp, color = Ink3,
                )
                Spacer(Modifier.weight(1f))
                if (e.type != C.TYPE_HABIT) {
                    Text("编辑", fontSize = 12.sp, color = Ink2, modifier = Modifier.clickable { onEdit() })
                    Spacer(Modifier.width(12.dp))
                }
                Text("分享", fontSize = 12.sp, color = AccentDark, modifier = Modifier.clickable { onShare() })
                Spacer(Modifier.width(12.dp))
                Text("删除", fontSize = 12.sp, color = Danger, modifier = Modifier.clickable { onDelete() })
            }
            Spacer(Modifier.height(6.dp))
            EntryBody(e)
            val tags = C.decodeTags(e.tagsJson)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t ->
                        Text(
                            "#$t", fontSize = 11.sp, color = AccentDark,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(AccentBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryBody(e: EntryEntity) {
    when (e.type) {
        C.TYPE_MOOD -> {
            val m = remember(e.clientId, e.updatedAt) { runCatching { C.decode<MoodContent>(e.contentJson) }.getOrNull() } ?: return
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(MoodEmojis.getOrElse(m.score - 1) { "😐" }, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("心情 ${m.score}/5 · ${MoodLabels.getOrElse(m.score - 1) { "一般" }}",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (m.emotions.isNotEmpty()) {
                        Text(m.emotions.joinToString("、"), fontSize = 12.sp, color = Ink2)
                    }
                }
            }
            m.note?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 14.sp, color = Ink)
            }
        }
        C.TYPE_TEXT -> {
            val t = remember(e.clientId, e.updatedAt) { runCatching { C.decode<TextContent>(e.contentJson) }.getOrNull() } ?: return
            t.title?.let {
                Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
            }
            Text(t.body ?: "", fontSize = 14.sp, lineHeight = 21.sp)
        }
        C.TYPE_PHOTO -> {
            val p = remember(e.clientId, e.updatedAt) { runCatching { C.decode<PhotoContent>(e.contentJson) }.getOrNull() } ?: return
            val repo = Repo.get(LocalContext.current)
            // 优先显示本地文件（离线/刚记录完立即能看），本地没有的再走服务器
            val models: List<Any> = buildList {
                p.localFiles.forEach { path -> if (java.io.File(path).exists()) add(java.io.File(path)) }
                val localIds = p.localFileIds.toSet()
                p.imageIds.filter { it !in localIds }.forEach { id -> add(repo.api.serverUrl + "/api/images/$id/file") }
            }
            if (models.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.take(3).forEach { m ->
                        coil.compose.AsyncImage(
                            model = m,
                            contentDescription = "照片",
                            modifier = Modifier
                                .size(width = 96.dp, height = 96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Line),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            p.caption?.let {
                Text(it, fontSize = 14.sp)
            }
        }
        C.TYPE_HABIT -> {
            val h = remember(e.clientId, e.updatedAt) { runCatching { C.decode<HabitCheckinContent>(e.contentJson) }.getOrNull() } ?: return
            Text("✅ 完成打卡 · ${h.habitName ?: "习惯"}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AccentDark)
        }
        C.TYPE_HEALTH -> {
            val h = remember(e.clientId, e.updatedAt) { runCatching { C.decode<HealthContent>(e.contentJson) }.getOrNull() } ?: return
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                h.sleepHours?.let { HealthCell("😴 ${it}", "睡眠(小时)") }
                h.weightKg?.let { HealthCell("⚖️ ${it}", "体重(kg)") }
                h.steps?.let { HealthCell("👟 ${it}", "步数") }
            }
        }
        C.TYPE_DREAM -> {
            val d = remember(e.clientId, e.updatedAt) { runCatching { C.decode<DreamContent>(e.contentJson) }.getOrNull() } ?: return
            if (d.body.isNotBlank()) {
                Text(d.body, fontSize = 14.sp, lineHeight = 21.sp)
            }
            if (d.emotions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("醒来：", fontSize = 12.sp, color = Ink3)
                    d.emotions.forEach { em ->
                        Text(
                            em, fontSize = 12.sp, color = SelectedEmotionColor,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(EmotionBg)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private val EmotionBg = androidx.compose.ui.graphics.Color(0xFFFDF3E3)
private val SelectedEmotionColor = androidx.compose.ui.graphics.Color(0xFF9A7B3F)

@Composable
private fun HealthCell(v: String, k: String) {
    Column {
        Text(v, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(k, fontSize = 11.sp, color = Ink3)
    }
}

fun entryEmoji(type: String) = when (type) {
    C.TYPE_MOOD -> "💭"; C.TYPE_TEXT -> "📝"; C.TYPE_PHOTO -> "📷"
    C.TYPE_HABIT -> "✅"; C.TYPE_HEALTH -> "💪"; C.TYPE_DREAM -> "🌙"; else -> "📌"
}

fun String.timeOf(): String = try {
    java.time.Instant.parse(this).atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
} catch (_: Exception) { "" }
