package com.lf2ide.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

private class ResizeHandle(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF22D3EE.toInt(); strokeWidth = 2 * d; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..3) {
            val o = i * 6 * d
            c.drawLine(w - o, h - 4 * d, w - 4 * d, h - o, p)
        }
    }
}

/**
 * Pembungkus panel Design: menempel (docked) di layar, atau mengambang (floating) —
 * bisa diseret lewat batang judul dan diubah ukurannya lewat sudut kanan-bawah.
 */
class DesignWindow(ctx: Context, body: View) : FrameLayout(ctx) {
    var floating = false
        private set
    var collapsed = false
        private set
    var fw = 0 // lebar saat mengambang (px)
    var fh = 0 // tinggi saat mengambang (px)
    var onChanged: (() -> Unit)? = null      // mode/ciut berubah: layout perlu diterapkan ulang
    var onSettled: (() -> Unit)? = null      // selesai geser/ubah ukuran: simpan
    var onToggleFloat: (() -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val body = body
    private val btnFloat = ImageButton(ctx)
    private val btnCollapse = TextView(ctx)
    private val handle = ResizeHandle(ctx)
    private var lx = 0f
    private var ly = 0f

    private fun dp(v: Int) = (v * d).toInt()

    init {
        val bar = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0D1524.toInt())
            setPadding(dp(10), 0, dp(2), 0)
        }
        val title = TextView(ctx).apply {
            text = "Design"
            setTextColor(0xFFE2E8F0.toInt())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        btnCollapse.apply {
            text = "▾"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            setOnClickListener { setCollapsed(!collapsed); onChanged?.invoke() }
        }
        bar.addView(btnCollapse, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT))
        btnFloat.apply {
            setImageResource(R.drawable.ic_float)
            setBackgroundColor(0)
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { onToggleFloat?.invoke() }
        }
        bar.addView(btnFloat, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT))

        // seret batang judul = pindahkan jendela (hanya saat mengambang)
        bar.setOnTouchListener { _, e ->
            if (!floating) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = e.rawX; ly = e.rawY; parent?.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> {
                    translationX += e.rawX - lx
                    translationY += e.rawY - ly
                    lx = e.rawX; ly = e.rawY
                    clamp()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onSettled?.invoke()
            }
            true
        }

        // seret sudut kanan-bawah = ubah ukuran
        handle.visibility = View.GONE
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = e.rawX; ly = e.rawY; parent?.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> {
                    fw += (e.rawX - lx).toInt()
                    fh += (e.rawY - ly).toInt()
                    lx = e.rawX; ly = e.rawY
                    clamp()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onSettled?.invoke()
            }
            true
        }

        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
        inner.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(inner, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(handle, LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END))
        setFloating(false)
    }

    fun setFloating(on: Boolean) {
        floating = on
        val g = GradientDrawable().apply {
            setColor(0xFF111B2E.toInt())
            if (on) {
                cornerRadius = 10 * d
                setStroke(maxOf(1, (1.5f * d).toInt()), 0xFF22D3EE.toInt())
            }
        }
        background = g
        elevation = if (on) 16 * d else 0f
        clipToOutline = on
        btnFloat.setImageResource(if (on) R.drawable.ic_dock else R.drawable.ic_float)
        handle.visibility = if (on && !collapsed) View.VISIBLE else View.GONE
    }

    fun setCollapsed(on: Boolean) {
        collapsed = on
        body.visibility = if (on) View.GONE else View.VISIBLE
        btnCollapse.text = if (on) "▸" else "▾"
        handle.visibility = if (floating && !on) View.VISIBLE else View.GONE
    }

    /** Jaga agar jendela tetap di dalam layar dan ukurannya wajar. */
    fun clamp() {
        val p = parent as? View ?: return
        if (!floating || p.width == 0) return
        val minVis = dp(80)
        translationX = translationX.coerceIn(-(fw - minVis).toFloat(), (p.width - minVis).toFloat())
        translationY = translationY.coerceIn(0f, (p.height - dp(36)).toFloat())
        fw = fw.coerceIn(dp(200), maxOf(dp(200), (p.width - translationX).toInt()))
        fh = fh.coerceIn(dp(150), maxOf(dp(150), (p.height - translationY).toInt()))
        val lp = layoutParams ?: return
        val h = if (collapsed) dp(36) else fh
        if (lp.width != fw || lp.height != h) {
            lp.width = fw
            lp.height = h
            layoutParams = lp
        }
    }
}
