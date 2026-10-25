package com.lf2ide.mobile.core

/** Port dari LF2DataUtils.ReadFrame */
object FrameParser {
    private val TOP: Set<String> = Schema.FRAME_FIELDS.map { "$it:" }.toSet()

    fun read(raw: String): Frame? = read(Tokenizer.tokenize(raw))

    fun read(t: List<String>): Frame? {
        if (!(t.size > 2 && t[0] == "<frame>" && t[t.size - 1] == "<frame_end>")) return null
        val f = Frame(num(t[1]), t[2])
        var cur: Sect? = null
        var i = 3
        while (i < t.size) {
            val tok = t[i]
            val c = cur
            if (c == null) {
                if (tok in TOP) {
                    i++
                    f.top[tok.dropLast(1)] = num(t.getOrNull(i))
                } else if (tok == "sound:") {
                    i++
                    f.sound = t.getOrNull(i)
                } else if (tok.endsWith(":") && Schema.fields.containsKey(tok.dropLast(1))) {
                    val name = tok.dropLast(1)
                    val s = Sect(name)
                    cur = s
                    when (name) {
                        "bdy" -> f.bdys.add(s)
                        "itr" -> f.itrs.add(s)
                        else -> f.points[name] = s
                    }
                }
            } else if (tok == c.type + "_end:") {
                cur = null
            } else {
                val key = tok.dropLast(1)
                val fld = Schema.fields[c.type]!!.firstOrNull { it.first == key }
                val pair = Schema.pairs[c.type]?.firstOrNull { it.first == key }
                if (fld != null) {
                    i++
                    val n = num(t.getOrNull(i))
                    c.v[key] = if (fld.second == 'b') (if (n != 0) 1 else 0) else n
                } else if (pair != null) {
                    i++
                    c.v[pair.second] = num(t.getOrNull(i))
                    i++
                    c.v[pair.third] = num(t.getOrNull(i))
                }
            }
            i++
        }
        return f
    }

    private fun num(s: String?): Int = s?.toIntOrNull() ?: throw DataSyntaxError("Angka tidak valid: $s")
}

/** Port dari ToString() tiap kelas (Frame, Itr, Bdy, ...) */
object FrameWriter {
    private fun sect(s: Sect): String {
        val fl = Schema.fields[s.type]!!
        val body = StringBuilder(fl.filter { s.v.containsKey(it.first) }.joinToString("  ") { "${it.first}: ${s.v[it.first]}" })
        val pr = Schema.pairs[s.type]?.filter { s.v.containsKey(it.second) && s.v.containsKey(it.third) }
        if (!pr.isNullOrEmpty()) {
            body.append("  \n      ").append(pr.joinToString("  ") { "${it.first}: ${s.v[it.second]} ${s.v[it.third]}" })
        }
        return "   ${s.type}:\n      $body\n   ${s.type}_end:"
    }

    fun frameToString(f: Frame): String {
        val sb = StringBuilder("<frame> ${f.index} ${f.caption}\n   ")
        sb.append(Schema.FRAME_FIELDS.filter { f.top.containsKey(it) }.joinToString("  ") { "$it: ${f.top[it]}" })
        f.sound?.let { sb.append("\n   sound: ").append(it) }
        for (n in listOf("bpoint", "opoint", "cpoint", "wpoint")) f.points[n]?.let { sb.append('\n').append(sect(it)) }
        for (s in f.itrs) sb.append('\n').append(sect(s))
        for (s in f.bdys) sb.append('\n').append(sect(s))
        return sb.append("\n<frame_end>").toString()
    }
}
