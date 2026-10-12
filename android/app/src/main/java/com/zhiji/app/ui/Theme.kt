package com.zhiji.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFFFAF9F7)
val Card = Color(0xFFFFFFFF)
val Ink = Color(0xFF2C2A26)
val Ink2 = Color(0xFF6F6A61)
val Ink3 = Color(0xFFA39D92)
val Line = Color(0xFFECE8E2)
val Accent = Color(0xFF7C9A6D)
val AccentDark = Color(0xFF5F7D52)
val AccentBg = Color(0xFFECF2E8)
val Danger = Color(0xFFD66A5C)

val MoodColors = listOf(Color(0xFFEF5350), Color(0xFFFF8A65), Color(0xFFFFD54F), Color(0xFF9CCC65), Color(0xFF66BB6A))
val MoodEmojis = listOf("😢", "😕", "😐", "🙂", "😄")
val MoodLabels = listOf("糟糕", "较差", "一般", "不错", "很好")

val Emotions = listOf("开心", "平静", "满足", "兴奋", "幸福", "期待", "感动", "惊喜", "疲惫", "焦虑", "烦躁", "委屈", "孤独", "悲伤", "愤怒", "无力")

@Composable
fun ShadoujiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AccentDark,
            background = Bg,
            surface = Card,
            onBackground = Ink,
            onSurface = Ink,
            outline = Line,
        ),
        content = content,
    )
}
