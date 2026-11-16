package app.ilyakuchka.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import app.ilyakuchka.app.data.Api
import app.ilyakuchka.app.data.arr
import app.ilyakuchka.app.data.findConnection
import app.ilyakuchka.app.data.get
import app.ilyakuchka.app.data.jo
import app.ilyakuchka.app.data.nodes
import app.ilyakuchka.app.data.imageUrl
import app.ilyakuchka.app.data.str
import app.ilyakuchka.app.data.tiptapText
import app.ilyakuchka.app.data.titleOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Приводит любой ответ к виду connection { edges { node } } для постраничного списка. */
private fun asConnection(data: JsonElement): JsonElement {
    findConnection(data)?.let { return it }
    val o = data as? JsonObject
    val arr = o?.values?.firstOrNull { it is JsonArray && it.isNotEmpty() } as? JsonArray
    val list = arr ?: JsonArray(listOfNotNull(o?.values?.firstOrNull { it is JsonObject }))
    return jo("edges" to list.map { jo("node" to it) })
}

fun openNode(nav: Nav, kind: String, n: JsonElement) {
    val slug = n["slug"].str
    val id = n["id"].str
    val tn = n["__typename"].str ?: ""
    val k = when {
        kind != "none" && kind.isNotEmpty() -> kind
        tn.contains("Manga") -> "manga"
        tn.contains("Anime") -> "anime"
        tn.contains("User") -> "user"
        tn.contains("Team") -> "team"
        tn.contains("Guild") -> "guild"
        tn.contains("Studio") -> "studio"
        tn.contains("Person") -> "person"
        tn.contains("Character") -> "character"
        else -> "none"
    }
    val title = titleOf(n)
    when {
        k == "manga" && slug != null -> nav.push(Route.MangaR(slug))
        k == "anime" && slug != null -> nav.push(Route.AnimeR(slug))
        k == "chapter" && slug != null -> nav.push(Route.ReaderR(slug, n["manga"]["slug"].str))
        k == "conversation" && id != null -> nav.push(Route.ChatR(id, title))
        k == "post" && slug != null -> nav.push(Route.DetailR(title, "fetchPost", jo("slug" to slug)))
        k == "user" && slug != null -> nav.push(Route.DetailR(title, "fetchUser", jo("slug" to slug)))
        k == "team" && slug != null -> nav.push(Route.DetailR(title, "fetchTeam", jo("slug" to slug)))
        k == "guild" && slug != null -> nav.push(Route.DetailR(title, "fetchGuild", jo("slug" to slug)))
        k == "studio" && slug != null -> nav.push(Route.DetailR(title, "fetchStudio", jo("slug" to slug)))
        k == "person" && slug != null -> nav.push(Route.DetailR(title, "fetchPerson", jo("slug" to slug)))
        k == "character" && slug != null -> nav.push(Route.DetailR(title, "fetchCharacter", jo("slug" to slug)))
        k == "card" && slug != null -> nav.push(Route.DetailR(title, "fetchCard", jo("slug" to slug)))
        k == "collectible" && slug != null -> nav.push(Route.DetailR(title, "fetchCollectible", jo("slug" to slug)))
        else -> nav.push(Route.RawR(title, n))
    }
}

