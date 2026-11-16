package app.ilyakuchka.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.ilyakuchka.app.data.Api
import app.ilyakuchka.app.data.arr
import app.ilyakuchka.app.data.at
import app.ilyakuchka.app.data.get
import app.ilyakuchka.app.data.imageUrl
import app.ilyakuchka.app.data.jo
import app.ilyakuchka.app.data.nodes
import app.ilyakuchka.app.data.str
import app.ilyakuchka.app.data.tiptapText
import app.ilyakuchka.app.data.titleOf
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

@Composable
private fun ActionRow(vararg actions: Pair<String, () -> Unit>) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(actions.toList()) { (t, f) -> OutlinedButton(onClick = f) { Text(t) } }
    }
}

@Composable
fun MangaScreen(slug: String) {
    val nav = LocalNav.current
    val h = rememberLoad(slug) { Api.call("fetchManga", jo("slug" to slug))["manga"] }
    Screen(
        (h.state as? Load.Ok<JsonElement?>)?.value?.let { titleOf(it) } ?: "Манга",
        onBack = { nav.pop() }
    ) {
        Async(h) { m ->
            if (m == null) {
                ErrorBox("Тайтл не найден", null); return@Async
            }
            val id = m["id"].str ?: ""
            var branchIdx by remember { mutableStateOf(0) }
            val branches = m["branches"].arr
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(m["cover"]["main"]["url"].str ?: imageUrl(m), Modifier.width(130.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(titleOf(m), style = MaterialTheme.typography.titleLarge)
                        Text(m["originalName"]["content"].str ?: "", color = MaterialTheme.colorScheme.outline)
                        LabeledRow("Статус", m["status"].str)
                        LabeledRow("Тип", m["type"].str)
                        LabeledRow("Оценка", m["score"].str)
                        LabeledRow("Просмотры", m["views"].str)
                        LabeledRow("Глав", m["chapters"].str)
                        LabeledRow("Перевод", m["translitionStatus"].str)
                        LabeledRow("Страна", m["originCountry"].str)
                        LabeledRow("Выпуск", m["releasedOn"].str)
                    }
                }
                ActionRow(
                    "Закладка" to { nav.push(Route.OpR("updateUserBookmark", jo("input" to jo("subjectId" to id)))) },
                    "Оценить" to { nav.push(Route.OpR("voteManga", jo("input" to jo("mangaId" to id)))) },
                    "Комментарии" to { nav.push(Route.CommentsR(id, "Комментарии")) },
                    "Персонажи" to { nav.push(Route.ListR("Персонажи", "fetchMangaCharacters", jo("mangaId" to id))) },
                    "Авторы" to { nav.push(Route.ListR("Авторы", "fetchMangaStaff", jo("mangaId" to id))) },
                    "Связанное" to { nav.push(Route.ListR("Связанное", "fetchMangaRelations", jo("mangaId" to id))) },
                    "Источники" to { nav.push(Route.ListR("Внешние источники", "fetchMangaExternalSources", jo("mangaId" to id))) }
                )
                val desc = m["localizations"].arr.firstOrNull { it["lang"].str?.uppercase() == "RU" }
                    ?: m["localizations"].arr.firstOrNull()
                tiptapText(desc["description"]).takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                val labels = m["labels"].arr.map { titleOf(it) }
                if (labels.isNotEmpty()) Text(labels.joinToString(" · "), Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.primary)

                if (branches.isNotEmpty()) {
                    SectionTitle("Главы")
                    if (branches.size > 1) {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(branches.size) { i ->
                                val b = branches[i]
                                val team = b["primaryTeamActivities"].arr.firstOrNull()["team"]["name"].str
                                FilterChip(
                                    selected = i == branchIdx,
                                    onClick = { branchIdx = i },
                                    label = { Text("${b["lang"].str ?: ""} ${team ?: ""} (${b["chapters"].str ?: 0})") }
                                )
                            }
                        }
                    }
                    val branchId = branches[branchIdx.coerceIn(0, branches.lastIndex)]["id"].str ?: ""
                    ChapterList(branchId, slug)
                }
                val recs = m["recommendations"].arr
                if (recs.isNotEmpty()) {
                    SectionTitle("Похожее")
                    TileRow(recs, onClick = { r -> r["slug"].str?.let { nav.push(Route.MangaR(it)) } })
                }
                val rels = m["relations"].arr.mapNotNull { it["destination"] }
                if (rels.isNotEmpty()) {
                    SectionTitle("Связанные тайтлы")
                    TileRow(rels, onClick = { r -> r["slug"].str?.let { nav.push(Route.MangaR(it)) } })
                }
            }
        }
    }
}

