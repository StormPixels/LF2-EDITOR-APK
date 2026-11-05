package com.lf2ide.mobile.core

/** Satu frame di teks .dat: nomor, posisi awal `<frame>`, dan posisi tepat setelah `<frame_end>`. */
class FrameRange(val idx: Int, val s: Int, val e: Int)

/** Kepala frame untuk daftar/pencarian: nomor, caption, dan posisi `<frame>`. */
class FrameHead(val idx: Int, val caption: String, val pos: Int)

/** Ringkasan satu frame untuk daftar visual: field utama dan rentang teksnya. */
class FrameInfo(val idx: Int, val caption: String, val pic: Int?, val wait: Int, val next: Int?, val s: Int, val e: Int)

/** Frame berurutan dengan nama gerakan yang sama (mis. standing 0-3). */
class FrameGroup(val key: String, val frames: List<FrameInfo>)

/** Satu baris header karakter (name, head, walking_speed, ...) beserta rentang nilainya di teks. */
class HeadField(val key: String, val value: String, val s: Int, val e: Int)

/**
 * Operasi teks pada frame (salin frame, salin/tempel move, hapus, cari).
 * Bekerja langsung pada teks mentah supaya field yang tidak dikenal parser tidak hilang.
 */
object FrameOps {
    const val MAX_INDEX = 998

    private val HDR = Regex("""<frame>\s+(\d+)""")
    private val HDR_NAME = Regex("""<frame>\s+(\d+)\s+(\S+)""")
    private val HDR_SUB = Regex("""^<frame>(\s+)\d+""")
    private val NEXT = Regex("""(?<![A-Za-z_])next:\s*(\d+)""")

    // field yang berisi nomor frame (tujuan lompatan)
    private val REF1 = Regex(
        """(?<![A-Za-z_])(next|hit_a|hit_d|hit_j|hit_Fa|hit_Ua|hit_Da|hit_Fj|hit_Uj|hit_Dj|hit_ja|action|fronthurtact|backhurtact|vaction|aaction|jaction|daction|taction|weaponact):(\s*)(\d+)"""
    )
    // itr: dua nomor frame per field
    private val REF2 = Regex("""(?<![A-Za-z_])(catchingact|caughtact):(\s*)(\d+)(\s+)(\d+)""")

