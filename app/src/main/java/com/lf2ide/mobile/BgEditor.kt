package com.lf2ide.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.lf2ide.mobile.core.BgDoc
import com.lf2ide.mobile.core.BgInfo
import com.lf2ide.mobile.core.Edit

/** Satu layer pada pratinjau: gambar (bila sudah dimuat), posisi, dan lebar yang tertulis. */
class BgLayerView(val idx: Int, val name: String, val bmp: Bitmap?, val x: Int, val y: Int, val w: Int, val sel: Boolean)

/** Pratinjau background: layer digambar berurutan (atas daftar = paling belakang); layer bisa digeser. */
class BgPreview(ctx: Context) : View(ctx) {
    var worldW = 1600
    var worldH = 500
    var z1: Int? = null
    var z2: Int? = null
    var layers: List<BgLayerView> = emptyList()
    var onPick: ((Int) -> Unit)? = null
    var onMoved: ((Int, Int, Int) -> Unit)? = null

    private var dragIdx = -1
    private var dragX = 0
    private var dragY = 0
    private var startX = 0f
    private var startY = 0f
    private var baseX = 0
    private var baseY = 0
    private var moved = false
    private val p = Paint()
    private val d = ctx.resources.displayMetrics.density

    private fun sc(): Float = minOf(width / maxOf(1, worldW).toFloat(), height / maxOf(1, worldH).toFloat())

    private fun boundsOf(l: BgLayerView): RectF {
        val bw = l.bmp?.width ?: maxOf(l.w, 40)
        val bh = l.bmp?.height ?: 40
        return RectF(0f, 0f, bw.toFloat(), bh.toFloat())
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(V_BG)
        val s = sc()
        p.style = Paint.Style.STROKE
        p.color = V_LINE
        p.strokeWidth = d
        c.drawRect(0f, 0f, worldW * s, worldH * s, p)
        for (l in layers) {
            val x = if (l.idx == dragIdx) dragX else l.x
            val y = if (l.idx == dragIdx) dragY else l.y
            val b = boundsOf(l)
            val r = RectF(x * s, y * s, (x + b.right) * s, (y + b.bottom) * s)
            val bmp = l.bmp
            if (bmp != null) {
                p.style = Paint.Style.FILL
                p.isFilterBitmap = false
                c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), r, p)
            } else {
                p.style = Paint.Style.FILL
                p.color = 0x3322D3EE
                c.drawRect(r, p)
                p.color = V_DIM
                p.textSize = 11f * d
                c.drawText(l.name, r.left + 4f * d, r.top + 14f * d, p)
            }
            if (l.sel) {
                p.style = Paint.Style.STROKE
                p.color = 0xFFFFE600.toInt()
                p.strokeWidth = 2f * d
                c.drawRect(r, p)
            }
        }
        // batas z (perkiraan: nilai z dipetakan ke sumbu vertikal)
        p.style = Paint.Style.STROKE
        p.color = 0xFF22D3EE.toInt()
        p.strokeWidth = d
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f * d, 6f * d), 0f)
        for (z in listOf(z1, z2)) if (z != null) c.drawLine(0f, z * s, worldW * s, z * s, p)
        p.pathEffect = null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = sc()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val wx = e.x / s
                val wy = e.y / s
                var hit = -1
                for (l in layers.reversed()) {
                    val b = boundsOf(l)
                    if (wx >= l.x && wx <= l.x + b.right && wy >= l.y && wy <= l.y + b.bottom) { hit = l.idx; break }
                }
                if (hit < 0) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                val l = layers.first { it.idx == hit }
                dragIdx = hit
                baseX = l.x
                baseY = l.y
                dragX = l.x
                dragY = l.y
                startX = e.x
                startY = e.y
                moved = false
                onPick?.invoke(hit)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragIdx >= 0) {
                    dragX = baseX + ((e.x - startX) / s).toInt()
                    dragY = baseY + ((e.y - startY) / s).toInt()
                    moved = true
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val i = dragIdx
                dragIdx = -1
                if (i >= 0 && moved && e.actionMasked == MotionEvent.ACTION_UP) onMoved?.invoke(i, dragX, dragY)
                invalidate()
                return true
            }
        }
        return true
    }
}