@Composable
private fun ChapterList(branchId: String, mangaSlug: String) {
    val nav = LocalNav.current
    var desc by remember { mutableStateOf(true) }
    val pager = rememberPager(branchId to desc) { after ->
        Api.call(
            "fetchMangaChapters",
            jo("branchId" to branchId, "after" to after, "orderBy" to jo("field" to "NUMBER", "direction" to if (desc) "DESC" else "ASC"))
        )["mangaChapters"]
    }
    Column {
        Row(Modifier.padding(horizontal = 12.dp)) {
            FilterChip(selected = desc, onClick = { desc = !desc }, label = { Text(if (desc) "Новые сверху" else "Старые сверху") })
        }
        for (c in pager.items) {
            ListItem(
                headlineContent = { Text("Том ${c["volume"].str ?: "-"} · Глава ${c["number"].str ?: ""}") },
                supportingContent = { Text((c["name"].str ?: "") + " " + (c["createdAt"].str?.take(10) ?: "")) },
                modifier = Modifier.clickable { c["slug"].str?.let { nav.push(Route.ReaderR(it, mangaSlug)) } }
            )
        }
        pager.error?.let { ErrorBox(it) { pager.loadMore() } }
        if (pager.hasNext && pager.error == null) {
            OutlinedButton(onClick = { pager.loadMore() }, Modifier.padding(12.dp)) {
                Text(if (pager.loading) "Загрузка…" else "Ещё главы")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(r: Route.ReaderR) {
    val nav = LocalNav.current
    val h = rememberLoad(r.chapterSlug) {
        val ch = Api.call(
            "fetchMangaChapter",
            jo("slug" to r.chapterSlug, "cdnQuality" to if (app.ilyakuchka.app.data.Prefs.dataSaver) "low" else null)
        )["mangaChapter"]
        // статистика прочтения — необязательная
        try {
            ch["id"].str?.let { Api.call("readMangaChapter", jo("input" to jo("chapterId" to it))) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
        ch
    }
    Screen("Читалка", onBack = { nav.pop() }) {
        Async(h) { ch ->
            if (ch == null) {
                ErrorBox("Глава не найдена", null); return@Async
            }
            val pages = ch["pages"].arr.mapNotNull { it["image"]["compress"]["url"].str ?: it["image"]["original"]["url"].str }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        enabled = ch["prevSlug"].str != null,
                        onClick = { nav.pop(); nav.push(Route.ReaderR(ch["prevSlug"].str!!, r.mangaSlug)) }
                    ) { Text("←") }
                    Text("Том ${ch["volume"].str ?: "-"} · Гл. ${ch["number"].str ?: ""}", Modifier.align(Alignment.CenterVertically))
                    OutlinedButton(
                        enabled = ch["nextSlug"].str != null,
                        onClick = { nav.pop(); nav.push(Route.ReaderR(ch["nextSlug"].str!!, r.mangaSlug)) }
                    ) { Text("→") }
                }
                if (app.ilyakuchka.app.data.Prefs.readerMode == "paged") {
                    val ps = rememberPagerState { pages.size }
                    HorizontalPager(ps, Modifier.weight(1f)) { i ->
                        AsyncImage(pages[i], null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(pages) { u ->
                            AsyncImage(u, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Button(onClick = { nav.push(Route.CommentsR(ch["id"].str ?: "", "Комментарии")) }) { Text("Комментарии") }
                                ch["nextSlug"].str?.let { ns ->
                                    Button(onClick = { nav.pop(); nav.push(Route.ReaderR(ns, r.mangaSlug)) }) { Text("Следующая") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeScreen(slug: String) {
    val nav = LocalNav.current
    val h = rememberLoad(slug) { Api.call("fetchAnime", jo("slug" to slug))["anime"] }
    Screen((h.state as? Load.Ok<JsonElement?>)?.value?.let { titleOf(it) } ?: "Аниме", onBack = { nav.pop() }) {
        Async(h) { a ->
            if (a == null) {
                ErrorBox("Тайтл не найден", null); return@Async
            }
            val id = a["id"].str ?: ""
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(imageUrl(a), Modifier.width(130.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(titleOf(a), style = MaterialTheme.typography.titleLarge)
                        LabeledRow("Статус", a["status"].str)
                        LabeledRow("Тип", a["type"].str)
                        LabeledRow("Оценка", a["score"].str)
                        LabeledRow("Эпизодов", a["episodes"].str)
                    }
                }
                ActionRow(
                    "Закладка" to { nav.push(Route.OpR("updateUserBookmark", jo("input" to jo("subjectId" to id)))) },
                    "Оценить" to { nav.push(Route.OpR("voteAnime", jo("input" to jo("animeId" to id)))) },
                    "Комментарии" to { nav.push(Route.CommentsR(id, "Комментарии")) },
                    "Персонажи" to { nav.push(Route.ListR("Персонажи", "fetchAnimeCharacters", jo("animeId" to id))) },
                    "Авторы" to { nav.push(Route.ListR("Авторы", "fetchAnimeStaff", jo("animeId" to id))) },
                    "Связанное" to { nav.push(Route.ListR("Связанное", "fetchAnimeRelations", jo("animeId" to id))) }
                )
                SectionTitle("Серии")
                Episodes(id)
            }
        }
    }
}

@Composable
private fun Episodes(animeId: String) {
    val nav = LocalNav.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = rememberPager(animeId) { after ->
        Api.call(
            "fetchAnimeEpisodes",
            jo("animeId" to animeId, "after" to after, "orderBy" to jo("field" to "NUMBER", "direction" to "ASC"))
        )["animeEpisodes"]
    }
    Column {
        for (e in pager.items) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Серия ${e["number"].str ?: ""} ${e["name"].str ?: ""}", style = MaterialTheme.typography.titleSmall)
                for (t in e["translations"].arr) {
                    val teams = t["teams"].arr.joinToString { it["name"].str ?: "" }
                    OutlinedButton(
                        onClick = {
                            val url = t["embedUrl"].str ?: return@OutlinedButton
                            nav.push(Route.EmbedR(url, "Серия ${e["number"].str ?: ""}"))
                            t["id"].str?.let { tid ->
                                scope.launch {
                                    try {
                                        Api.call("watchAnimeEpisodeTranslation", jo("input" to jo("translationId" to tid)))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    ) { Text("${t["type"].str ?: ""} ${t["embedSource"].str ?: ""} $teams".trim()) }
                }
            }
        }
        pager.error?.let { ErrorBox(it) { pager.loadMore() } }
        if (pager.hasNext && pager.error == null) {
            OutlinedButton(onClick = { pager.loadMore() }, Modifier.padding(12.dp)) { Text("Ещё серии") }
        }
    }
}
