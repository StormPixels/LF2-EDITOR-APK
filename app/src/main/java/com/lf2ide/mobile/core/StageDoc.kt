package com.lf2ide.mobile.core

/** Satu baris musuh di dalam phase: `id: 30 hp: 50 times: 1 ratio: .7 x: 100 <boss> # komentar`. */
class StageEnemy(val s: Int, val e: Int, val line: String) {
    val data: String get() { val i = line.indexOf('#'); return if (i >= 0) line.substring(0, i) else line }
    val comment: String get() { val i = line.indexOf('#'); return if (i >= 0) line.substring(i + 1).trim() else "" }
    fun get(key: String): String? = StageDoc.keyRe(key).find(data)?.groupValues?.get(1)
    val id: Int? get() = get("id")?.toIntOrNull()
    val x: Int? get() = get("x")?.toIntOrNull()
    val boss: Boolean get() = data.contains("<boss>")
    val soldier: Boolean get() = data.contains("<soldier>")
}

/** Satu `<phase> ... <phase_end>`: s = posisi `<phase>`, ls = awal barisnya, e = akhir `<phase_end>`. */
class StagePhase(
    val s: Int, val ls: Int, val e: Int, val endTag: Int,
    val bound: Int?, val music: String?, val comment: String, val enemies: List<StageEnemy>
)

class StageInfo(val id: Int?, val phases: List<StagePhase>)

/** Pembaca dan penyunting teks stage .dat; semua penyuntingan berupa Edit pada teks mentah (komentar dan tata letak tetap). */
object StageDoc {
    private val cache = HashMap<String, Regex>()
    private val ID_ENEMY = Regex("""(?<![A-Za-z_])id\s*:""")
    private val BOUND = Regex("""(?<![A-Za-z_])bound\s*:\s*(\S+)""")
    private val MUSIC = Regex("""(?<![A-Za-z_])music\s*:\s*(\S+)""")
    const val DEFAULT_INDENT = "                "

    fun keyRe(k: String): Regex =
        cache.getOrPut(k) { Regex("(?<![A-Za-z_])" + Regex.escape(k) + "\\s*:\\s*(\\S+)") }