    fun ranges(t: String): List<FrameRange> {
        val out = ArrayList<FrameRange>()
        for (m in HDR.findAll(t)) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            val e = t.indexOf("<frame_end>", m.range.first)
            if (e < 0) continue
            out.add(FrameRange(idx, m.range.first, e + 11))
        }
        return out
    }

    fun headers(t: String): List<FrameHead> =
        HDR_NAME.findAll(t).mapNotNull { m ->
            val idx = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            FrameHead(idx, m.groupValues[2], m.range.first)
        }.toList()

    fun indexOf(raw: String): Int? = HDR.find(raw)?.groupValues?.get(1)?.toIntOrNull()

    fun nextOf(raw: String): Int? = NEXT.find(raw)?.groupValues?.get(1)?.toIntOrNull()

    /** Nomor kosong pertama >= from (minimal 1) sampai MAX_INDEX, atau null jika habis. */
    fun freeFrom(used: Set<Int>, from: Int): Int? {
        var i = maxOf(1, from)
        while (i <= MAX_INDEX) {
            if (i !in used) return i
            i++
        }
        return null
    }

    fun withIndex(raw: String, ni: Int): String =
        HDR_SUB.replace(raw) { m -> "<frame>" + m.groupValues[1] + ni }

    private fun mp(s: String, m: Map<Int, Int>): String {
        val n = s.toIntOrNull() ?: return s
        val r = if (n != 0) m[n] else null
        return r?.toString() ?: s
    }

    /** Ganti tujuan lompatan yang menunjuk ke frame dalam peta (nomor 0 = "tidak ada", tidak diubah). */
    fun remap(raw: String, m: Map<Int, Int>): String {
        var r = REF2.replace(raw) { g ->
            g.groupValues[1] + ":" + g.groupValues[2] + mp(g.groupValues[3], m) + g.groupValues[4] + mp(g.groupValues[5], m)
        }
        r = REF1.replace(r) { g -> g.groupValues[1] + ":" + g.groupValues[2] + mp(g.groupValues[3], m) }
        return r
    }

    /** Frame-frame sebuah move: mulai dari `start`, ikuti `next` sampai 0, >=999, hilang, atau berulang. */
    fun chain(t: String, start: Int, limit: Int = 64): List<String> {
        val byIdx = HashMap<Int, FrameRange>()
        for (r in ranges(t)) if (!byIdx.containsKey(r.idx)) byIdx[r.idx] = r
        val out = ArrayList<String>()
        val seen = HashSet<Int>()
        var i = start
        while (out.size < limit) {
            val r = byIdx[i] ?: break
            if (!seen.add(i)) break
            val raw = t.substring(r.s, r.e)
            out.add(raw)
            val n = nextOf(raw) ?: break
            if (n == 0 || n >= 999) break
            i = n
        }
        return out
    }

    /**
     * Beri nomor baru (mulai dari `from`, hanya nomor yang belum dipakai) untuk sekelompok frame hasil salinan,
     * lalu arahkan ulang lompatan antar frame di dalam kelompok itu. Null jika nomor kosong tidak cukup.
     */
    fun relocate(raws: List<String>, used: Set<Int>, from: Int): List<String>? {
        val taken = HashSet(used)
        val map = LinkedHashMap<Int, Int>()
        val olds = ArrayList<Int>()
        var next = from
        for (raw in raws) {
            val o = indexOf(raw) ?: return null
            val ni = freeFrom(taken, next) ?: return null
            olds.add(o)
            taken.add(ni)
            map[o] = ni
            next = ni + 1
        }
        return raws.mapIndexed { k, raw -> remap(withIndex(raw, map.getValue(olds[k])), map) }
    }

    /** Berapa frame lain (selain yang mulai di `exceptPos`) yang `next`-nya menuju `idx`. */
    fun referrers(t: String, idx: Int, exceptPos: Int): Int {
        var n = 0
        for (r in ranges(t)) {
            if (r.s == exceptPos) continue
            val v = nextOf(t.substring(r.s, r.e))
            if (v == idx) n++
        }
        return n
    }

    /** Hapus frame [s, e) beserta sampai dua pemisah baris setelahnya; kembalikan batas akhir yang dihapus. */
    fun deleteEnd(t: String, e: Int): Int {
        var j = e
        var nl = 0
        while (j < t.length && nl < 2 && (t[j] == '\n' || t[j] == '\r' || t[j] == ' ' || t[j] == '\t')) {
            if (t[j] == '\n') nl++
            j++
        }
        return j
    }

    // ---------- ringkasan, kelompok, dan penyuntingan field ----------
    private val SECT = Regex("""(?<![A-Za-z_])(bpoint|opoint|cpoint|wpoint|itr|bdy):""")
    private val HDR_CAP = Regex("""^(<frame>\s+\d+\s+)\S+""")
    private val GKEY = Regex("""[_\-]?\d+$""")
    private val HEADRE = Regex("""(?m)^[ \t]*([A-Za-z_][A-Za-z_0-9]*):?[ \t]+([^\r\n]*?)[ \t]*$""")
    private val KEYRE = HashMap<String, Regex>()

    private fun keyRe(k: String): Regex =
        KEYRE.getOrPut(k) { Regex("(?<![A-Za-z_])" + Regex.escape(k) + ":(\\s*)(\\S+)") }

    /** Batas akhir bagian field utama frame (sebelum seksi bdy/itr/point pertama atau <frame_end>). */
    private fun topLimit(raw: String): Int {
        val endTok = raw.lastIndexOf("<frame_end>")
        val limit = if (endTok >= 0) endTok else raw.length
        val m = SECT.find(raw)
        return if (m != null) minOf(limit, m.range.first) else limit
    }

    fun intIn(raw: String, key: String): Int? =
        keyRe(key).find(raw.substring(0, topLimit(raw)))?.groupValues?.get(2)?.toIntOrNull()

    fun infos(t: String): List<FrameInfo> {
        val out = ArrayList<FrameInfo>()
        for (r in ranges(t)) {
            val raw = t.substring(r.s, r.e)
            val cap = HDR_NAME.find(raw)?.groupValues?.get(2) ?: ""
            out.add(FrameInfo(r.idx, cap, intIn(raw, "pic"), intIn(raw, "wait") ?: 0, intIn(raw, "next"), r.s, r.e))
        }
        return out
    }

    fun groupKey(caption: String): String = caption.lowercase().replace(GKEY, "")

    fun group(list: List<FrameInfo>): List<FrameGroup> {
        val out = ArrayList<FrameGroup>()
        var curKey: String? = null
        var buf = ArrayList<FrameInfo>()
        for (f in list) {
            val k = groupKey(f.caption)
            if (k != curKey) {
                if (buf.isNotEmpty()) out.add(FrameGroup(curKey ?: "", buf))
                buf = ArrayList()
                curKey = k
            }
            buf.add(f)
        }
        if (buf.isNotEmpty()) out.add(FrameGroup(curKey ?: "", buf))
        return out
    }

    /**
     * Ubah (atau hapus bila value null) satu field angka/teks pada bagian utama frame.
     * Field yang belum ada ditambahkan tepat setelah baris header frame.
     */
    fun setTop(raw: String, key: String, value: String?): String {
        val cut = topLimit(raw)
        var head = raw.substring(0, cut)
        val tail = raw.substring(cut)
        val m = keyRe(key).find(head)
        if (m != null) {
            val g = m.groups[2]!!
            head = if (value == null) {
                head.substring(0, m.range.first).trimEnd(' ', '\t') + head.substring(m.range.last + 1)
            } else {
                head.substring(0, g.range.first) + value + head.substring(g.range.last + 1)
            }
        } else if (value != null) {
            val nl = head.indexOf('\n')
            head = if (nl >= 0) head.substring(0, nl) + "\n   " + key + ": " + value + head.substring(nl)
            else head + "\n   " + key + ": " + value
        }
        return head + tail
    }

    /** Ganti caption pada header `<frame> idx caption`. */
    fun withCaption(raw: String, caption: String): String =
        HDR_CAP.replace(raw) { m -> m.groupValues[1] + caption }

    /** Baris `kunci nilai` / `kunci: nilai` di header karakter (sebelum frame pertama); baris file(...) tidak ikut. */
    fun headFields(t: String): List<HeadField> {
        val fi = t.indexOf("<frame>")
        val head = t.substring(0, if (fi < 0) t.length else fi)
        val out = ArrayList<HeadField>()
        for (m in HEADRE.findAll(head)) {
            val v = m.groups[2] ?: continue
            out.add(HeadField(m.groupValues[1], v.value, v.range.first, v.range.last + 1))
        }
        return out
    }
}
