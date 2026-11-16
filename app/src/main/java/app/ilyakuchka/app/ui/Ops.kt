package app.ilyakuchka.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ilyakuchka.app.data.Api
import app.ilyakuchka.app.data.JsonCompact
import app.ilyakuchka.app.data.JsonPretty
import app.ilyakuchka.app.data.Op
import app.ilyakuchka.app.data.OpVar
import app.ilyakuchka.app.data.Operations
import app.ilyakuchka.app.data.get
import app.ilyakuchka.app.data.jo
import app.ilyakuchka.app.data.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Каталог всех операций из operations.graphql: поиск и запуск любой query/mutation/subscription. */
@Composable
fun ExplorerScreen() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("all") }
    val all = remember { Operations.all.sortedWith(compareBy({ it.kind }, { it.name })) }
    val list = all.filter {
        (kind == "all" || it.kind == kind) && (q.isBlank() || it.name.contains(q.trim(), ignoreCase = true))
    }
    Screen("Все операции (${all.size})", onBack = { nav.pop() }) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), singleLine = true, placeholder = { Text("Поиск операции") })
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all", "query", "mutation", "subscription")) { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) })
                }
            }
            LazyColumn {
                items(list) { op ->
                    ListItem(
                        headlineContent = { Text(op.name) },
                        supportingContent = { Text("${op.kind} · ${op.vars.size} перем. · ${op.source}") },
                        modifier = Modifier.clickable { nav.push(Route.OpR(op.name)) }
                    )
                }
            }
        }
    }
}

private fun parseValue(v: OpVar, raw: String): Any? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return when {
        v.isList || v.base.endsWith("Input") || (!v.isScalarText && !v.isBool && !v.isInt && !v.isFloat && t.startsWith("{")) ->
            JsonCompact.parseToJsonElement(t)
        v.isInt -> t.toLong()
        v.isFloat -> t.toDouble()
        else -> t // строки, ID, enum — как есть
    }
}

/** Универсальная форма запуска: поля создаются из объявленных переменных операции. */
@Composable
fun OpScreen(r: Route.OpR) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val op: Op = remember(r.name) { Operations.get(r.name) }
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            for (v in op.vars) {
                val p = r.preset?.get(v.name)
                put(
                    v.name,
                    when {
                        p == null -> v.default?.trim('"') ?: ""
                        p is JsonPrimitive && p !is JsonNull -> p.content
                        else -> JsonPretty.encodeToString(JsonElement.serializer(), p)
                    }
                )
            }
        }
    }
    var result by remember { mutableStateOf<JsonElement?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<JsonElement>() }
    var job by remember { mutableStateOf<Job?>(null) }

    fun run() {
        error = null; result = null; events.clear()
        val vars = try {
            val m = LinkedHashMap<String, JsonElement>()
            for (v in op.vars) {
                val raw = values[v.name].orEmpty()
                val parsed = parseValue(v, raw)
                if (parsed == null) {
                    if (v.required) throw IllegalArgumentException("Нужно значение: ${v.name}")
                } else {
                    m[v.name] = app.ilyakuchka.app.data.toJson(parsed)
                }
            }
            JsonObject(m)
        } catch (e: Exception) {
            error = e.message ?: "Неверное значение"
            return
        }
        job?.cancel()
        running = true
        job = scope.launch {
            try {
                if (op.kind == "subscription") {
                    Api.subscribe(op.name, vars).collect { events.add(0, JsonCompact.parseToJsonElement(it)) }
                } else {
                    result = Api.call(op.name, vars)
                    if (Api.lastErrors.isNotBlank()) error = Api.lastErrors
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                running = false
            }
        }
    }

    Screen(op.name, onBack = { job?.cancel(); nav.pop() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${op.kind} · из ${op.source}", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
            for (v in op.vars) {
                if (v.isBool) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${v.name}${if (v.required) " *" else ""}", Modifier.weight(1f))
                        Switch(values[v.name] == "true", { values[v.name] = it.toString() })
                    }
                } else {
                    OutlinedTextField(
                        value = values[v.name].orEmpty(),
                        onValueChange = { values[v.name] = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("${v.name}: ${v.type}") },
                        minLines = if (v.base.endsWith("Input") || v.isList) 3 else 1
                    )
                }
            }
            Button(onClick = { run() }, enabled = !running) {
                Text(if (running) "Выполняется…" else if (op.kind == "subscription") "Подписаться" else "Выполнить")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { JsonView(it) }
            if (events.isNotEmpty()) {
                SectionTitle("События (${events.size})")
                for (e in events) JsonView(e)
            }
        }
    }
}