    fun parse(t: String): StageInfo {
        val sid = Regex("""<stage>\s*id\s*:\s*(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val out = ArrayList<StagePhase>()
        var from = 0
        while (true) {
            val s = t.indexOf("<phase>", from)
            if (s < 0) break
            val et = t.indexOf("<phase_end>", s)
            if (et < 0) break
            val e = et + "<phase_end>".length
            val ls = t.lastIndexOf('\n', s - 1) + 1
            val enemies = ArrayList<StageEnemy>()
            var bound: Int? = null
            var music: String? = null
            var comment = ""
            var pos = s
            var first = true
            while (pos < et) {
                var le = t.indexOf('\n', pos)
                if (le < 0 || le > et) le = et
                val raw = t.substring(pos, le).trimEnd('\r')
                if (first) {
                    first = false
                    val hash = raw.indexOf('#')
                    val data = if (hash >= 0) raw.substring(0, hash) else raw
                    if (hash >= 0) comment = raw.substring(hash + 1).trim()
                    bound = BOUND.find(data)?.groupValues?.get(1)?.toIntOrNull()
                    MUSIC.find(data)?.let { music = it.groupValues[1] }
                } else if (raw.isNotBlank()) {
                    val data = raw.substringBefore('#')
                    val mm = MUSIC.find(data)
                    if (mm != null) music = mm.groupValues[1]
                    else if (ID_ENEMY.containsMatchIn(data)) enemies.add(StageEnemy(pos, pos + raw.length, raw))
                }
                pos = le + 1
            }
            out.add(StagePhase(s, ls, e, et, bound, music, comment, enemies))
            from = e
        }
        return StageInfo(sid, out)
    }

    // ---------- penyuntingan satu baris ----------
    fun setLineKV(line: String, key: String, value: String?): String {
        val ci = line.indexOf('#')
        val data = if (ci >= 0) line.substring(0, ci) else line
        val rest = if (ci >= 0) line.substring(ci) else ""
        val m = keyRe(key).find(data)
        val nd: String
        if (m != null) {
            val g = m.groups[1]!!
            nd = if (value == null) data.substring(0, m.range.first).trimEnd() + data.substring(m.range.last + 1).let { if (it.isBlank()) "" else " $it" }
            else data.substring(0, g.range.first) + value + data.substring(g.range.last + 1)
        } else if (value != null) {
            val core = data.trimEnd()
            val tag = core.indexOf('<')
            nd = if (tag >= 0) core.substring(0, tag).trimEnd() + "  $key: $value  " + core.substring(tag) else "$core  $key: $value"
        } else {
            nd = data
        }
        return if (rest.isEmpty()) nd else (if (nd.endsWith(" ")) nd else "$nd  ") + rest
    }

    fun setLineTag(line: String, tag: String, on: Boolean): String {
        val ci = line.indexOf('#')
        val data = if (ci >= 0) line.substring(0, ci) else line
        val rest = if (ci >= 0) line.substring(ci) else ""
        val has = data.contains(tag)
        val nd = when {
            on && !has -> data.trimEnd() + " " + tag
            !on && has -> data.replace(tag, "").trimEnd()
            else -> data
        }
        return if (rest.isEmpty()) nd else (if (nd.endsWith(" ")) nd else "$nd  ") + rest
    }

    // ---------- musuh ----------
    fun setEnemy(e: StageEnemy, key: String, value: String?) = Edit(e.s, e.e, setLineKV(e.line, key, value))

    fun setEnemyTag(e: StageEnemy, tag: String, on: Boolean) = Edit(e.s, e.e, setLineTag(e.line, tag, on))

    fun addEnemy(t: String, p: StagePhase): Edit {
        val indent = p.enemies.lastOrNull()?.line?.takeWhile { it == ' ' || it == '\t' } ?: DEFAULT_INDENT
        val ls = t.lastIndexOf('\n', p.endTag - 1) + 1
        return Edit(ls, ls, indent + "id: 30  hp: 50  times: 1\n")
    }

    fun copyEnemy(e: StageEnemy) = Edit(e.e, e.e, "\n" + e.line)

    fun deleteEnemy(t: String, e: StageEnemy): Edit {
        var end = e.e
        if (end < t.length && t[end] == '\r') end++
        if (end < t.length && t[end] == '\n') end++
        return Edit(e.s, end, "")
    }

    // ---------- phase ----------
    fun setPhaseField(t: String, p: StagePhase, key: String, value: String?): Edit {
        val raw = t.substring(p.s, p.e)
        val m = keyRe(key).find(raw)
        val nr: String = when {
            m != null && value == null -> raw.substring(0, m.range.first).trimEnd(' ', '\t') + raw.substring(m.range.last + 1)
            m != null -> { val g = m.groups[1]!!; raw.substring(0, g.range.first) + value + raw.substring(g.range.last + 1) }
            value == null -> raw
            key == "bound" -> raw.replaceFirst("<phase>", "<phase> bound: $value")
            else -> {
                val nl = raw.indexOf('\n')
                val indent = p.enemies.firstOrNull()?.line?.takeWhile { it == ' ' || it == '\t' } ?: DEFAULT_INDENT
                if (nl >= 0) raw.substring(0, nl) + "\n" + indent + key + ": " + value + raw.substring(nl)
                else raw
            }
        }
        return Edit(p.s, p.e, nr)
    }

    fun addPhase(t: String): Edit {
        val block = "        <phase> bound: 1500\n$DEFAULT_INDENT" + "id: 30  hp: 50  times: 1\n        <phase_end>\n"
        val se = t.indexOf("<stage_end>")
        if (se >= 0) {
            val ls = t.lastIndexOf('\n', se - 1) + 1
            return Edit(ls, ls, block + "\n")
        }
        return Edit(t.length, t.length, (if (t.endsWith("\n") || t.isEmpty()) "" else "\n") + block)
    }

    fun copyPhase(t: String, p: StagePhase) = Edit(p.e, p.e, "\n\n" + t.substring(p.ls, p.e))

    fun deletePhase(t: String, p: StagePhase) = Edit(p.ls, FrameOps.deleteEnd(t, p.e), "")

    fun movePhase(t: String, info: StageInfo, i: Int, dir: Int): Edit? {
        val j = i + dir
        if (i !in info.phases.indices || j !in info.phases.indices) return null
        val a = info.phases[minOf(i, j)]
        val b = info.phases[maxOf(i, j)]
        val blockA = t.substring(a.ls, a.e)
        val between = t.substring(a.e, b.ls)
        val blockB = t.substring(b.ls, b.e)
        return Edit(a.ls, b.e, blockB + between + blockA)
    }

    fun setStageId(t: String, value: Int): Edit? {
        val m = Regex("""(<stage>\s*id\s*:\s*)(\d+)""").find(t)
        if (m != null) {
            val g = m.groups[2]!!
            return Edit(g.range.first, g.range.last + 1, value.toString())
        }
        val i = t.indexOf("<stage>")
        if (i < 0) return null
        return Edit(i + 7, i + 7, " id: $value")
    }
}
