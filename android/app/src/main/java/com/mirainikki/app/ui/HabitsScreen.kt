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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.mirainikki.app.data.HabitEntity
import com.mirainikki.app.data.Repo
import kotlinx.coroutines.launch

/** 习惯页：今日一键打卡 + 管理 */
@Composable
fun HabitsScreen() {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val scope = rememberCoroutineScope()
    val habits by repo.habitsFlow().collectAsState(initial = emptyList())
    var checked by remember { mutableStateOf(emptySet<String>()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { checked = repo.todayCheckinDates() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✅ 习惯", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("＋ 新习惯", color = AccentDark, fontSize = 14.sp, modifier = Modifier.clickable { showAdd = true })
        }

        if (habits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有习惯\n点右上角「新习惯」创建一个\n比如：阅读 · 跑步 · 早睡 🌱",
                    color = Ink3, fontSize = 14.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 60.dp))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(habits, key = { it.clientId }) { h ->
                    val done = h.clientId in checked
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(h.emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(if (done) "今天已打卡 ✓" else "今天还没打卡", fontSize = 12.sp,
                                    color = if (done) AccentDark else Ink3)
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (done) Accent else Color(0xFFF2EFE9))
                                    .clickable(enabled = !done) {
                                        scope.launch {
                                            repo.addHabitCheckin(h)
                                            checked = checked + h.clientId
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (done) "✓" else "打卡", color = Color.White, fontSize = if (done) 18.sp else 11.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("删除", fontSize = 12.sp, color = Ink3,
                                modifier = Modifier.clickable {
                                    scope.launch { repo.deleteHabit(h) }
                                })
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showAdd) {
        HabitAddDialog(onDismiss = { showAdd = false }, onAdd = { name, emoji ->
            scope.launch { repo.addHabit(name, emoji) }
            showAdd = false
        })
    }
}

/** 首页快捷「习惯」入口：弹层快速打卡（记入当前浏览的日期） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitQuickPick(date: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val scope = rememberCoroutineScope()
    val habits by repo.habitsFlow().collectAsState(initial = emptyList())
    val checked by produceState(initialValue = emptySet<String>(), date) { value = repo.checkinDatesOn(date) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("完成打卡", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (habits.isEmpty()) {
                Text("还没有习惯，去「习惯」页创建", color = Ink3, fontSize = 14.sp)
            }
            habits.forEach { h ->
                val done = h.clientId in checked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (done) AccentBg else Color.White)
                        .clickable(enabled = !done) {
                            scope.launch {
                                repo.addHabitCheckin(h, date)
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(h.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(h.name, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (done) "已打卡 ✓" else "点击打卡", fontSize = 13.sp, color = if (done) AccentDark else Ink3)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HabitAddDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("✅") }
    val emojiOptions = listOf("📚", "🏃", "💪", "🧘", "💧", "🌅", "🎸", "✍️", "🥗", "😴", "🚭", "💰")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新习惯") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("习惯名称，如：阅读") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text("选个图标", fontSize = 12.sp, color = Ink2)
                Spacer(Modifier.height(6.dp))
                Column {
                    emojiOptions.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            row.forEach { e ->
                                Text(
                                    e, fontSize = 22.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (emoji == e) AccentBg else Color.Transparent)
                                        .clickable { emoji = e }
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim(), emoji) }) { Text("创建", color = AccentDark) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
