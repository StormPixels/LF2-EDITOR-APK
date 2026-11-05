package com.lf2ide.mobile.core

/** Perubahan satu frame terhadap versi asli: '+' baru, '-' dihapus, '~' berubah. */
class Change(val kind: Char, val idx: Int, val caption: String, val pos: Int, val lines: List<String>)

/** Penyuntingan teks (ganti [s, e) dengan text) untuk memulihkan frame. */
class Edit(val s: Int, val e: Int, val text: String)

/** Perbandingan teks .dat dengan versi asli: daftar perubahan (was/now) dan pemulihan per frame. */
object Diff {
    private val KV = Regex("""(?<![A-Za-z_])([A-Za-z_][A-Za-z_0-9]*):\s*(\S+)""")
    private val BLOCK = Regex("""(?s)(itr|bdy|wpoint|opoint|cpoint|bpoint):(.*?)\1_end:""")
    private val WS = Regex("""\s+""")

    private fun norm(s: String) = s.trim().replace(WS, " ")

    private fun short(s: String) = if (s.length > 90) s.substring(0, 90) + "…" else s

    private fun topMap(raw: String): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        val top = FrameOps.topPart(raw)
        val nl = top.indexOf('\n')
        val body = if (nl >= 0) top.substring(nl) else ""
        for (x in KV.findAll(body)) if (!m.containsKey(x.groupValues[1])) m[x.groupValues[1]] = x.groupValues[2]
        return m
    }

    private fun blocks(raw: String): List<String> =
        BLOCK.findAll(raw).map { it.groupValues[1] + ": " + norm(it.groupValues[2]) }.toList()

    private fun captionOf(raw: String): String = FrameOps.headers(raw).firstOrNull()?.caption ?: ""

    private fun fieldDiff(a: String, b: String): List<String> {
        val out = ArrayList<String>()
        val ca = captionOf(a)
        val cb = captionOf(b)
        if (ca != cb) out.add("nama: $ca → $cb")
        val ma = topMap(a)
        val mb = topMap(b)
        val keys = LinkedHashSet<String>()
        keys.addAll(ma.keys)
        keys.addAll(mb.keys)
        for (k in keys) {
            val x = ma[k]
            val y = mb[k]
            if (x != y) out.add("$k: ${x ?: "—"} → ${y ?: "—"}")
        }
        val rest = blocks(a).toMutableList()
        val added = ArrayList<String>()
        for (x in blocks(b)) if (!rest.remove(x)) added.add(x)
        for (x in rest) out.add("− " + short(x))
        for (x in added) out.add("+ " + short(x))
        return out
    }

    fun frames(orig: String, now: String): List<Change> {
        val io = FrameOps.infos(orig)
        val inw = FrameOps.infos(now)
        val mo = LinkedHashMap<Int, FrameInfo>()
        for (f in io) if (!mo.containsKey(f.idx)) mo[f.idx] = f
        val mn = LinkedHashMap<Int, FrameInfo>()
        for (f in inw) if (!mn.containsKey(f.idx)) mn[f.idx] = f
        val out = ArrayList<Change>()
        for (n in mn.values) {
            val o = mo[n.idx]
            if (o == null) {
                out.add(Change('+', n.idx, n.caption, n.s, listOf("frame baru")))
            } else {
                val a = orig.substring(o.s, o.e)
                val b = now.substring(n.s, n.e)
                if (norm(a) != norm(b)) out.add(Change('~', n.idx, n.caption, n.s, fieldDiff(a, b)))
            }
        }
        for (o in mo.values) {
            if (mn.containsKey(o.idx)) continue
            val nextAlive = mn.values.firstOrNull { it.idx > o.idx }
            out.add(Change('-', o.idx, o.caption, nextAlive?.s ?: now.length, listOf("frame dihapus")))
        }
        out.sortBy { it.idx }
        return out
    }

    /** Baris perubahan pada header karakter (name, walking_speed, ...). */
    fun headerChanges(orig: String, now: String): List<String> {
        val a = LinkedHashMap<String, String>()
        for (h in FrameOps.headFields(orig)) if (!a.containsKey(h.key)) a[h.key] = h.value
        val b = LinkedHashMap<String, String>()
        for (h in FrameOps.headFields(now)) if (!b.containsKey(h.key)) b[h.key] = h.value
        val keys = LinkedHashSet<String>()
        keys.addAll(a.keys)
        keys.addAll(b.keys)
        val out = ArrayList<String>()
        for (k in keys) if (a[k] != b[k]) out.add("$k: ${a[k] ?: "—"} → ${b[k] ?: "—"}")
        return out
    }

    /** Penyuntingan untuk mengembalikan satu frame ke bentuk aslinya; null bila tak bisa ditentukan. */
    fun restore(now: String, orig: String, ch: Change): Edit? {
        val rn = FrameOps.ranges(now)
        val ro = FrameOps.ranges(orig)
        val cur = rn.firstOrNull { it.idx == ch.idx }
        val old = ro.firstOrNull { it.idx == ch.idx }
        return when (ch.kind) {
            '~' -> if (cur != null && old != null) Edit(cur.s, cur.e, orig.substring(old.s, old.e)) else null
            '+' -> if (cur != null) Edit(cur.s, FrameOps.deleteEnd(now, cur.e), "") else null
            else -> {
                if (old == null) return null
                val raw = orig.substring(old.s, old.e)
                val before = ro.takeWhile { it.idx != ch.idx }.map { it.idx }.reversed()
                val prev = before.firstNotNullOfOrNull { id -> rn.firstOrNull { it.idx == id } }
                when {
                    prev != null -> Edit(prev.e, prev.e, "\n\n" + raw)
                    rn.isNotEmpty() -> Edit(rn[0].s, rn[0].s, raw + "\n\n")
                    else -> Edit(now.length, now.length, raw)
                }
            }
        }
    }
}
