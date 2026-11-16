package app.ilyakuchka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ilyakuchka.app.data.endCursor
import app.ilyakuchka.app.data.get
import app.ilyakuchka.app.data.hasNext
import app.ilyakuchka.app.data.imageUrl
import app.ilyakuchka.app.data.nodes
import app.ilyakuchka.app.data.str
import app.ilyakuchka.app.data.titleOf
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

// ---------- загрузка ----------

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

class LoadHolder<T> {
    var state: Load<T> by mutableStateOf<Load<T>>(Load.Loading)
    var tick by mutableIntStateOf(0)
    fun retry() {
        tick++
    }
}

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): LoadHolder<T> {
    val h = remember(key) { LoadHolder<T>() }
    LaunchedEffect(key, h.tick) {
        h.state = Load.Loading
        try {
            h.state = Load.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            h.state = Load.Err(e.message ?: e.toString())
        }
    }
    return h
}

@Composable
fun <T> Async(h: LoadHolder<T>, content: @Composable (T) -> Unit) {
    when (val s = h.state) {
        is Load.Loading -> Spinner()
        is Load.Err -> ErrorBox(s.message) { h.retry() }
        is Load.Ok -> content(s.value)
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) Button(onClick = onRetry) { Text("Повторить") }
    }
}

// ---------- постраничная загрузка ----------

class PagerState(
    private val scope: CoroutineScope,
    private val transform: (JsonElement) -> JsonElement = { it },
    private val fetch: suspend (String?) -> JsonElement?
) {
    val items = mutableStateListOf<JsonElement>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasNext by mutableStateOf(true)
    private var cursor: String? = null

    fun loadMore() {
        if (loading || !hasNext) return
        loading = true
        error = null
        scope.launch {
            try {
                val conn = fetch(cursor)
                items.addAll(conn.nodes().map(transform))
                cursor = conn.endCursor()
                hasNext = conn.hasNext() && cursor != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun rememberPager(
    key: Any?,
    transform: (JsonElement) -> JsonElement = { it },
    fetch: suspend (String?) -> JsonElement?
): PagerState {
    val scope = rememberCoroutineScope()
    val st = remember(key) { PagerState(scope, transform, fetch) }
    LaunchedEffect(st) { st.loadMore() }
    return st
}

// ---------- каркас экрана ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    embedded: Boolean = false,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = { actions() }
            )
        },
        contentWindowInsets = if (embedded) WindowInsets(0.dp) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) { content() }
    }
}

// ---------- карточки ----------

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, ratio: Float = 0.7f) {
    Box(
        modifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun Tile(
    node: JsonElement?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            Cover(imageUrl(node), Modifier.fillMaxWidth())
            val score = node["score"].str
            if (score != null && score != "0" && score != "0.0") {
                Pill(score, Modifier.padding(6.dp))
            }
        }
        Text(
            titleOf(node),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (subtitle != null) {
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun TileRow(
    list: List<JsonElement>,
    subtitle: (JsonElement) -> String? = { null },
    node: (JsonElement) -> JsonElement? = { it },
    onClick: (JsonElement) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list) { it0 ->
            Tile(node(it0), Modifier.width(118.dp), subtitle(it0)) { onClick(it0) }
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
