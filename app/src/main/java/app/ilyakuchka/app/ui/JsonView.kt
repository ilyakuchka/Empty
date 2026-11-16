package app.ilyakuchka.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ilyakuchka.app.data.arr
import app.ilyakuchka.app.data.get
import app.ilyakuchka.app.data.isImageUrl
import app.ilyakuchka.app.data.str
import app.ilyakuchka.app.data.tiptapText
import app.ilyakuchka.app.data.titleOf
import coil.compose.AsyncImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun looksTiptap(v: JsonElement): Boolean = when (v) {
    is JsonObject -> v["type"] != null && (v["content"] != null || v["text"] != null)
    is JsonArray -> v.isNotEmpty() && v.all { it["type"] != null && (it["content"] != null || it["text"] != null) }
    else -> false
}

private fun sizeOf(v: JsonElement): Int = when (v) {
    is JsonObject -> v.size
    is JsonArray -> v.size
    else -> 0
}

/**
 * Универсальный просмотр любого GraphQL-ответа: картинки показываются картинками,
 * Tiptap-текст — текстом, вложенные объекты и списки сворачиваются.
 */
@Composable
fun JsonView(el: JsonElement?, modifier: Modifier = Modifier, depth: Int = 0) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (el) {
            null -> Unit
            is JsonNull -> Unit
            is JsonPrimitive -> ScalarValue(el.content)
            is JsonObject -> ObjectBody(el, depth)
            is JsonArray -> ArrayBody(el, depth)
        }
    }
}

@Composable
private fun ScalarValue(s: String) {
    if (isImageUrl(s)) {
        AsyncImage(
            model = s,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
        )
    } else {
        Text(s, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ObjectBody(o: JsonObject, depth: Int) {
    val own = o["url"].str
    if (own != null && isImageUrl(own)) {
        ScalarValue(own)
    }
    for ((k, v) in o) {
        if (k == "__typename" || v is JsonNull) continue
        if (k == "url" && own != null && isImageUrl(own)) continue
        if (v is JsonPrimitive) {
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
                Text(
                    k,
                    Modifier.width(110.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Column(Modifier.weight(1f)) { ScalarValue(v.content) }
            }
        } else if (looksTiptap(v)) {
            val t = tiptapText(v)
            if (t.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        k,
                        Modifier.width(110.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Section(k, v, depth)
        }
    }
}

@Composable
private fun ArrayBody(a: JsonArray, depth: Int) {
    val shown = a.take(100)
    shown.forEachIndexed { i, item ->
        when (item) {
            is JsonNull -> Unit
            is JsonPrimitive -> ScalarValue(item.content)
            else -> {
                val t = if (item is JsonObject) titleOf(item).takeIf { it != "—" } else null
                Section(t ?: "#${i + 1}", item, depth)
            }
        }
    }
    if (a.size > shown.size) {
        Text("… ещё ${a.size - shown.size}", color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Section(title: String, v: JsonElement, depth: Int) {
    var open by remember { mutableStateOf(depth < 1 && sizeOf(v) <= 14) }
    val count = if (v is JsonArray) " [${v.size}]" else ""
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            Text("$title$count", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        if (open) {
            JsonView(v, Modifier.padding(start = 14.dp), depth + 1)
        }
    }
}
