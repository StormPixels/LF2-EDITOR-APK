package com.lf2ide.mobile

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.lf2ide.mobile.core.Lang

class HlSpan(color: Int) : ForegroundColorSpan(color)

/** Pewarna sintaks per baris; hanya dipanggil untuk jendela baris yang terlihat. */
class Highlighter(lang: String) {
    private val kw = HashMap<String, Int>()
    private val re = Regex("""#.*|-?\b\d+\b|<?[A-Za-z_]\w*>?:?""")

    init {
        val lists = Lang.keywords[lang] ?: Lang.keywords["object_dat"] ?: emptyMap()
        for ((idx, words) in lists) {
            val color = LIST_COLORS[(idx.toIntOrNull() ?: 0) % LIST_COLORS.size]
            for (w in words) kw[w.lowercase()] = color
        }
    }

    fun apply(e: Editable, start: Int, end: Int) {
        if (end <= start) return
        for (s in e.getSpans(start, end, HlSpan::class.java)) e.removeSpan(s)
        val seg: CharSequence = e.subSequence(start, end)
        for (m in re.findAll(seg)) {
            val t = m.value
            val color: Int? = when {
                t[0] == '#' -> C_COMMENT
                t[0] == '-' || t[0].isDigit() -> C_NUMBER
                else -> kw[t.trim('<', '>', ':').lowercase()]
            }
            if (color != null) {
                e.setSpan(HlSpan(color), start + m.range.first, start + m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    companion object {
        const val C_COMMENT = 0xFF6A9955.toInt()
        const val C_NUMBER = 0xFFD7BA7D.toInt()
        val LIST_COLORS = intArrayOf(0xFF5AA7FF.toInt(), 0xFFC586C0.toInt(), 0xFF4EC9B0.toInt(), 0xFFE0A060.toInt())
    }
}
