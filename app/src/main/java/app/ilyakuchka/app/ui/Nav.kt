package app.ilyakuchka.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface Route {
    data class MangaR(val slug: String) : Route
    data class AnimeR(val slug: String) : Route
    data class ReaderR(val chapterSlug: String, val mangaSlug: String?) : Route
    data class ListR(
        val title: String,
        val op: String,
        val vars: JsonObject = JsonObject(emptyMap()),
        val kind: String = "none"
    ) : Route

    class DetailR(
        val title: String,
        val op: String,
        val vars: JsonObject,
        val actions: ((JsonElement) -> List<Pair<String, Route>>)? = null
    ) : Route

    data class CommentsR(val subjectId: String, val title: String) : Route
    data class ChatR(val id: String, val title: String) : Route
    data class OpR(val name: String, val preset: JsonObject? = null) : Route
    data class EmbedR(val url: String, val title: String) : Route
    class RawR(val title: String, val data: JsonElement) : Route
    data object ExplorerR : Route
    data object SettingsR : Route
    data object LoginR : Route
}

class Nav {
    val stack = mutableStateListOf<Route>()
    fun push(r: Route) {
        stack.add(r)
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }
