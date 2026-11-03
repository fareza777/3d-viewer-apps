package com.studio3dpro.engine.io

/**
 * Minimal JSON reader/writer used for glTF and project files. Avoids Android
 * dependencies so the whole engine stays testable on the JVM.
 */
sealed class JsonValue {
    data class Obj(val fields: LinkedHashMap<String, JsonValue> = LinkedHashMap()) : JsonValue() {
        operator fun get(key: String): JsonValue? = fields[key]
        val size: Int get() = fields.size
        fun keys(): Set<String> = fields.keys
    }

    data class Arr(val items: MutableList<JsonValue> = mutableListOf()) : JsonValue() {
        operator fun get(index: Int): JsonValue? = items.getOrNull(index)
        val size: Int get() = items.size
    }

    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()

    val asObj: Obj? get() = this as? Obj
    val asArr: Arr? get() = this as? Arr
    val asString: String? get() = (this as? Str)?.value
    val asNumber: Double? get() = (this as? Num)?.value
    val asBool: Boolean? get() = (this as? Bool)?.value
    val asInt: Int? get() = (this as? Num)?.value?.toInt()
    val asFloat: Float? get() = (this as? Num)?.value?.toFloat()

    fun obj(key: String): Obj? = (asObj?.fields?.get(key)) as? Obj
    fun arr(key: String): Arr? = (asObj?.fields?.get(key)) as? Arr
    fun str(key: String): String? = ((asObj?.fields?.get(key)) as? Str)?.value
    fun num(key: String): Double? = (asObj?.fields?.get(key) as? Num)?.value ?: null
    fun int(key: String): Int? = num(key)?.toInt()
    fun float(key: String): Float? = num(key)?.toFloat()
    fun bool(key: String): Boolean? = (asObj?.fields?.get(key) as? Bool)?.value

    fun optStr(key: String): String? = (asObj?.fields?.get(key) as? Str)?.value
    fun optFloat(key: String, fallback: Float): Float = float(key) ?: fallback
    fun optInt(key: String, fallback: Int): Int = int(key) ?: fallback
    fun optBool(key: String, fallback: Boolean): Boolean = bool(key) ?: fallback

    fun stringOr(key: String, fallback: String): String = optStr(key) ?: fallback

    fun doubleArray(): DoubleArray? {
        val a = asArr ?: return null
        return DoubleArray(a.size) { a.items[it].asNumber ?: 0.0 }
    }

    fun floatArray(): FloatArray? {
        val a = asArr ?: return null
        return FloatArray(a.size) { a.items[it].asFloat ?: 0f }
    }

    fun intArray(): IntArray? {
        val a = asArr ?: return null
        return IntArray(a.size) { a.items[it].asInt ?: 0 }
    }

    companion object {
        fun of(value: String) = Str(value)
        fun of(value: Number) = Num(value.toDouble())
        fun of(value: Boolean) = Bool(value)
        fun objOf(vararg pairs: Pair<String, JsonValue?>): Obj {
            val o = Obj()
            for ((k, v) in pairs) if (v != null) o.fields[k] = v
            return o
        }
        fun arrOf(vararg items: JsonValue): Arr = Arr(items.toMutableList())
        fun arrOf(items: List<JsonValue>): Arr = Arr(items.toMutableList())
        fun floats(values: FloatArray): Arr = Arr(values.map { Num(it.toDouble()) }.toMutableList())
        fun ints(values: IntArray): Arr = Arr(values.map { Num(it.toDouble()) }.toMutableList())
    }
}

