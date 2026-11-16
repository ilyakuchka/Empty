package app.ilyakuchka.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

val JsonCompact = Json { ignoreUnknownKeys = true; isLenient = true }
val JsonPretty = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

operator fun JsonElement?.get(key: String): JsonElement? {
    val v = (this as? JsonObject)?.get(key)
    return if (v == null || v is JsonNull) null else v
}

val JsonElement?.str: String?
    get() {
        val p = this as? JsonPrimitive ?: return null
        return if (p is JsonNull) null else p.content
    }

val JsonElement?.long: Long? get() = (this as? JsonPrimitive)?.longOrNull
val JsonElement?.dbl: Double? get() = (this as? JsonPrimitive)?.doubleOrNull
val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.booleanOrNull
val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()
val JsonElement?.obj: JsonObject? get() = this as? JsonObject

fun JsonElement?.at(vararg keys: String): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) {
        cur = cur[k] ?: return null
    }
    return cur
}

/** Узлы из GraphQL-коннекшна { edges { node } }. */
fun JsonElement?.nodes(): List<JsonElement> = this["edges"].arr.mapNotNull { it["node"] }

fun JsonElement?.hasNext(): Boolean = this["pageInfo"]["hasNextPage"].bool == true

fun JsonElement?.endCursor(): String? = this["pageInfo"]["endCursor"].str

fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
    is List<*> -> JsonArray(v.map { toJson(it) })
    else -> JsonPrimitive(v.toString())
}

fun jo(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.filter { it.second != null }.associate { it.first to toJson(it.second) })

/** Первый connection (объект с edges) внутри ответа — для универсальных списков. */
fun findConnection(el: JsonElement?): JsonElement? {
    if (el is JsonObject) {
        if (el["edges"] is JsonArray) return el
        for (v in el.values) {
            val r = findConnection(v)
            if (r != null) return r
        }
    }
    return null
}

private fun pickLang(items: List<JsonElement>): String? {
    for (l in listOf("RU", "ru", "EN", "en")) {
        val s = items.firstOrNull { it["lang"].str == l }["content"].str
        if (!s.isNullOrBlank()) return s
    }
    return items.firstOrNull()["content"].str
}

fun titleOf(n: JsonElement?): String {
    val t = pickLang(n["titles"].arr)
    if (!t.isNullOrBlank()) return t
    val o = n["originalName"]
    val oo = o.str ?: o["content"].str
    if (!oo.isNullOrBlank()) return oo
    n["name"].str?.let { if (it.isNotBlank()) return it }
    n["title"].str?.let { if (it.isNotBlank()) return it }
    return n["slug"].str ?: n["id"].str ?: "—"
}

private fun urlOfImage(img: JsonElement?): String? =
    img["preview"]["url"].str
        ?: img["compress"]["url"].str
        ?: img["original"]["url"].str
        ?: img["url"].str

fun imageUrl(n: JsonElement?): String? {
    for (k in listOf("cover", "avatar", "image", "logotype", "banner", "preview")) {
        val u = urlOfImage(n[k])
        if (!u.isNullOrBlank()) return u
    }
    return null
}

private val blockTypes = setOf(
    "paragraph", "heading", "listItem", "blockquote", "codeBlock", "hardBreak", "bulletList", "orderedList"
)

private fun walkText(n: JsonElement?, sb: StringBuilder) {
    when (n) {
        is JsonArray -> n.forEach { walkText(it, sb) }
        is JsonObject -> {
            n["text"].str?.let { sb.append(it) }
            n["content"]?.let { walkText(it, sb) }
            val t = n["type"].str
            if (t != null && t in blockTypes) sb.append("\n")
        }
        else -> Unit
    }
}

/** Tiptap-документ (описания, комментарии, сообщения) → простой текст. */
fun tiptapText(n: JsonElement?): String {
    val sb = StringBuilder()
    walkText(n, sb)
    return sb.toString().trim()
}

fun isImageUrl(s: String): Boolean {
    if (!s.startsWith("http")) return false
    val base = s.substringBefore('?').lowercase()
    return base.endsWith(".jpg") || base.endsWith(".jpeg") || base.endsWith(".png") ||
        base.endsWith(".webp") || base.endsWith(".avif") || base.endsWith(".gif")
}
