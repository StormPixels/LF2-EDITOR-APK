package com.lf2ide.mobile.core

/** Satu temuan pemeriksaan: level 0 = kesalahan, 1 = peringatan; pos = posisi `<frame>` di teks (0 bila umum). */
class Problem(val level: Int, val idx: Int?, val pos: Int, val text: String)

/** Pemeriksaan data .dat objek: nomor ganda, lompatan ke frame yang tidak ada, field kurang, gambar di luar lembar. */
object Validator {
    private val REQUIRED = listOf("pic", "state", "wait", "next", "centerx", "centery")
    private val BLOCK = Regex("""(?s)(itr|bdy):(.*?)\1_end:""")
    private val BOX_KEYS = listOf("kind", "x", "y", "w", "h")

    fun check(t: String, sheetRanges: List<IntRange>? = null): List<Problem> {
        val out = ArrayList<Problem>()
        val opens = Regex("<frame>").findAll(t).count()
        val closes = Regex("<frame_end>").findAll(t).count()
        if (opens != closes) out.add(Problem(0, null, 0, "Jumlah <frame> ($opens) dan <frame_end> ($closes) tidak sama"))

        val rs = FrameOps.ranges(t)
        val all = rs.map { it.idx }.toSet()
        val seen = HashSet<Int>()
        for (r in rs) {
            val raw = t.substring(r.s, r.e)
            if (!seen.add(r.idx)) out.add(Problem(0, r.idx, r.s, "Nomor frame ${r.idx} dipakai lebih dari sekali"))

            for (k in REQUIRED) {
                if (FrameOps.intIn(raw, k) == null) out.add(Problem(1, r.idx, r.s, "Frame ${r.idx}: field $k belum ada"))
            }

            // hanya next dan hit_* yang pasti menuju frame di file yang sama
            for ((k, v) in FrameOps.refs(raw)) {
                if (k != "next" && !k.startsWith("hit_")) continue
                if (v >= 999) continue
                if (v == 0 && k != "next") continue
                if (v !in all) out.add(Problem(0, r.idx, r.s, "Frame ${r.idx}: $k menuju frame $v yang tidak ada"))
            }

            val pic = FrameOps.intIn(raw, "pic")
            if (pic != null && sheetRanges != null && sheetRanges.isNotEmpty() && sheetRanges.none { pic in it }) {
                out.add(Problem(1, r.idx, r.s, "Frame ${r.idx}: pic $pic tidak ada di lembar gambar mana pun"))
            }

            for (m in BLOCK.findAll(raw)) {
                val type = m.groupValues[1]
                val body = m.groupValues[2]
                for (k in BOX_KEYS) {
                    if (!Regex("(?<![A-Za-z_])$k:").containsMatchIn(body)) {
                        out.add(Problem(1, r.idx, r.s, "Frame ${r.idx}: $type tanpa field $k"))
                    }
                }
                for (k in listOf("w", "h")) {
                    val v = Regex("(?<![A-Za-z_])$k:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                    if (v != null && v <= 0) out.add(Problem(1, r.idx, r.s, "Frame ${r.idx}: $type punya $k = $v (tidak positif)"))
                }
            }
        }
        return out
    }
}
