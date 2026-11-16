package app.ilyakuchka.app.data

import android.content.Context

data class OpVar(val name: String, val type: String, val default: String?) {
    val required: Boolean get() = type.endsWith("!") && default == null
    val base: String get() = type.replace("[", "").replace("]", "").replace("!", "")
    val isList: Boolean get() = type.startsWith("[")
    val isBool: Boolean get() = !isList && base == "Boolean"
    val isInt: Boolean get() = !isList && (base == "Int")
    val isFloat: Boolean get() = !isList && (base == "Float")
    val isScalarText: Boolean
        get() = !isList && (base == "String" || base == "ID" || base == "NaiveDate" || base == "DateTime")
}

data class Op(
    val name: String,
    val kind: String, // query | mutation | subscription
    val body: String,
    val vars: List<OpVar>,
    val fragments: List<String>,
    val source: String // файл JS-бандла сайта, откуда операция взята
)

/**
 * Реестр всех GraphQL-операций сайта (assets/operations.graphql): 286 операций и 133 фрагмента.
 * Документ для отправки собирается из тела операции + всех транзитивно используемых фрагментов.
 */
object Operations {
    private val fragments = LinkedHashMap<String, String>()
    private val ops = LinkedHashMap<String, Op>()
    private val docCache = HashMap<String, String>()
    @Volatile private var loaded = false

    val all: List<Op> get() = ops.values.toList()

    fun get(name: String): Op = ops[name] ?: error("Unknown operation $name")

    fun has(name: String): Boolean = ops.containsKey(name)

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val text = ctx.assets.open("operations.graphql").bufferedReader().use { it.readText() }
        val headerRx = Regex("(?m)^#\\s+(?:query|mutation|subscription|fragment)\\s+\\w+\\s+\\[([^\\]]*)]")
        val defRx = Regex("(?m)^(query|mutation|subscription|fragment)\\s+(\\w+)")
        val sources = HashMap<String, String>()
        run {
            val nameRx = Regex("(?m)^#\\s+(query|mutation|subscription|fragment)\\s+(\\w+)\\s+\\[([^\\]]*)]")
            for (m in nameRx.findAll(text)) sources[m.groupValues[2]] = m.groupValues[3]
        }
        val starts = defRx.findAll(text).toList()
        val commentRx = Regex("(?m)^#.*$")
        for (i in starts.indices) {
            val s = starts[i]
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else text.length
            val raw = commentRx.replace(text.substring(s.range.first, end), "").trim()
            val kind = s.groupValues[1]
            val name = s.groupValues[2]
            if (kind == "fragment") {
                fragments[name] = raw
            } else {
                ops[name] = Op(
                    name = name,
                    kind = kind,
                    body = raw,
                    vars = parseVars(raw),
                    fragments = emptyList(),
                    source = sources[name] ?: ""
                )
            }
        }
        // зависимости фрагментов
        for ((n, op) in ops.toMap()) {
            ops[n] = op.copy(fragments = collect(op.body))
        }
        loaded = true
    }

    private fun spreads(body: String): List<String> =
        Regex("\\.\\.\\.\\s*(\\w+)").findAll(body).map { it.groupValues[1] }.filter { it != "on" }.toList()

    private fun collect(body: String): List<String> {
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque(spreads(body))
        while (queue.isNotEmpty()) {
            val f = queue.removeFirst()
            if (!seen.add(f)) continue
            val fb = fragments[f] ?: continue
            queue.addAll(spreads(fb))
        }
        return seen.toList()
    }

    fun document(name: String): String = synchronized(docCache) {
        docCache.getOrPut(name) {
            val op = get(name)
            val sb = StringBuilder(op.body)
            for (f in op.fragments) {
                sb.append("\n\n").append(fragments[f] ?: "")
            }
            sb.toString()
        }
    }

    private fun parseVars(body: String): List<OpVar> {
        val open = body.indexOf('(')
        val brace = body.indexOf('{')
        if (open < 0 || (brace in 0 until open)) return emptyList()
        var depth = 0
        var close = -1
        for (i in open until body.length) {
            val c = body[i]
            if (c == '(') depth++
            if (c == ')') {
                depth--
                if (depth == 0) {
                    close = i
                    break
                }
            }
        }
        if (close < 0) return emptyList()
        val header = body.substring(open + 1, close)
        val rx = Regex("\\$(\\w+)\\s*:\\s*")
        val ms = rx.findAll(header).toList()
        val out = ArrayList<OpVar>()
        for (i in ms.indices) {
            val m = ms[i]
            val end = if (i + 1 < ms.size) ms[i + 1].range.first else header.length
            var seg = header.substring(m.range.last + 1, end).trim().trimEnd(',').trim()
            var def: String? = null
            val eq = seg.indexOf('=')
            if (eq >= 0) {
                def = seg.substring(eq + 1).trim()
                seg = seg.substring(0, eq).trim()
            }
            out.add(OpVar(m.groupValues[1], seg, def))
        }
        return out
    }
}
