package com.lf2ide.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lf2ide.mobile.core.Cell
import com.lf2ide.mobile.core.Frame
import com.lf2ide.mobile.core.FrameParser
import com.lf2ide.mobile.core.FrameWriter
import com.lf2ide.mobile.core.Schema
import com.lf2ide.mobile.core.Sect
import com.lf2ide.mobile.core.Sheet
import com.lf2ide.mobile.core.Sprites
import kotlin.math.hypot
import kotlin.math.roundToInt

class Sel(val type: String, val i: Int)

private const val C_PANEL = 0xFF262C31.toInt()
private const val C_BG = 0xFF1E2327.toInt()
private const val C_FG = 0xFFD6DDE3.toInt()
private const val C_DIM = 0xFF8A96A0.toInt()
private const val C_ACC = 0xFF4AA3FF.toInt()
private val COL = mapOf(
    "bdy" to 0xFF2FBF5F.toInt(), "itr" to 0xFFE0443E.toInt(), "wpoint" to 0xFFE0A020.toInt(),
    "opoint" to 0xFF3B82F6.toInt(), "cpoint" to 0xFFA855F7.toInt(), "bpoint" to 0xFF14B8A6.toInt()
)
private val POINTS = listOf("wpoint", "opoint", "cpoint", "bpoint")

private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

fun Frame.obj(s: Sel?): Sect? {
    if (s == null) return null
    return when (s.type) {
        "itr" -> itrs.getOrNull(s.i)
        "bdy" -> bdys.getOrNull(s.i)
        else -> points[s.type]
    }
}

/** Kanvas Design (pengganti DrawBox): sprite + kotak itr/bdy + titik; ketuk untuk memilih, geser untuk memindah. */
class DesignCanvas(ctx: Context) : View(ctx) {
    var frame: Frame? = null
    var cell: Cell? = null
    var bmp: Bitmap? = null
    var sel: Sel? = null
    val vis = HashMap<String, Boolean>()
    var onSelect: (() -> Unit)? = null
    var onMoved: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null

    private val ww: Int get() = maxOf(120, (cell?.s?.w ?: 0) + 10)
    private val wh: Int get() = maxOf(110, (cell?.s?.h ?: 0) + 10)
    private val bg = Paint()
    private val bp = Paint().apply { isFilterBitmap = false }
    private val fill = Paint().apply { style = Paint.Style.FILL }
    private val stroke = Paint().apply { style = Paint.Style.STROKE }
    private val line = Paint().apply { color = 0xFFE0443E.toInt(); strokeWidth = 2f }
    private var dx0 = 0f
    private var dy0 = 0f
    private var ox = 0
    private var oy = 0
    private var dragging = false

