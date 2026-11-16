package app.ilyakuchka.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.ilyakuchka.app.data.Api
import app.ilyakuchka.app.data.Prefs
import app.ilyakuchka.app.data.arr
import app.ilyakuchka.app.data.at
import app.ilyakuchka.app.data.bool
import app.ilyakuchka.app.data.get
import app.ilyakuchka.app.data.jo
import app.ilyakuchka.app.data.nodes
import app.ilyakuchka.app.data.str
import app.ilyakuchka.app.data.titleOf
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement

private val tabs = listOf(
    "Главная" to Icons.Filled.Home,
    "Каталог" to Icons.Filled.GridView,
    "Поиск" to Icons.Filled.Search,
    "Закладки" to Icons.Filled.Bookmarks,
    "Ещё" to Icons.Filled.MoreHoriz
)

suspend fun refreshViewer() {
    if (!Prefs.loggedIn) return
    try {
        val d = Api.call("fetchViewerBaseSettings")
        val u = d.at("viewer", "user")
        val id = u["id"].str
        if (id != null) Prefs.setUser(id, u["name"].str ?: "", u["slug"].str ?: "")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // не авторизован или нет сети — не критично
    }
}

@Composable
fun App() {
    val nav = remember { Nav() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val authKey = Prefs.authVersion.let { Prefs.token + "|" + Prefs.cookie }
    LaunchedEffect(authKey) { refreshViewer() }

    CompositionLocalProvider(LocalNav provides nav) {
        val top = nav.stack.lastOrNull()
        BackHandler(enabled = top != null) { nav.pop() }
        if (top != null) {
            key(top) { RouteScreen(top) }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(t.second, contentDescription = t.first) },
                                label = { Text(t.first) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        0 -> HomeScreen()
                        1 -> CatalogScreen()
                        2 -> SearchScreen()
                        3 -> LibraryScreen()
                        else -> MoreScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun RouteScreen(r: Route) {
    when (r) {
        is Route.MangaR -> MangaScreen(r.slug)
        is Route.AnimeR -> AnimeScreen(r.slug)
        is Route.ReaderR -> ReaderScreen(r)
        is Route.ListR -> ListScreen(r)
        is Route.DetailR -> DetailScreen(r)
        is Route.CommentsR -> CommentsScreen(r)
        is Route.ChatR -> ChatScreen(r)
        is Route.OpR -> OpScreen(r)
        is Route.EmbedR -> EmbedScreen(r)
        is Route.RawR -> RawScreen(r)
        Route.ExplorerR -> ExplorerScreen()
        Route.SettingsR -> SettingsScreen()
        Route.LoginR -> LoginScreen()
    }
}

// ---------- Главная ----------

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val h = rememberLoad("home") {
        Api.call(
            "fetchMainPage",
            jo(
                "skipLabelsSpotlight" to false,
                "skipManga" to false,
                "skipAnime" to false,
                "skipPosts" to false
            )
        )
    }
    Screen("Empty", embedded = true, actions = {
        IconButton(onClick = { nav.push(Route.SettingsR) }) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
        }
    }) {
        Async(h) { d ->
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                val next = d["mangaBookmarkNextChapters"].arr
                if (next.isNotEmpty()) {
                    item { SectionTitle("Продолжить чтение") }
                    item {
                        TileRow(
                            next,
                            subtitle = { c -> "Гл. ${c["number"].str ?: ""}" },
                            node = { c -> c["manga"] },
                            onClick = { c ->
                                val cs = c["slug"].str
                                if (cs != null) nav.push(Route.ReaderR(cs, c["manga"]["slug"].str))
                            }
                        )
                    }
                }
                val last = d.at("lastMangaChapters").nodes()
                if (last.isNotEmpty()) {
                    item { SectionTitle("Новые главы") }
                    item {
                        TileRow(
                            last,
                            subtitle = { m ->
                                m["lastChapters"].arr.firstOrNull()["number"].str?.let { "Гл. $it" }
                            },
                            onClick = { m -> m["slug"].str?.let { nav.push(Route.MangaR(it)) } }
                        )
                    }
                }
                for (sp in d["experimentalSpotlightLabels"].arr) {
                    val mangas = sp["previewMangas"].arr
                    if (mangas.isEmpty()) continue
                    item { SectionTitle(titleOf(sp["label"])) }
                    item {
                        TileRow(mangas, onClick = { m -> m["slug"].str?.let { nav.push(Route.MangaR(it)) } })
                    }
                }
                val eps = d.at("lastEpisodeTranslations").nodes()
                if (eps.isNotEmpty()) {
                    item { SectionTitle("Новые серии") }
                    item {
                        TileRow(
                            eps,
                            subtitle = { e -> e["episode"]["number"].str?.let { "Серия $it" } },
                            node = { e -> e["anime"] },
                            onClick = { e -> e["anime"]["slug"].str?.let { nav.push(Route.AnimeR(it)) } }
                        )
                    }
                }
                val posts = d.at("lastPosts").nodes()
                if (posts.isNotEmpty()) {
                    item { SectionTitle("Посты") }
                    items(posts) { p ->
                        ListItem(
                            headlineContent = { Text(p["title"].str ?: "—") },
                            supportingContent = { Text(p["author"]["name"].str ?: "") },
                            modifier = Modifier.clickable {
                                p["slug"].str?.let {
                                    nav.push(Route.DetailR(p["title"].str ?: "Пост", "fetchPost", jo("slug" to it)))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ---------- Каталог ----------

@Composable
fun CatalogScreen() {
    val nav = LocalNav.current
    var kind by rememberSaveable { mutableStateOf("manga") }
    var query by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf("CREATED_AT") }
    var dir by rememberSaveable { mutableStateOf("DESC") }

    val op = if (kind == "manga") "fetchMangas" else "fetchAnimes"
    val field = if (kind == "manga") "mangas" else "animes"
    val pager = rememberPager(listOf(op, applied, order, dir)) { after ->
        Api.call(
            op,
            jo(
                "first" to 24,
                "after" to after,
                "search" to applied.ifBlank { null },
                "orderField" to order,
                "orderDirection" to dir
            )
        ).at(field)
    }

    Screen("Каталог", embedded = true) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == "manga", onClick = { kind = "manga"; order = "CREATED_AT" }, label = { Text("Манга") })
                FilterChip(selected = kind == "anime", onClick = { kind = "anime"; order = "CREATED_AT" }, label = { Text("Аниме") })
                if (kind == "manga") {
                    FilterChip(
                        selected = order == "LAST_CHAPTER_AT",
                        onClick = { order = if (order == "LAST_CHAPTER_AT") "CREATED_AT" else "LAST_CHAPTER_AT" },
                        label = { Text("По главам") }
                    )
                }
                FilterChip(
                    selected = dir == "ASC",
                    onClick = { dir = if (dir == "ASC") "DESC" else "ASC" },
                    label = { Text(if (dir == "ASC") "↑" else "↓") }
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("Поиск по каталогу") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applied = query.trim() })
            )
            TileGrid(pager) { n ->
                val s = n["slug"].str ?: return@TileGrid
                nav.push(if (kind == "manga") Route.MangaR(s) else Route.AnimeR(s))
            }
        }
    }
}

@Composable
fun TileGrid(pager: PagerState, onClick: (JsonElement) -> Unit) {
    val shown = pager.items.filter { Prefs.showExplicit || it["containExplicitThemes"].bool != true }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(shown) { n ->
            Tile(n, Modifier.fillMaxWidth()) { onClick(n) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val err = pager.error
            if (err != null) {
                ErrorBox(err) { pager.loadMore() }
            } else if (pager.hasNext) {
                LaunchedEffect(pager.items.size) { pager.loadMore() }
                Spinner(Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
}

// ---------- Поиск ----------

private val searchTypes = listOf("MANGA", "ANIME", "USER", "TEAM", "GUILD", "STUDIO", "PERSON", "CHARACTER")

@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    var query by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("MANGA") }
    val popular = rememberLoad("popular-queries") { Api.call("fetchPopularTodaySearchQueries") }

    Screen("Поиск", embedded = true) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                placeholder = { Text("Название, пользователь, команда…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applied = query.trim() })
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchTypes) { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                }
            }
            if (applied.isBlank()) {
                val qs = (popular.state as? Load.Ok<JsonElement>)?.value["popularTodaySearchQueries"].arr
                    .mapNotNull { it.str ?: it["query"].str }
                if (qs.isNotEmpty()) {
                    SectionTitle("Популярные запросы сегодня")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(qs) { q ->
                            FilterChip(selected = false, onClick = { query = q; applied = q }, label = { Text(q) })
                        }
                    }
                }
            } else {
                SearchResults(applied, type)
            }
        }
    }
}

@Composable
private fun SearchResults(q: String, type: String) {
    val nav = LocalNav.current
    val h = rememberLoad(q to type) {
        try {
            Api.call("trackSearchQuery", jo("input" to jo("query" to q)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // трекинг необязателен
        }
        Api.call("search", jo("query" to q, "type" to type))
    }
    Async(h) { d ->
        val res = d.at("search").nodes()
        if (res.isEmpty()) {
            Text("Ничего не найдено", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
        }
        LazyColumn {
            items(res) { n -> NodeRow(n, "") { openNode(nav, "", n) } }
        }
    }
}

// ---------- Закладки ----------

@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    var subject by rememberSaveable { mutableStateOf("MANGA") }
    val uid = Prefs.userId
    Screen("Закладки", embedded = true) {
        if (!Prefs.loggedIn) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Закладки хранятся на сайте — войдите в аккаунт.")
                Button(onClick = { nav.push(Route.LoginR) }, modifier = Modifier.padding(top = 12.dp)) { Text("Войти") }
            }
        } else if (uid.isBlank()) {
            Spinner()
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = subject == "MANGA", onClick = { subject = "MANGA" }, label = { Text("Манга") })
                    FilterChip(selected = subject == "ANIME", onClick = { subject = "ANIME" }, label = { Text("Аниме") })
                }
                val pager = rememberPager(listOf(uid, subject), transform = { it["entity"] ?: it }) { after ->
                    Api.call(
                        "fetchUserBookmarks",
                        jo("after" to after, "userId" to uid, "subjectType" to subject)
                    ).at("userBookmarks")
                }
                TileGrid(pager) { n ->
                    val s = n["slug"].str ?: return@TileGrid
                    nav.push(if (subject == "MANGA") Route.MangaR(s) else Route.AnimeR(s))
                }
            }
        }
    }
}

