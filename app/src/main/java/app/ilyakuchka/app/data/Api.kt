package app.ilyakuchka.app.data

import app.ilyakuchka.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

object Api {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    /** Частичные ошибки последнего ответа (когда data пришла, но с errors). */
    @Volatile var lastErrors: String = ""

    const val UA = "Empty/1.1.2-test (Android)"

    private fun authHeader(): String? {
        val t = Prefs.token.trim()
        if (t.isBlank()) return null
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    private fun Request.Builder.withAuth(): Request.Builder {
        header("User-Agent", UA)
        header("Origin", BuildConfig.SITE_URL)
        header("Referer", BuildConfig.SITE_URL + "/")
        authHeader()?.let { header("Authorization", it) }
        if (Prefs.cookie.isNotBlank()) header("Cookie", Prefs.cookie)
        return this
    }

    /** Выполняет именованную операцию из реестра и возвращает поле data. */
    suspend fun call(op: String, vars: JsonObject = JsonObject(emptyMap())): JsonElement =
        withContext(Dispatchers.IO) {
            val doc = Operations.document(op)
            val body = jo("operationName" to op, "query" to doc, "variables" to vars).toString()
            val req = Request.Builder()
                .url(BuildConfig.API_URL)
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .withAuth()
                .build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val root = try {
                    JsonCompact.parseToJsonElement(text)
                } catch (e: Exception) {
                    throw ApiException("HTTP ${r.code}: ${text.take(300)}")
                }
                val data = root["data"]
                val errors = root["errors"].arr
                lastErrors = errors.joinToString("; ") { it["message"].str ?: "" }
                if (data == null) {
                    throw ApiException(errors.firstOrNull()["message"].str ?: "HTTP ${r.code}")
                }
                data
            }
        }

    /** Подписка по протоколу graphql-transport-ws; каждое событие — JSON-строка payload. */
    fun subscribe(op: String, vars: JsonObject = JsonObject(emptyMap())): Flow<String> = callbackFlow {
        val doc = Operations.document(op)
        val builder = Request.Builder()
            .url(BuildConfig.WS_URL)
            .header("Sec-WebSocket-Protocol", "graphql-transport-ws")
            .withAuth()
        val ws = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = jo("Authorization" to authHeader())
                webSocket.send(jo("type" to "connection_init", "payload" to payload).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = try {
                    JsonCompact.parseToJsonElement(text)
                } catch (e: Exception) {
                    return
                }
                when (m["type"].str) {
                    "connection_ack" -> {
                        val sub = jo(
                            "id" to "1",
                            "type" to "subscribe",
                            "payload" to jo(
                                "operationName" to op,
                                "query" to doc,
                                "variables" to vars
                            )
                        )
                        webSocket.send(sub.toString())
                    }
                    "ping" -> webSocket.send("{\"type\":\"pong\"}")
                    "next" -> trySend((m["payload"] ?: m).toString())
                    "error" -> close(ApiException((m["payload"] ?: m).toString()))
                    "complete" -> close()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose { ws.close(1000, null) }
    }
}