@Composable
fun NodeRow(n: JsonElement, kind: String, onClick: () -> Unit) {
    val sub = listOfNotNull(
        n["type"].str, n["status"].str, n["number"].str?.let { "№ $it" },
        n["author"]["name"].str, n["manga"]?.let { titleOf(it) },
        n["createdAt"].str?.take(10)
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(titleOf(n), maxLines = 2) },
        supportingContent = { if (sub.isNotBlank()) Text(sub, maxLines = 1) },
        leadingContent = {
            val u = imageUrl(n) ?: imageUrl(n["manga"])
            if (u != null) Cover(u, Modifier.width(44.dp))
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun ListScreen(r: Route.ListR) {
    val nav = LocalNav.current
    val hasAfter = Operations_hasAfter(r.op)
    val pager = rememberPager(r) { after ->
        val vars = if (after != null) JsonObject(r.vars + ("after" to JsonPrimitive(after))) else r.vars
        asConnection(Api.call(r.op, vars))
    }
    val state = rememberLazyListState()
    Screen(r.title, onBack = { nav.pop() }) {
        LazyColumn(state = state) {
            items(pager.items) { n ->
                val simple = n["id"] == null && n["slug"] == null
                if (simple && n.str == null && n["edges"] == null && n["name"] == null && n["titles"] == null) {
                    JsonView(n, Modifier.padding(12.dp))
                    HorizontalDivider()
                } else {
                    NodeRow(n, r.kind) { openNode(nav, r.kind, n) }
                }
            }
            item {
                val err = pager.error
                if (err != null) ErrorBox(err) { pager.loadMore() }
                else if (pager.hasNext && hasAfter) {
                    LaunchedEffect(pager.items.size) { pager.loadMore() }
                    Spinner(Modifier.fillMaxWidth().padding(16.dp))
                } else if (pager.loading) Spinner(Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
}

private fun Operations_hasAfter(op: String) =
    app.ilyakuchka.app.data.Operations.get(op).vars.any { it.name == "after" }

@Composable
fun DetailScreen(r: Route.DetailR) {
    val nav = LocalNav.current
    val h = rememberLoad(r) { Api.call(r.op, r.vars) }
    Screen(r.title, onBack = { nav.pop() }) {
        Async(h) { d ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                JsonView(d)
                Button(onClick = { nav.push(Route.OpR(r.op, r.vars)) }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Открыть в консоли API")
                }
            }
        }
    }
}

@Composable
fun CommentsScreen(r: Route.CommentsR) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf(0) }
    val pager = rememberPager(r.subjectId to order) { after ->
        Api.call("fetchThreadComments", jo("subjectId" to r.subjectId, "after" to after))["comments"]
    }
    Screen(r.title, onBack = { nav.pop() }) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) {
                items(pager.items) { c ->
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            c["author"]["name"].str ?: "—",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(tiptapText(c["content"]))
                        Text(
                            "↑${c["upvotes"].str ?: 0} ↓${c["downvotes"].str ?: 0} · ${c["createdAt"].str?.take(10) ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        for (rep in c["replies"].arr) {
                            Column(Modifier.padding(start = 16.dp, top = 6.dp)) {
                                Text(rep["author"]["name"].str ?: "—", style = MaterialTheme.typography.labelMedium)
                                Text(tiptapText(rep["content"]))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    pager.error?.let { ErrorBox(it) { pager.loadMore() } }
                    if (pager.hasNext && pager.error == null) {
                        LaunchedEffect(pager.items.size) { pager.loadMore() }
                        Spinner(Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
            }
            msg?.let { Text(it, Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Комментарий") })
                Button(
                    onClick = {
                        val t = text.trim()
                        if (t.isEmpty()) return@Button
                        scope.launch {
                            try {
                                Api.call("createComment", jo("input" to jo("subjectId" to r.subjectId, "content" to tiptapDoc(t))))
                                text = ""; msg = null; order++
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                msg = e.message
                            }
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("→") }
            }
        }
    }
}

fun tiptapDoc(text: String): JsonElement = jo(
    "type" to "doc",
    "content" to text.split("\n").map { line ->
        jo("type" to "paragraph", "content" to if (line.isEmpty()) emptyList<Any>() else listOf(jo("type" to "text", "text" to line)))
    }
)

@Composable
fun ChatScreen(r: Route.ChatR) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val msgs = remember { mutableStateListOf<JsonElement>() }
    var loaded by remember { mutableStateOf(false) }

    suspend fun reload() {
        val d = Api.call("fetchConversationMessages", jo("conversationId" to r.id, "last" to 40))
        msgs.clear()
        msgs.addAll(d["conversationMessages"].nodes())
        loaded = true
    }
    LaunchedEffect(r.id) {
        try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { err = e.message; loaded = true }
    }
    // живые обновления по WebSocket-подписке
    LaunchedEffect(r.id) {
        try {
            Api.subscribe("onConversationMessageUpdated").collect {
                try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // подписка недоступна — остаётся ручное обновление
        }
    }
    Screen(r.title, onBack = { nav.pop() }) {
        Column(Modifier.fillMaxSize()) {
            if (!loaded) Spinner(Modifier.weight(1f).fillMaxWidth())
            else LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                items(msgs.reversed()) { m ->
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(
                            (m["user"]["name"].str ?: "—") + (if (m["viewerDidAuthor"].str == "true") " (вы)" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(tiptapText(m["content"]).ifBlank { "[${m["type"].str ?: "сообщение"}]" })
                    }
                }
            }
            err?.let { Text(it, Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Сообщение") })
                Button(
                    onClick = {
                        val t = text.trim()
                        if (t.isEmpty()) return@Button
                        scope.launch {
                            try {
                                Api.call("createConversationMessage", jo("input" to jo("conversationId" to r.id, "content" to tiptapDoc(t))))
                                text = ""; err = null; reload()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                err = e.message
                            }
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("→") }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedScreen(r: Route.EmbedR) {
    val nav = LocalNav.current
    Screen(r.title, onBack = { nav.pop() }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    loadUrl(r.url)
                }
            }
        )
    }
}

@Composable
fun RawScreen(r: Route.RawR) {
    val nav = LocalNav.current
    Screen(r.title, onBack = { nav.pop() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) { JsonView(r.data) }
    }
}