// ---------- Ещё ----------

private data class MoreItem(val title: String, val route: Route)

private fun listR(title: String, op: String, kind: String = "none", vararg v: Pair<String, Any?>) =
    Route.ListR(title, op, jo(*v), kind)

private fun detailR(title: String, op: String, vararg v: Pair<String, Any?>) =
    Route.DetailR(title, op, jo(*v))

@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    val groups = remember {
        listOf(
            "Контент" to listOf(
                MoreItem("Лента новых глав", listR("Лента новых глав", "fetchFeed", "chapter")),
                MoreItem("Короткие главы (shorts)", listR("Shorts", "fetchShortsMangaChapters", "chapter")),
                MoreItem("Популярная манга — день", listR("Популярное: день", "fetchPopularMangaByPeriod", "manga", "period" to "DAY")),
                MoreItem("Популярная манга — неделя", listR("Популярное: неделя", "fetchPopularMangaByPeriod", "manga", "period" to "WEEK")),
                MoreItem("Популярное аниме — неделя", listR("Популярное аниме", "fetchPopularAnimeByPeriod", "anime", "period" to "WEEK")),
                MoreItem("Рекомендации", listR("Рекомендации", "fetchMangaRecommendations", "manga")),
                MoreItem("Подборки", listR("Подборки", "fetchExperimentalSpotlights")),
                MoreItem("Посты", listR("Посты", "fetchPosts", "post", "status" to "PUBLISHED")),
                MoreItem("Новые комментарии", listR("Новые комментарии", "fetchNewComments", "none", "skipTotalCount" to true))
            ),
            "Сообщество" to listOf(
                MoreItem("Сообщения", listR("Сообщения", "fetchConversations", "conversation")),
                MoreItem("Команды", listR("Команды", "fetchTeams", "team")),
                MoreItem("Гильдии", listR("Гильдии", "fetchGuilds", "guild")),
                MoreItem("Активность", listR("Активность", "fetchActivities")),
                MoreItem("Достижения", listR("Достижения", "fetchAchievements")),
                MoreItem("Рейтинг пользователей", detailR("Рейтинг", "fetchUserRanks")),
                MoreItem("Топы", detailR("Топы", "fetchMainUserTops", "includeExperiences" to true, "includeCredits" to true)),
                MoreItem("События", listR("События", "fetchEvents", "none", "startAt" to "2026-01-01", "orderBy" to jo("field" to "START_AT", "direction" to "ASC")))
            ),
            "Карточки и магазин" to listOf(
                MoreItem("Карточки", listR("Карточки", "fetchCards", "card")),
                MoreItem("Коллекционное", listR("Коллекционное", "fetchCollectibles", "collectible", "type" to "AVATAR")),
                MoreItem("Наборы", listR("Наборы", "fetchCollectibleBundles")),
                MoreItem("Купоны", listR("Купоны", "fetchCollectibleCoupons")),
                MoreItem("Платёжные системы", detailR("Платёжные системы", "fetchPaymentProcessors"))
            ),
            "Управление" to listOf(
                MoreItem("Тикеты", listR("Тикеты", "fetchTickets", "none", "orderBy" to jo("field" to "CREATED_AT", "direction" to "DESC"))),
                MoreItem("Жалобы", listR("Жалобы", "fetchReports", "none", "first" to 20, "orderBy" to jo("field" to "CREATED_AT", "direction" to "DESC"))),
                MoreItem("Ожидающие карточки", listR("Ожидающие карточки", "fetchPendingCards")),
                MoreItem("Проекты моих команд", listR("Проекты команд", "fetchViewerTeamProjects")),
                MoreItem("Мои стикеры", detailR("Стикеры", "fetchUserStickerSets")),
                MoreItem("Мои эмодзи", detailR("Эмодзи", "fetchUserEmojiSets"))
            ),
            "Приложение" to listOf(
                MoreItem("Все операции API (286)", Route.ExplorerR),
                MoreItem("Настройки и вход", Route.SettingsR)
            )
        )
    }
    Screen("Ещё", embedded = true) {
        LazyColumn {
            for ((group, entries) in groups) {
                item { SectionTitle(group) }
                items(entries) { e ->
                    ListItem(
                        headlineContent = { Text(e.title) },
                        modifier = Modifier.clickable { nav.push(e.route) }
                    )
                }
            }
        }
    }
}
