package com.lf2ide.mobile.core

/** Pasangan `kunci: nilai` dengan posisi kunci (ks) dan nilai [vs, ve) di teks. */
class KV(val key: String, val value: String, val ks: Int, val vs: Int, val ve: Int)

/** Satu `layer: ... layer_end` pada file background; path = baris nama gambar. */
class BgLayer(
    val s: Int, val e: Int, val ls: Int,
    val path: String, val pathS: Int, val pathE: Int,
    val attrs: List<KV>
) {
    fun get(key: String): String? = attrs.firstOrNull { it.key == key }?.value
    fun int(key: String): Int? = get(key)?.trim()?.toIntOrNull()
}

class BgInfo(val header: List<KV>, val headEnd: Int, val layers: List<BgLayer>) {
    fun get(key: String): String? = header.firstOrNull { it.key == key }?.value
    fun int(key: String): Int? = get(key)?.trim()?.toIntOrNull()
}

/** Pembaca dan penyunting file background (.dat): header (width, zboundary, shadow, ...) dan layer-nya. */
object BgDoc {
    private val KEY = Regex("""([A-Za-z_][A-Za-z_0-9]*)\s*:""")

    /** Semua `kunci: nilai` pada [from, to); komentar `#` diabaikan; nilai berakhir di kunci berikutnya. */
    fun kvs(t: String, from: Int, to: Int): List<KV> {
        val out = ArrayList<KV>()
        var pos = from
        while (pos < to) {
            var le = t.indexOf('\n', pos)
            if (le < 0 || le > to) le = to
            var lineEnd = le
            val hash = t.indexOf('#', pos)
            if (hash in pos until lineEnd) lineEnd = hash
            val line = t.substring(pos, lineEnd)
            val ms = KEY.findAll(line).toList()
            for ((i, m) in ms.withIndex()) {
                var vs = pos + m.range.last + 1
                var ve = if (i + 1 < ms.size) pos + ms[i + 1].range.first else lineEnd
                while (vs < ve && t[vs].isWhitespace()) vs++
                while (ve > vs && t[ve - 1].isWhitespace()) ve--
                out.add(KV(m.groupValues[1], t.substring(vs, ve), pos + m.range.first, vs, ve))
            }
            pos = le + 1
        }
        return out
    }

    private fun findLayerStart(t: String, from: Int): Int {
        var i = t.indexOf("layer:", from)
        while (i > 0 && (t[i - 1].isLetterOrDigit() || t[i - 1] == '_')) i = t.indexOf("layer:", i + 1)
        return i
    }

    fun parse(t: String): BgInfo {
        val layers = ArrayList<BgLayer>()
        var firstLs = -1
        var from = 0
        while (true) {
            val s = findLayerStart(t, from)
            if (s < 0) break
            val ee = t.indexOf("layer_end", s)
            if (ee < 0) break
            val e = ee + "layer_end".length
            val ls = t.lastIndexOf('\n', s - 1) + 1
            if (firstLs < 0) firstLs = ls
            // baris pertama yang berisi data setelah "layer:" = nama gambar (bila tanpa titik dua)
            var pos = s + "layer:".length
            var path = ""
            var ps = -1
            var pe = -1
            var attrFrom = pos
            while (pos < ee) {
                var le = t.indexOf('\n', pos)
                if (le < 0 || le > ee) le = ee
                var lineEnd = le
                val hash = t.indexOf('#', pos)
                if (hash in pos until lineEnd) lineEnd = hash
                var a = pos
                var b = lineEnd
                while (a < b && t[a].isWhitespace()) a++
                while (b > a && t[b - 1].isWhitespace()) b--
                if (a < b) {
                    val seg = t.substring(a, b)
                    if (!seg.contains(':')) { path = seg; ps = a; pe = b; attrFrom = le }
                    break
                }
                pos = le + 1
            }
            layers.add(BgLayer(s, e, ls, path, ps, pe, kvs(t, attrFrom, ee)))
            from = e
        }
        val headEnd = if (firstLs >= 0) firstLs else t.length
        return BgInfo(kvs(t, 0, headEnd), headEnd, layers)
    }

    // ---------- header ----------
    fun setHeader(t: String, info: BgInfo, key: String, value: String): Edit {
        val kv = info.header.firstOrNull { it.key == key }
        if (kv != null) return Edit(kv.vs, kv.ve, value)
        return Edit(info.headEnd, info.headEnd, "$key: $value\n")
    }

    // ---------- layer ----------
    fun setLayerAttr(t: String, l: BgLayer, key: String, value: String?): Edit? {
        val kv = l.attrs.firstOrNull { it.key == key }
        if (kv != null) return if (value == null) Edit(kv.ks, kv.ve, "") else Edit(kv.vs, kv.ve, value)
        if (value == null) return null
        val endTok = l.e - "layer_end".length
        val ls = t.lastIndexOf('\n', endTok - 1) + 1
        return Edit(ls, ls, "  $key: $value\n")
    }

    fun setLayerPath(l: BgLayer, path: String): Edit =
        if (l.pathS >= 0) Edit(l.pathS, l.pathE, path)
        else Edit(l.s + "layer:".length, l.s + "layer:".length, "\n  $path")

    fun addLayer(t: String, info: BgInfo): Edit {
        val last = info.layers.lastOrNull()
        val path = last?.path?.ifEmpty { null } ?: "bg\\new\\layer.bmp"
        val w = info.int("width") ?: 1000
        val block = "layer:\n  $path\n  transparency: 0  width: $w  x: 0  y: 0\nlayer_end\n"
        if (last != null) return Edit(last.e, last.e, "\n\n" + block.trimEnd('\n'))
        val pre = if (t.isEmpty() || t.endsWith("\n")) "" else "\n"
        return Edit(t.length, t.length, pre + "\n" + block)
    }

    fun copyLayer(t: String, l: BgLayer) = Edit(l.e, l.e, "\n\n" + t.substring(l.ls, l.e))

    fun deleteLayer(t: String, l: BgLayer) = Edit(l.ls, FrameOps.deleteEnd(t, l.e), "")

    fun moveLayer(t: String, info: BgInfo, i: Int, dir: Int): Edit? {
        val j = i + dir
        if (i !in info.layers.indices || j !in info.layers.indices) return null
        val a = info.layers[minOf(i, j)]
        val b = info.layers[maxOf(i, j)]
        return Edit(a.ls, b.e, t.substring(b.ls, b.e) + t.substring(a.e, b.ls) + t.substring(a.ls, a.e))
    }
}