/** Editor background: daftar layer, pratinjau, dan panel field (header dan layer terpilih). */
class BgEditor(
    private val ctx: Context,
    private val sprites: SpriteStore,
    private val ed: EditText
) {
    val root = LinearLayout(ctx)

    private val ui = UiKit(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val loadRun = Runnable { load() }
    private var info: BgInfo = BgDoc.parse("")
    private var sel = 0
    private var applying = false

    private val listPanel = LinearLayout(ctx)
    private val centerPanel = LinearLayout(ctx)
    private val rightPanel = LinearLayout(ctx)
    private val layerList = ListView(ctx)
    private val preview = BgPreview(ctx)
    private val msg = TextView(ctx)
    private val form = LinearLayout(ctx)
    private val adapter = LayerAdapter()

    private val knownHeader = listOf("name", "width", "zboundary", "shadow", "shadowsize")
    private val knownAttr = listOf("transparency", "width", "x", "y", "loop", "cc", "c1", "c2")

    init {
        root.setBackgroundColor(V_BG)
        buildPanels()
        layoutFor(true)
    }

    fun setLandscape(land: Boolean) = layoutFor(land)

    fun refresh() { if (root.isShown) load() }

    fun follow() {
        if (applying || !root.isShown) return
        handler.removeCallbacks(loadRun)
        handler.postDelayed(loadRun, 150)
    }

    private fun layoutFor(land: Boolean) {
        root.removeAllViews()
        if (land) {
            root.orientation = LinearLayout.HORIZONTAL
            root.addView(listPanel, ui.lp(0, MATCH, 2.4f))
            root.addView(centerPanel, ui.lp(0, MATCH, 4f))
            root.addView(rightPanel, ui.lp(0, MATCH, 3.6f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            root.addView(listPanel, ui.lp(MATCH, 0, 3f))
            root.addView(centerPanel, ui.lp(MATCH, 0, 3f))
            root.addView(rightPanel, ui.lp(MATCH, 0, 5f))
        }
    }

    private fun buildPanels() {
        listPanel.orientation = LinearLayout.VERTICAL
        listPanel.setBackgroundColor(V_PANEL)
        val bar = LinearLayout(ctx)
        bar.addView(ui.btn("+ Layer") { addLayer() }, ui.lp(0, WRAP, 1.2f))
        bar.addView(ui.btn("Salin") { copyLayer() }, ui.lp(0, WRAP, 1f))
        bar.addView(ui.btn("Hapus") { deleteLayer() }, ui.lp(0, WRAP, 1f))
        bar.addView(ui.btn("▲") { moveLayer(-1) }, ui.lp(0, WRAP, 0.7f))
        bar.addView(ui.btn("▼") { moveLayer(1) }, ui.lp(0, WRAP, 0.7f))
        listPanel.addView(bar, ui.lp(MATCH, WRAP))
        listPanel.addView(ui.tv("Atas = paling belakang", 11f, V_DIM).apply { setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(2)) })
        layerList.adapter = adapter
        layerList.divider = null
        layerList.setOnItemClickListener { _, _, pos, _ -> sel = pos; load() }
        listPanel.addView(layerList, ui.lp(MATCH, 0, 1f))

        centerPanel.orientation = LinearLayout.VERTICAL
        centerPanel.addView(preview, ui.lp(MATCH, 0, 1f))
        msg.textSize = 12f
        msg.setTextColor(V_DIM)
        msg.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(6))
        centerPanel.addView(msg, ui.lp(MATCH, WRAP))
        preview.onPick = { i -> sel = i; adapter.notifyDataSetChanged(); buildForm(); updatePreview() }
        preview.onMoved = { i, x, y -> moveLayerTo(i, x, y) }

        rightPanel.orientation = LinearLayout.VERTICAL
        rightPanel.setBackgroundColor(V_PANEL)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(16))
        val sv = ScrollView(ctx)
        sv.addView(form, FrameLayout.LayoutParams(MATCH, WRAP))
        rightPanel.addView(sv, ui.lp(MATCH, 0, 1f))
    }

    // ---------- memuat dan menulis ----------
    private fun load() {
        info = BgDoc.parse(ed.text.toString())
        if (sel >= info.layers.size) sel = info.layers.size - 1
        if (sel < 0) sel = 0
        adapter.notifyDataSetChanged()
        updatePreview()
        buildForm()
    }

    private fun apply(edit: Edit?) {
        if (edit == null) return
        val t = ed.text.toString()
        if (edit.s < 0 || edit.e > t.length || edit.s > edit.e) return
        if (t.substring(edit.s, edit.e) == edit.text) return
        applying = true
        try {
            ed.text.replace(edit.s, edit.e, edit.text)
        } catch (ex: Exception) {
        } finally {
            applying = false
        }
        load()
    }

    private fun updatePreview() {
        preview.worldW = info.int("width") ?: 1600
        val z = info.get("zboundary")?.trim()?.split(Regex("\\s+")) ?: emptyList()
        preview.z1 = z.getOrNull(0)?.toIntOrNull()
        preview.z2 = z.getOrNull(1)?.toIntOrNull()
        val views = ArrayList<BgLayerView>()
        for ((i, l) in info.layers.withIndex()) {
            var bmp: Bitmap? = null
            if (l.path.isNotEmpty()) {
                bmp = sprites.peek(l.path)
                if (bmp == null && !sprites.has(l.path) && !sprites.isMissing(l.path)) sprites.request(l.path) { preview.post { updatePreview() } }
            }
            views.add(BgLayerView(i, l.path.substringAfterLast('\\').substringAfterLast('/'), bmp, l.int("x") ?: 0, l.int("y") ?: 0, l.int("width") ?: 0, i == sel))
        }
        preview.layers = views
        msg.text = "Lebar latar ${preview.worldW} · ${info.layers.size} layer. Geser layer di pratinjau untuk mengubah x dan y; garis putus-putus = zboundary (perkiraan)."
        preview.invalidate()
    }

    // ---------- operasi layer ----------
    private fun addLayer() {
        sel = info.layers.size
        apply(BgDoc.addLayer(ed.text.toString(), info))
    }

    private fun copyLayer() {
        val l = info.layers.getOrNull(sel) ?: return
        sel += 1
        apply(BgDoc.copyLayer(ed.text.toString(), l))
    }

    private fun deleteLayer() {
        val l = info.layers.getOrNull(sel) ?: return
        AlertDialog.Builder(ctx)
            .setMessage("Hapus layer ${sel + 1} (${l.path})?")
            .setPositiveButton("Hapus") { _, _ ->
                val cur = info.layers.getOrNull(sel)
                if (cur != null) {
                    val e = BgDoc.deleteLayer(ed.text.toString(), cur)
                    sel = maxOf(0, sel - 1)
                    apply(e)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun moveLayer(dir: Int) {
        val e = BgDoc.moveLayer(ed.text.toString(), info, sel, dir) ?: return
        sel += dir
        apply(e)
    }

    private fun moveLayerTo(i: Int, x: Int, y: Int) {
        val l = info.layers.getOrNull(i) ?: return
        apply(BgDoc.setLayerAttr(ed.text.toString(), l, "x", x.toString()))
        val l2 = info.layers.getOrNull(i) ?: return
        apply(BgDoc.setLayerAttr(ed.text.toString(), l2, "y", y.toString()))
    }

    private fun commitHeader(key: String, v: String, numeric: Boolean) {
        val s = v.trim()
        if (s.isEmpty()) return
        if (numeric && s.toIntOrNull() == null) return
        apply(BgDoc.setHeader(ed.text.toString(), info, key, s))
    }

    private fun commitAttr(key: String, v: String) {
        val l = info.layers.getOrNull(sel) ?: return
        val s = v.trim()
        if (s.isNotEmpty() && s.toIntOrNull() == null) return
        apply(BgDoc.setLayerAttr(ed.text.toString(), l, key, if (s.isEmpty()) null else s))
    }

    private fun commitPath(v: String) {
        val l = info.layers.getOrNull(sel) ?: return
        val s = v.trim()
        if (s.isEmpty()) return
        apply(BgDoc.setLayerPath(l, s))
    }

    // ---------- panel kanan ----------
    private fun section(t: String): TextView {
        val h = ui.tv(t, 13f, V_SEC)
        h.setPadding(0, ui.dp(10), 0, ui.dp(2))
        return h
    }

    private fun row(vararg cols: View): LinearLayout {
        val r = LinearLayout(ctx)
        for (c in cols) r.addView(c, ui.lp(0, WRAP, 1f))
        if (cols.size == 1) r.addView(View(ctx), ui.lp(0, 1, 1f))
        return r
    }

    private fun headerLabel(k: String): String = when (k) {
        "name" -> "Nama"
        "width" -> "Lebar latar"
        "zboundary" -> "Batas z (min maks)"
        "shadow" -> "Gambar bayangan"
        "shadowsize" -> "Ukuran bayangan"
        else -> k
    }

    private fun attrLabel(k: String): String = when (k) {
        "transparency" -> "Transparan (0/1)"
        "width" -> "Lebar"
        "x" -> "X"
        "y" -> "Y"
        else -> k
    }

    private fun buildForm() {
        form.removeAllViews()
        form.addView(section("LATAR"))
        val hk = ArrayList<String>()
        hk.addAll(knownHeader)
        for (kv in info.header) if (!hk.contains(kv.key)) hk.add(kv.key)
        var i = 0
        while (i < hk.size) {
            val cells = ArrayList<View>()
            for (j in i until minOf(i + 2, hk.size)) {
                val k = hk[j]
                val numeric = k == "width" || k == "left" || k == "right"
                cells.add(ui.field(headerLabel(k), info.get(k) ?: "", if (numeric) 1 else 0, { }, { v -> commitHeader(k, v, numeric) }))
            }
            form.addView(row(*cells.toTypedArray()))
            i += 2
        }
        val l = info.layers.getOrNull(sel)
        if (l == null) {
            form.addView(ui.tv("Belum ada layer. Ketuk + Layer untuk menambah.", 13f, V_DIM))
            return
        }
        form.addView(section("LAYER ${sel + 1}"))
        form.addView(row(ui.field("Gambar (path)", l.path, 0, { }, { v -> commitPath(v) })))
        val keys = ArrayList<String>()
        keys.addAll(knownAttr)
        for (kv in l.attrs) if (!keys.contains(kv.key)) keys.add(kv.key)
        var n = 0
        while (n < keys.size) {
            val cells = ArrayList<View>()
            for (j in n until minOf(n + 2, keys.size)) {
                val k = keys[j]
                cells.add(ui.field(attrLabel(k), l.get(k) ?: "", 1, { }, { v -> commitAttr(k, v) }))
            }
            form.addView(row(*cells.toTypedArray()))
            n += 2
        }
        form.addView(ui.tv("Kosongkan isian untuk menghapus field dari layer.", 11f, V_DIM))
    }

    private inner class LayerAdapter : BaseAdapter() {
        override fun getCount(): Int = info.layers.size
        override fun getItem(position: Int): Any = info.layers[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val l = info.layers[position]
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
            col.setBackgroundColor(if (position == sel) V_SEL else 0)
            col.addView(ui.tv("Layer ${position + 1}", 14f, V_FG))
            val name = l.path.substringAfterLast('\\').substringAfterLast('/')
            col.addView(ui.tv("$name · x ${l.int("x") ?: 0}, y ${l.int("y") ?: 0}", 12f, V_DIM))
            return col
        }
    }
}