    init {
        val tile = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val tc = Canvas(tile)
        tc.drawColor(0xFF3A4249.toInt())
        val p = Paint().apply { color = 0xFF454E56.toInt() }
        tc.drawRect(0f, 0f, 16f, 16f, p)
        tc.drawRect(16f, 16f, 32f, 32f, p)
        bg.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * wh / ww.toFloat()).toInt())
    }

    override fun onDraw(c: Canvas) {
        val s = width / ww.toFloat()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        val f = frame ?: return
        val b = bmp
        val ce = cell
        if (b != null && ce != null) {
            c.drawBitmap(b, Rect(ce.sx, ce.sy, ce.sx + ce.s.w, ce.sy + ce.s.h), RectF(0f, 0f, ce.s.w * s, ce.s.h * s), bp)
        }
        if (vis["center"] != false) {
            val cx = f.top["centerx"]
            val cy = f.top["centery"]
            if (cx != null && cy != null) {
                c.drawLine(cx * s, 0f, cx * s, height.toFloat(), line)
                c.drawLine(0f, cy * s, width.toFloat(), cy * s, line)
            }
        }
        for (t in listOf("bdy", "itr")) {
            if (vis[t] == false) continue
            val list = if (t == "itr") f.itrs else f.bdys
            val base = COL[t]!!
            for ((i, o) in list.withIndex()) {
                val x = o.v["x"] ?: continue
                val y = o.v["y"] ?: continue
                val w = o.v["w"] ?: continue
                val h = o.v["h"] ?: continue
                val on = sel?.type == t && sel?.i == i
                fill.color = (base and 0xFFFFFF) or 0x44000000
                stroke.color = if (on) 0xFFFFFFFF.toInt() else base
                stroke.strokeWidth = if (on) 5f else 2f
                c.drawRect(x * s, y * s, (x + w) * s, (y + h) * s, fill)
                c.drawRect(x * s, y * s, (x + w) * s, (y + h) * s, stroke)
            }
        }
        for (t in POINTS) {
            if (vis[t] == false) continue
            val o = f.points[t] ?: continue
            val x = o.v["x"] ?: continue
            val y = o.v["y"] ?: continue
            fill.color = COL[t]!!
            c.drawCircle(x * s, y * s, if (sel?.type == t) 7f * s / 2f else 4f * s / 2f, fill)
        }
    }

    private fun hit(f: Frame, x: Float, y: Float): Sel? {
        for (t in POINTS) {
            if (vis[t] == false) continue
            val o = f.points[t] ?: continue
            val px = o.v["x"] ?: continue
            val py = o.v["y"] ?: continue
            if (hypot(px - x, py - y) <= 6f) return Sel(t, 0)
        }
        for (t in listOf("itr", "bdy")) {
            if (vis[t] == false) continue
            val list = if (t == "itr") f.itrs else f.bdys
            for (i in list.indices.reversed()) {
                val o = list[i]
                val bx = o.v["x"] ?: continue
                val by = o.v["y"] ?: continue
                val bw = o.v["w"] ?: continue
                val bh = o.v["h"] ?: continue
                if (x >= bx && x <= bx + bw && y >= by && y <= by + bh) return Sel(t, i)
            }
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val f = frame ?: return false
        val s = width / ww.toFloat()
        val x = e.x / s
        val y = e.y / s
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                sel = hit(f, x, y)
                onSelect?.invoke()
                val o = f.obj(sel)
                dragging = o != null && o.v.containsKey("x") && o.v.containsKey("y")
                if (dragging && o != null) {
                    dx0 = x; dy0 = y
                    ox = o.v["x"] ?: 0
                    oy = o.v["y"] ?: 0
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val o = f.obj(sel)
                if (o != null) {
                    o.v["x"] = ox + (x - dx0).roundToInt()
                    o.v["y"] = oy + (y - dy0).roundToInt()
                    onMoved?.invoke()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                onDone?.invoke()
            }
        }
        return true
    }
}

