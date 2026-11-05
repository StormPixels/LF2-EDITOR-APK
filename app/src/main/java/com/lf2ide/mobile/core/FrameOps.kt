package com.lf2ide.mobile.core

/** Satu frame di teks .dat: nomor, posisi awal `<frame>`, dan posisi tepat setelah `<frame_end>`. */
class FrameRange(val idx: Int, val s: Int, val e: Int)

/** Kepala frame untuk daftar/pencarian: nomor, caption, dan posisi `<frame>`. */
class FrameHead(val idx: Int, val caption: String, val pos: Int)

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
}
