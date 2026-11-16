package app.ilyakuchka.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
    private lateinit var sp: SharedPreferences

    var theme: String by mutableStateOf("system")
        private set
    var showExplicit: Boolean by mutableStateOf(false)
        private set
    var readerMode: String by mutableStateOf("vertical")
        private set
    var dataSaver: Boolean by mutableStateOf(false)
        private set
    var authVersion: Int by mutableStateOf(0)
        private set

    var token: String = ""
        private set
    var cookie: String = ""
        private set
    var userId: String = ""
        private set
    var userName: String = ""
        private set
    var userSlug: String = ""
        private set

    fun init(c: Context) {
        sp = c.getSharedPreferences("empty", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "system") ?: "system"
        showExplicit = sp.getBoolean("explicit", false)
        readerMode = sp.getString("reader", "vertical") ?: "vertical"
        dataSaver = sp.getBoolean("saver", false)
        token = sp.getString("token", "") ?: ""
        cookie = sp.getString("cookie", "") ?: ""
        userId = sp.getString("userId", "") ?: ""
        userName = sp.getString("userName", "") ?: ""
        userSlug = sp.getString("userSlug", "") ?: ""
        authVersion++
    }

    val loggedIn: Boolean get() = authVersion >= 0 && (token.isNotBlank() || cookie.isNotBlank())

    fun changeTheme(v: String) {
        theme = v
        sp.edit().putString("theme", v).apply()
    }

    fun changeShowExplicit(v: Boolean) {
        showExplicit = v
        sp.edit().putBoolean("explicit", v).apply()
    }

    fun changeReaderMode(v: String) {
        readerMode = v
        sp.edit().putString("reader", v).apply()
    }

    fun changeDataSaver(v: Boolean) {
        dataSaver = v
        sp.edit().putBoolean("saver", v).apply()
    }

    fun setAuth(token: String, cookie: String) {
        this.token = token.trim()
        this.cookie = cookie.trim()
        sp.edit().putString("token", this.token).putString("cookie", this.cookie).apply()
        authVersion++
    }

    fun setUser(id: String, name: String, slug: String) {
        userId = id
        userName = name
        userSlug = slug
        sp.edit().putString("userId", id).putString("userName", name).putString("userSlug", slug).apply()
        authVersion++
    }

    fun logout() {
        token = ""
        cookie = ""
        userId = ""
        userName = ""
        userSlug = ""
        sp.edit().clear().putString("theme", theme).putBoolean("explicit", showExplicit)
            .putString("reader", readerMode).putBoolean("saver", dataSaver).apply()
        authVersion++
    }
}
