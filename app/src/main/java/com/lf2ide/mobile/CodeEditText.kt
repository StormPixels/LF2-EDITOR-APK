package com.lf2ide.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.EditText

/**
 * EditText dengan gutter nomor baris dan sorotan baris aktif (sesuai desain).
 * Tanpa pembungkusan baris, jadi satu baris layout = satu baris teks; hanya baris yang terlihat yang digambar.
 */
class CodeEditText(ctx: Context) : EditText(ctx) {
    var onSel: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val gutterW = 44 * d
    private val numPad = 8 * d
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF64748B.toInt(); typeface = Typeface.MONOSPACE }
    private val numCur = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22D3EE.toInt(); typeface = Typeface.MONOSPACE }
    private val gutterBg = Paint().apply { color = 0xFF0D1524.toInt() }
    private val curBg = Paint().apply { color = 0xFF0F2A4A.toInt() }

    init {
        setPadding((50 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
        onSel?.invoke()
    }

    override fun onDraw(c: Canvas) {
        val lay = layout
        var cur = 0
        if (lay != null) {
            cur = lay.getLineForOffset(maxOf(0, selectionStart))
            val top = (extendedPaddingTop + lay.getLineTop(cur)).toFloat()
            val bot = (extendedPaddingTop + lay.getLineBottom(cur)).toFloat()
            c.drawRect(scrollX.toFloat(), top, (scrollX + width).toFloat(), bot, curBg)
        }
        super.onDraw(c)
        if (lay == null) return
        c.drawRect(scrollX.toFloat(), scrollY.toFloat(), scrollX + gutterW, (scrollY + height).toFloat(), gutterBg)
        num.textSize = textSize * 0.9f
        numCur.textSize = num.textSize
        val first = lay.getLineForVertical(maxOf(0, scrollY - extendedPaddingTop))
        val last = lay.getLineForVertical(maxOf(0, scrollY + height - extendedPaddingTop))
        for (i in first..last) {
            val s = (i + 1).toString()
            val p = if (i == cur) numCur else num
            c.drawText(s, scrollX + gutterW - numPad - p.measureText(s), (extendedPaddingTop + lay.getLineBaseline(i)).toFloat(), p)
        }
    }
}