/** Panel Design (pengganti FormDesign/TagBox): mengikuti frame di posisi kursor editor, menulis balik ke kode. */
class DesignPanel(
    private val ctx: Context,
    private val sprites: SpriteStore,
    private val ed: EditText,
    private val pickSprite: () -> Unit
) {
    val root = ScrollView(ctx)
    private val canvas = DesignCanvas(ctx)
    private val idx = EditText(ctx)
    private val cap = EditText(ctx)
    private val msg = TextView(ctx)
    private val formBox = LinearLayout(ctx)
    private val fields = HashMap<String, EditText>()
    private val handler = Handler(Looper.getMainLooper())
    private val run = Runnable { load() }
    private var frame: Frame? = null
    private var rs = -1
    private var re = -1
    private var sheets: List<Sheet> = emptyList()
    private var headKey = ""
    private var cell: Cell? = null
    private var applying = false

    fun follow() {
        if (applying || root.visibility != View.VISIBLE) return
        handler.removeCallbacks(run)
        handler.postDelayed(run, 120)
    }

    fun refresh() { if (root.visibility == View.VISIBLE) load() }

    private fun load() {
        val t = ed.text.toString()
        val off = maxOf(0, ed.selectionStart)
        val s = t.lastIndexOf("<frame>", off)
        var e = if (s >= 0) t.indexOf("<frame_end>", s) else -1
        var fr: Frame? = null
        rs = -1; re = -1
        if (s >= 0 && e >= 0) {
            e += 11
            fr = try { FrameParser.read(t.substring(s, e)) } catch (ex: Exception) { null }
            if (fr != null) { rs = s; re = e }
        }
        frame = fr
        val he = t.indexOf("<bmp_end>")
        val hk = t.substring(0, if (he < 0) minOf(4000, t.length) else he + 9)
        if (hk != headKey) { headKey = hk; sheets = Sprites.parseSheets(hk) }
        val pic = fr?.top?.get("pic")
        val c = if (pic != null) Sprites.cellFor(sheets, pic) else null
        cell = c
        if (c != null && !sprites.has(c.s.name)) {
            sprites.request(c.s.name) { canvas.bmp = sprites.peek(c.s.name); canvas.invalidate(); note() }
        }
        canvas.frame = fr
        canvas.cell = c
        canvas.bmp = if (c != null) sprites.peek(c.s.name) else null
        if (fr?.obj(canvas.sel) == null) canvas.sel = null
        idx.setText(fr?.index?.toString() ?: "")
        cap.setText(fr?.caption ?: "")
        buildForm()
        note()
        canvas.requestLayout()
        canvas.invalidate()
    }

    private fun note() {
        val f = frame
        val c = cell
        msg.text = when {
            f == null -> "Letakkan kursor di dalam sebuah frame"
            c == null -> "pic ${f.top["pic"]}: tidak ada di bmp_begin"
            sprites.has(c.s.name) -> "pic ${f.top["pic"]} · ${c.s.name}"
            else -> "Sprite \"${c.s.name}\" belum dimuat. Ketuk Sprite…"
        }
    }

    private fun apply() {
        val f = frame ?: return
        if (rs < 0) return
        val txt = FrameWriter.frameToString(f)
        applying = true
        try {
            ed.text.replace(rs, re, txt)
            re = rs + txt.length
        } catch (ex: Exception) {
        } finally {
            applying = false
        }
    }

    private fun isBool(type: String, key: String) = Schema.fields[type]?.any { it.first == key && it.second == 'b' } == true

    private fun commit(key: String, value: String) {
        val s = canvas.sel ?: return
        val o = frame?.obj(s) ?: return
        val cur = o.v[key]
        if (value.isBlank()) {
            if (cur == null) return
            o.v.remove(key)
        } else {
            val n = value.trim().toIntOrNull() ?: return
            if (n == cur) return
            o.v[key] = if (isBool(s.type, key)) (if (n != 0) 1 else 0) else n
        }
        apply()
        canvas.invalidate()
    }

    private fun label(t: String) = TextView(ctx).apply { text = t; setTextColor(C_DIM); textSize = 12f }

    private fun numField(key: String, value: Int?) = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        setSingleLine()
        textSize = 12f
        setTextColor(C_FG)
        setBackgroundColor(C_BG)
        setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
        imeOptions = EditorInfo.IME_ACTION_DONE
        setText(value?.toString() ?: "")
        setOnFocusChangeListener { _, has -> if (!has) commit(key, text.toString()) }
        setOnEditorActionListener { v, _, _ -> commit(key, v.text.toString()); v.clearFocus(); true }
        fields[key] = this
    }

    private fun buildForm() {
        formBox.removeAllViews()
        fields.clear()
        val s = canvas.sel
        val o = frame?.obj(s)
        if (s == null || o == null) { formBox.addView(label("Ketuk kotak atau titik di kanvas untuk memilih")); return }
        formBox.addView(label(s.type + (if (s.type == "itr" || s.type == "bdy") " #${s.i + 1}" else "")).apply { setTextColor(C_ACC) })
        val items = ArrayList<Pair<String, String>>()
        for ((k, _) in Schema.fields[s.type] ?: emptyList()) items.add(k to k)
        for (p in Schema.pairs[s.type] ?: emptyList()) { items.add(p.first to p.second); items.add("" to p.third) }
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in i until minOf(i + 2, items.size)) {
                val (lab, key) = items[j]
                val cellBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(ctx, 3), dp(ctx, 8), dp(ctx, 3)) }
                cellBox.addView(label(lab), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                cellBox.addView(numField(key, o.v[key]), LinearLayout.LayoutParams(dp(ctx, 64), ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(cellBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            formBox.addView(row)
            i += 2
        }
    }

    private fun add(t: String) {
        val f = frame ?: return
        val s = Sect(t)
        s.v["kind"] = 0; s.v["x"] = 20; s.v["y"] = 20; s.v["w"] = 30; s.v["h"] = 30
        if (t == "itr") s.v["injury"] = 10
        val list = if (t == "itr") f.itrs else f.bdys
        list.add(s)
        canvas.sel = Sel(t, list.size - 1)
        apply(); buildForm(); canvas.invalidate()
    }

    private fun del() {
        val f = frame ?: return
        val s = canvas.sel ?: return
        when (s.type) {
            "itr" -> f.itrs.removeAt(s.i)
            "bdy" -> f.bdys.removeAt(s.i)
            else -> f.points.remove(s.type)
        }
        canvas.sel = null
        apply(); buildForm(); canvas.invalidate()
    }

    private fun gotoFrame(n: Int) {
        val m = Regex("<frame>\\s+$n\\s").find(ed.text) ?: return
        val off = m.range.first + 8
        ed.setSelection(off)
        ed.post {
            val lay = ed.layout
            if (lay != null) ed.scrollTo(0, maxOf(0, lay.getLineTop(lay.getLineForOffset(off)) - 2 * ed.lineHeight))
        }
    }

    private fun smallButton(t: String, click: () -> Unit) = Button(ctx).apply {
        text = t; isAllCaps = false; textSize = 12f
        setPadding(dp(ctx, 4), 0, dp(ctx, 4), 0)
        setOnClickListener { click() }
    }

    init {
        root.setBackgroundColor(C_PANEL)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 12))
        }
        root.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(label("Design  "))
        for (e in listOf(idx, cap)) {
            e.setSingleLine(); e.textSize = 13f; e.setTextColor(C_FG); e.setBackgroundColor(C_BG)
            e.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6)); e.imeOptions = EditorInfo.IME_ACTION_DONE
        }
        idx.inputType = InputType.TYPE_CLASS_NUMBER
        idx.hint = "idx"
        cap.hint = "caption"
        idx.setOnEditorActionListener { v, _, _ -> v.text.toString().trim().toIntOrNull()?.let { gotoFrame(it) }; v.clearFocus(); true }
        cap.setOnEditorActionListener { v, _, _ ->
            val f = frame
            if (f != null) {
                f.caption = v.text.toString().trim().replace(Regex("\\s+"), "_").ifEmpty { "frame" }
                apply()
            }
            v.clearFocus(); true
        }
        head.addView(idx, LinearLayout.LayoutParams(dp(ctx, 70), ViewGroup.LayoutParams.WRAP_CONTENT))
        head.addView(cap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(head)

        val visRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for ((k, l) in listOf("bdy" to "bdy", "itr" to "itr", "wpoint" to "w", "opoint" to "o", "cpoint" to "c", "bpoint" to "b", "center" to "center")) {
            visRow.addView(CheckBox(ctx).apply {
                text = l; isChecked = true; setTextColor(C_FG); textSize = 12f
                setOnCheckedChangeListener { _, on -> canvas.vis[k] = on; canvas.invalidate() }
            })
        }
        col.addView(HorizontalScrollView(ctx).apply { addView(visRow); isHorizontalScrollBarEnabled = false })

        col.addView(canvas, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        msg.setTextColor(C_DIM); msg.textSize = 12f
        col.addView(msg)

        val btns = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = { LinearLayout.LayoutParams(0, dp(ctx, 40), 1f) }
        btns.addView(smallButton("Sprite…") { pickSprite() }, lp())
        btns.addView(smallButton("+itr") { add("itr") }, lp())
        btns.addView(smallButton("+bdy") { add("bdy") }, lp())
        btns.addView(smallButton("Hapus") { del() }, lp())
        btns.addView(smallButton("↻") { load() }, lp())
        col.addView(btns)

        formBox.orientation = LinearLayout.VERTICAL
        formBox.setPadding(0, dp(ctx, 8), 0, 0)
        col.addView(formBox)

        canvas.onSelect = { buildForm() }
        canvas.onMoved = { fields["x"]?.setText(canvas.frame?.obj(canvas.sel)?.v?.get("x")?.toString() ?: ""); fields["y"]?.setText(canvas.frame?.obj(canvas.sel)?.v?.get("y")?.toString() ?: "") }
        canvas.onDone = { apply() }
        note()
    }
}
