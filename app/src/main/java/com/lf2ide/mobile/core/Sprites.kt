package com.lf2ide.mobile.core

data class Sheet(val start: Int, val end: Int, val file: String, val name: String, val w: Int, val h: Int, val row: Int, val col: Int)
class Cell(val s: Sheet, val sx: Int, val sy: Int)

/** Port dari SpriteSheet.cs; posisi sprite = (c*(w+1), r*(h+1)) seperti IDE asli */
object Sprites {
    private val RE = Regex("""file\((\d*)-(\d*)\): *(.*?) *w: *(\d*) *h: *(\d*) *row: *(\d*) *col: *(\d*)""")

    fun parseSheets(head: String): List<Sheet> = RE.findAll(head).map { m ->
        val g = m.groupValues
        val file = g[3].trim()
        Sheet(g[1].toIntOrNull() ?: 0, g[2].toIntOrNull() ?: 0, file, file.substringAfterLast('\\').substringAfterLast('/').lowercase(),
            g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0, g[7].toIntOrNull() ?: 0)
    }.toList()

    /**
     * `row: N` di .dat = jumlah sprite KE SAMPING per baris, `col: M` = jumlah baris KE BAWAH
     * (IDE asli menukar keduanya saat membaca). Nomor pic dihitung lanjut antar lembar:
     * posisi di lembar = pic - start, sehingga lembar ke-2 file(120-239) mulai lagi dari sel pertama.
     */
    fun cellFor(sheets: List<Sheet>, pic: Int): Cell? {
        val s = sheets.firstOrNull { pic >= it.start && pic <= it.end } ?: return null
        if (s.row <= 0) return null
        val i = pic - s.start
        return Cell(s, (i % s.row) * (s.w + 1), (i / s.row) * (s.h + 1))
    }
}
