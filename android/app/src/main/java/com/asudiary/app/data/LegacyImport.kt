package com.asudiary.app.data

/**
 * 旧日记文本解析（与 web/src/lib/legacyImport.ts 同逻辑）：
 * - JSON（全量备份格式）直接解析
 * - 文本按日期行分天：# 2026-01-15 / 2026年1月15日 / 2026/1/15 / [2026-01-15] / ISO 行
 */
object LegacyImport {

    data class Parsed(val entryDate: String, val body: String)

    private val DATE_PATTERNS = listOf(
        Regex("^#{1,3}\\s*(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?\\s*.*$"),
        Regex("^[(\\[]\\s*(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\s*[)\\]].*$"),
        Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\s*[（(]?周[一二三四五六日天][)）]?\\s*$"),
        Regex("^\\s*(\\d{4})-(\\d{2})-(\\d{2})\\s*$"),
    )

    /** 预编译失败防护：任一模式非法则跳过（ICU 与 JS 语法差异兜底） */
    private fun matchDate(line: String): Triple<Int, Int, Int>? {
        for (re in DATE_PATTERNS) {
            val m = runCatching { re.find(line) }.getOrNull() ?: continue
            val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            if (mo in 1..12 && d in 1..31) return Triple(y, mo, d)
        }
        return null
    }

    fun parse(text: String, defaultDate: String? = null): Pair<List<Parsed>, List<String>> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList<Parsed>() to emptyList()

        // JSON（全量备份）
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                val obj = C.json.parseToJsonElement(trimmed)
                val arr = if (obj is kotlinx.serialization.json.JsonArray) obj
                    else (obj as? kotlinx.serialization.json.JsonObject)?.get("entries") as? kotlinx.serialization.json.JsonArray
                val out = mutableListOf<Parsed>()
                arr?.forEach { el ->
                    val o = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val date = (o["entryDate"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val body = ((o["content"] as? kotlinx.serialization.json.JsonObject)?.get("body") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    if (date != null && Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) && !body.isNullOrBlank()) {
                        out.add(Parsed(date, body))
                    }
                }
                if (out.isNotEmpty()) return out to emptyList()
            } catch (_: Exception) { /* 按文本继续 */ }
        }

        // 文本按日期行分桶
        val buckets = LinkedHashMap<String, MutableList<String>>()
        val headerless = mutableListOf<String>()
        var currentDate: String? = null
        for (raw in trimmed.lines()) {
            val line = raw.trim()
            val dm = matchDate(line)
            if (dm != null) {
                val (y, mo, d) = dm
                currentDate = "%04d-%02d-%02d".format(y, mo, d)
                buckets.getOrPut(currentDate) { mutableListOf() }
                // 日期行带正文时保留后半句
                val rest = line.replace(Regex("^.*?\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?\\s*"), "").trim()
                if (rest.isNotEmpty()) buckets[currentDate]!!.add(rest)
            } else {
                (currentDate?.let { buckets[it] } ?: headerless).add(raw)
            }
        }
        val out = buckets.mapNotNull { (d, lines) ->
            val body = lines.joinToString("\n").trim()
            if (body.isNotEmpty()) Parsed(d, body) else null
        }.toMutableList()
        val unparsed = mutableListOf<String>()
        val rest = headerless.joinToString("\n").trim()
        if (rest.isNotEmpty()) {
            if (defaultDate != null) out.add(Parsed(defaultDate, rest))
            else unparsed.add(rest.take(50) + "…（无日期，未导入）")
        }
        return out to unparsed
    }
}
