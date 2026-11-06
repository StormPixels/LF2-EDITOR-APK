package com.lf2ide.mobile

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Pembuat komponen kecil (teks, tombol, chip, kolom isian) untuk editor stage dan BG. */
class UiKit(val ctx: Context) {
    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    fun btn(label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(V_FG)
        setBackgroundColor(V_BG)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = dp(34)
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { onClick() }
    }

    fun chip(label: String, on: Boolean, change: (Boolean) -> Unit): TextView {
        val v = TextView(ctx)
        var state = on
        fun paint() {
            v.setTextColor(if (state) V_BG else V_FG)
            v.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(if (state) V_ACC else V_PANEL)
                setStroke(dp(1), if (state) V_ACC else V_LINE)
            }
        }
        v.text = label
        v.textSize = 12f
        v.gravity = Gravity.CENTER
        v.setPadding(dp(10), dp(6), dp(10), dp(6))
        paint()
        v.setOnClickListener { state = !state; paint(); change(state) }
        return v
    }

    /** kind: 0 teks, 1 bilangan bulat (boleh negatif), 2 desimal. */
    fun input(value: String, kind: Int, onFocus: () -> Unit, onCommit: (String) -> Unit): EditText =
        EditText(ctx).apply {
            inputType = when (kind) {
                1 -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                2 -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                else -> InputType.TYPE_CLASS_TEXT
            }
            setSingleLine()
            textSize = 13f
            setTextColor(V_FG)
            setBackgroundColor(V_BG)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(value)
            setOnFocusChangeListener { _, has -> if (has) onFocus() else onCommit(this.text.toString()) }
            setOnEditorActionListener { v, _, _ -> onCommit(v.text.toString()); v.clearFocus(); true }
        }

    /** Satu kolom berlabel: nama di atas, isian di bawah. */
    fun field(
        label: String, value: String, kind: Int, onFocus: () -> Unit, onCommit: (String) -> Unit, key: String = ""
    ): LinearLayout {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(4), dp(6), dp(2))
        val top = LinearLayout(ctx)
        top.addView(tv(label, 13f, V_FG), lp(0, -2, 1f))
        if (key.isNotEmpty()) top.addView(tv(key, 10f, V_DIM), lp(-2, -2))
        col.addView(top)
        col.addView(input(value, kind, onFocus, onCommit), lp(-1, -2))
        return col
    }
}
