package app.ilyakuchka.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.ilyakuchka.app.BuildConfig
import app.ilyakuchka.app.data.Prefs

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    Screen("Настройки", onBack = { nav.pop() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Аккаунт", style = MaterialTheme.typography.titleMedium)
            if (Prefs.loggedIn) {
                Text("Вы вошли" + if (Prefs.userName.isNotBlank()) " как ${Prefs.userName}" else "")
                if (Prefs.userSlug.isNotBlank()) {
                    OutlinedButton(onClick = {
                        nav.push(Route.DetailR(Prefs.userName, "fetchUser", app.ilyakuchka.app.data.jo("slug" to Prefs.userSlug)))
                    }) { Text("Мой профиль") }
                }
                OutlinedButton(onClick = { Prefs.logout() }) { Text("Выйти") }
            } else {
                Button(onClick = { nav.push(Route.LoginR) }) { Text("Войти") }
            }

            Text("Тема", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, t) in listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")) {
                    FilterChip(selected = Prefs.theme == k, onClick = { Prefs.setTheme(k) }, label = { Text(t) })
                }
            }
            Text("Читалка", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = Prefs.readerMode == "vertical", onClick = { Prefs.setReaderMode("vertical") }, label = { Text("Лента") })
                FilterChip(selected = Prefs.readerMode == "paged", onClick = { Prefs.setReaderMode("paged") }, label = { Text("Постранично") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Экономия трафика", Modifier.weight(1f))
                Switch(Prefs.dataSaver, { Prefs.setDataSaver(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Показывать 18+ в каталоге", Modifier.weight(1f))
                Switch(Prefs.showExplicit, { Prefs.setShowExplicit(it) })
            }
            Text(
                "Empty ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n${BuildConfig.API_URL}",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen() {
    val nav = LocalNav.current
    var token by remember { mutableStateOf(Prefs.token) }
    var cookie by remember { mutableStateOf(Prefs.cookie) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    Screen("Вход", onBack = { nav.pop() }) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Войдите на сайте ниже и нажмите «Забрать сессию» — приложение возьмёт cookie.")
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        CookieManager.getInstance().setAcceptCookie(true)
                        loadUrl(BuildConfig.SITE_URL)
                        web = this
                    }
                }
            )
            Button(onClick = {
                val cm = CookieManager.getInstance()
                val parts = listOf(BuildConfig.SITE_URL, "https://api.senkuro.me", "https://sso.senkuro.net")
                    .mapNotNull { cm.getCookie(it) }.joinToString("; ")
                if (parts.isNotBlank()) {
                    cookie = parts
                    Prefs.setAuth(token, parts)
                    info = "Сессия сохранена"
                } else info = "Cookie не найдены — сначала войдите"
            }) { Text("Забрать сессию") }
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Bearer-токен (необязательно)") })
            OutlinedTextField(cookie, { cookie = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cookie (необязательно)") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Prefs.setAuth(token, cookie); info = "Сохранено" }) { Text("Сохранить") }
                OutlinedButton(onClick = { nav.pop() }) { Text("Готово") }
            }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