object Json {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWs()
        if (!parser.atEnd()) throw IllegalArgumentException("Trailing content at ${parser.position}")
        return value
    }

    fun stringify(value: JsonValue, pretty: Boolean = false): String {
        val sb = StringBuilder()
        write(value, sb, if (pretty) 0 else -1)
        return sb.toString()
    }

    private fun write(value: JsonValue, sb: StringBuilder, indent: Int) {
        when (value) {
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                for ((k, v) in value.fields) {
                    if (!first) sb.append(',')
                    first = false
                    if (indent >= 0) { sb.append('\n'); repeat(indent + 1) { sb.append("  ") } }
                    escape(k, sb)
                    sb.append(':')
                    if (indent >= 0) sb.append(' ')
                    write(v, sb, if (indent >= 0) indent + 1 else -1)
                }
                if (!first && indent >= 0) { sb.append('\n'); repeat(indent) { sb.append("  ") } }
                sb.append('}')
            }
            is JsonValue.Arr -> {
                sb.append('[')
                for (i in value.items.indices) {
                    if (i > 0) sb.append(',')
                    if (indent >= 0) { sb.append('\n'); repeat(indent + 1) { sb.append("  ") } }
                    write(value.items[i], sb, if (indent >= 0) indent + 1 else -1)
                }
                if (value.items.isNotEmpty() && indent >= 0) { sb.append('\n'); repeat(indent) { sb.append("  ") } }
                sb.append(']')
            }
            is JsonValue.Str -> escape(value.value, sb)
            is JsonValue.Num -> {
                val d = value.value
                if (d.isFinite() && d == d.toLong().toDouble() && abs(d) < 1e15) sb.append(d.toLong())
                else sb.append(d)
            }
            is JsonValue.Bool -> sb.append(value.value)
            JsonValue.Null -> sb.append("null")
        }
    }

    private fun abs(d: Double) = kotlin.math.abs(d)

    private fun escape(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val text: String) {
        var position = 0

        fun atEnd() = position >= text.length
        fun peek(): Char = if (atEnd()) '\u0000' else text[position]
        fun next(): Char = if (atEnd()) throw IllegalArgumentException("Unexpected end of JSON") else text[position++]
        fun skipWs() {
            while (!atEnd() && (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t')) position++
        }

        fun parseValue(): JsonValue {
            skipWs()
            return when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> { expect("true"); JsonValue.Bool(true) }
                'f' -> { expect("false"); JsonValue.Bool(false) }
                'n' -> { expect("null"); JsonValue.Null }
                else -> if (c == '-' || c.isDigit()) parseNumber()
                else throw IllegalArgumentException("Unexpected character '$c' at $position")
            }
        }

        private fun expect(word: String) {
            if (!text.startsWith(word, position)) throw IllegalArgumentException("Expected $word at $position")
            position += word.length
        }

        private fun parseObject(): JsonValue.Obj {
            position++
            val obj = JsonValue.Obj()
            skipWs()
            if (peek() == '}') { position++; return obj }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                if (next() != ':') throw IllegalArgumentException("Expected ':' at $position")
                obj.fields[key] = parseValue()
                skipWs()
                when (next()) {
                    ',' -> continue
                    '}' -> break
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $position")
                }
            }
            return obj
        }

        private fun parseArray(): JsonValue.Arr {
            position++
            val arr = JsonValue.Arr()
            skipWs()
            if (peek() == ']') { position++; return arr }
            while (true) {
                arr.items.add(parseValue())
                skipWs()
                when (next()) {
                    ',' -> continue
                    ']' -> break
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $position")
                }
            }
            return arr
        }

        private fun parseString(): String {
            if (next() != '"') throw IllegalArgumentException("Expected '\"' at ${position - 1}")
            val sb = StringBuilder()
            while (true) {
                val c = next()
                when (c) {
                    '"' -> break
                    '\\' -> {
                        when (val e = next()) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = text.substring(position, position + 4)
                                position += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseNumber(): JsonValue.Num {
            val start = position
            if (peek() == '-') position++
            while (!atEnd() && peek().isDigit()) position++
            if (!atEnd() && peek() == '.') {
                position++
                while (!atEnd() && peek().isDigit()) position++
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                position++
                if (!atEnd() && (peek() == '+' || peek() == '-')) position++
                while (!atEnd() && peek().isDigit()) position++
            }
            return JsonValue.Num(text.substring(start, position).toDouble())
        }
    }
}
