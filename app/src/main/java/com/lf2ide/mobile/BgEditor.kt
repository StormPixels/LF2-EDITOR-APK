package com.lf2ide.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.lf2ide.mobile.core.BgDoc
import com.lf2ide.mobile.core.BgInfo
import com.lf2ide.mobile.core.Edit

/** Satu layer pada pratinjau: gambar (bila sudah dimuat), posisi, dan lebar yang tertulis. */
class BgLayerView(val idx: Int, val name: String, val bmp: Bitmap?, val x: Int, val y: Int, val w: Int, val sel: Boolean)

/** Thumbnail gambar utuh (muat di dalam kotak). */
class BmpThumb(ctx: Context) : View(ctx) {
    var bmp: Bitmap? = null
    private val p = Paint().apply { isFilterBitmap = false }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF000000.toInt())
        val b = bmp ?: return
        if (b.width <= 0 || b.height <= 0 || width <= 0 || height <= 0) return
        val sc = minOf(width / b.width.toFloat(), height / b.height.toFloat())
        val w = b.width * sc
        val h = b.height * sc
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        c.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF(l, t, l + w, t + h), p)
    }
}

/**
 * Pratinjau background seperti tampilan di game: jendela kamera 800x450 yang bisa digeser (camX).
 * Urutan layer: yang paling atas di daftar paling depan, jadi digambar paling akhir dari bawah ke atas daftar.
 */
class BgPreview(ctx: Context) : View(ctx) {
    var worldW = 1600
    val viewW = 800
    val viewH = 450
    var camX = 0
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

    private fun sc(): Float = minOf(width / viewW.toFloat(), height / viewH.toFloat())
    private fun ox(): Float = (width - viewW * sc()) / 2f
    private fun oy(): Float = (height - viewH * sc()) / 2f

    private fun boundsOf(l: BgLayerView): RectF {
        val bw = l.bmp?.width ?: maxOf(l.w, 40)
        val bh = l.bmp?.height ?: 40
        return RectF(0f, 0f, bw.toFloat(), bh.toFloat())
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(V_BG)
        val s = sc()
        val ox = ox()
        val oy = oy()
        c.save()
        c.clipRect(ox, oy, ox + viewW * s, oy + viewH * s)
        c.drawColor(Color.BLACK)
        for (l in layers.reversed()) {
            val x = if (l.idx == dragIdx) dragX else l.x
            val y = if (l.idx == dragIdx) dragY else l.y
            val b = boundsOf(l)
            val r = RectF(ox + (x - camX) * s, oy + y * s, ox + (x - camX + b.right) * s, oy + (y + b.bottom) * s)
            val bmp = l.bmp
            p.style = Paint.Style.FILL
            if (bmp != null) {
                p.isFilterBitmap = false
                p.color = Color.WHITE
                c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), r, p)
            } else {
                p.color = 0x3322D3EE
                c.drawRect(r, p)
                p.color = V_DIM
                p.textSize = 11f * d
                c.drawText(l.name, r.left + 4f * d, r.top + 14f * d, p)
            }
        }
        // batas z (perkiraan: nilai z dipetakan ke sumbu vertikal)
        p.style = Paint.Style.STROKE
        p.color = 0xFFFFE600.toInt()
        p.strokeWidth = d
        p.pathEffect = DashPathEffect(floatArrayOf(8f * d, 6f * d), 0f)
        for (z in listOf(z1, z2)) if (z != null) c.drawLine(ox, oy + z * s, ox + viewW * s, oy + z * s, p)
        p.pathEffect = null
        for (l in layers) {
            if (!l.sel) continue
            val x = if (l.idx == dragIdx) dragX else l.x
            val y = if (l.idx == dragIdx) dragY else l.y
            val b = boundsOf(l)
            p.color = V_ACC
            p.strokeWidth = 2f * d
            c.drawRect(ox + (x - camX) * s, oy + y * s, ox + (x - camX + b.right) * s, oy + (y + b.bottom) * s, p)
        }
        c.restore()
        p.style = Paint.Style.STROKE
        p.color = V_LINE
        p.strokeWidth = 2f * d
        c.drawRect(ox, oy, ox + viewW * s, oy + viewH * s, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = sc()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val wx = (e.x - ox()) / s + camX
                val wy = (e.y - oy()) / s
                var hit = -1
                for (l in layers) { // paling depan dulu
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

/** Editor background: daftar layer bergambar, pratinjau berkamera, dan panel field berseksi. */
class BgEditor(
    private val ctx: Context,
    private val sprites: SpriteStore,
    private val ed: EditText
) {
    val root = LinearLayout(ctx)

    private val ui = UiKit(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val loadRun = Runnable { load() }
    private val thumbRun = Runnable { adapter.notifyDataSetChanged(); updatePreview() }
    private val camRun = Runnable { camTick() }
    private var info: BgInfo = BgDoc.parse("")
    private var sel = 0
    private var applying = false
    private var camPlaying = false

    private val listPanel = LinearLayout(ctx)
    private val centerPanel = LinearLayout(ctx)
    private val rightPanel = LinearLayout(ctx)
    private val layerList = ListView(ctx)
    private val preview = BgPreview(ctx)
    private val msg = TextView(ctx)
    private val descTv = TextView(ctx)
    private val form = LinearLayout(ctx)
    private val seek = SeekBar(ctx)
    private var camBtn: android.widget.Button? = null
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
            root.addView(centerPanel, ui.lp(0, MATCH, 4.2f))
            root.addView(rightPanel, ui.lp(0, MATCH, 3.4f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            root.addView(centerPanel, ui.lp(MATCH, 0, 4f))
            root.addView(listPanel, ui.lp(MATCH, 0, 3f))
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
        listPanel.addView(bar, ui.lp(MATCH, WRAP))
        val bar2 = LinearLayout(ctx)
        bar2.addView(ui.btn("▲ Ke depan") { moveLayer(-1) }, ui.lp(0, WRAP, 1f))
        bar2.addView(ui.btn("▼ Ke belakang") { moveLayer(1) }, ui.lp(0, WRAP, 1f))
        listPanel.addView(bar2, ui.lp(MATCH, WRAP))
        listPanel.addView(ui.tv("Atas = paling depan", 11f, V_DIM).apply { setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(2)) })
        layerList.adapter = adapter
        layerList.divider = null
        layerList.setOnItemClickListener { _, _, pos, _ -> sel = pos; load() }
        listPanel.addView(layerList, ui.lp(MATCH, 0, 1f))

        centerPanel.orientation = LinearLayout.VERTICAL
        centerPanel.addView(preview, ui.lp(MATCH, 0, 1f))
        val cam = LinearLayout(ctx)
        cam.gravity = Gravity.CENTER_VERTICAL
        cam.setPadding(ui.dp(6), ui.dp(2), ui.dp(10), ui.dp(2))
        val cb = ui.btn("▶") { toggleCam() }
        camBtn = cb
        cam.addView(cb, ui.lp(ui.dp(48), ui.dp(40)))
        cam.addView(ui.tv("Kamera", 13f, V_FG).apply { setPadding(ui.dp(8), 0, ui.dp(8), 0) })
        seek.max = 0
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                preview.camX = progress
                preview.invalidate()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        cam.addView(seek, ui.lp(0, WRAP, 1f))
        centerPanel.addView(cam, ui.lp(MATCH, WRAP))
        msg.textSize = 11f
        msg.setTextColor(V_DIM)
        msg.setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(6))
        centerPanel.addView(msg, ui.lp(MATCH, WRAP))
        preview.onPick = { i -> sel = i; adapter.notifyDataSetChanged(); buildForm(); updatePreview() }
        preview.onMoved = { i, x, y -> moveLayerTo(i, x, y) }

        rightPanel.orientation = LinearLayout.VERTICAL
        rightPanel.setBackgroundColor(V_PANEL)
        descTv.textSize = 12f
        descTv.setTextColor(V_ACC)
        descTv.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(2))
        rightPanel.addView(descTv, ui.lp(MATCH, WRAP))
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(16))
        val sv = ScrollView(ctx)
        sv.addView(form, FrameLayout.LayoutParams(MATCH, WRAP))
        rightPanel.addView(sv, ui.lp(MATCH, 0, 1f))
    }

    // ---------- kamera ----------
    private fun toggleCam() {
        camPlaying = !camPlaying
        camBtn?.text = if (camPlaying) "■" else "▶"
        handler.removeCallbacks(camRun)
        if (camPlaying) handler.post(camRun)
    }

    private fun camTick() {
        if (!camPlaying) return
        if (!root.isShown || seek.max <= 0) {
            camPlaying = false
            camBtn?.text = "▶"
            return
        }
        seek.progress = if (seek.progress + 5 > seek.max) 0 else seek.progress + 5
        handler.postDelayed(camRun, 16)
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

    private fun bitmapFor(path: String): Bitmap? {
        if (path.isEmpty()) return null
        val b = sprites.peek(path)
        if (b == null && !sprites.has(path) && !sprites.isMissing(path)) {
            sprites.request(path) { handler.removeCallbacks(thumbRun); handler.postDelayed(thumbRun, 80) }
        }
        return b
    }

    private fun updatePreview() {
        preview.worldW = info.int("width") ?: 1600
        seek.max = maxOf(0, preview.worldW - preview.viewW)
        if (preview.camX > seek.max) preview.camX = seek.max
        val z = info.get("zboundary")?.trim()?.split(Regex("\\s+")) ?: emptyList()
        preview.z1 = z.getOrNull(0)?.toIntOrNull()
        preview.z2 = z.getOrNull(1)?.toIntOrNull()
        val views = ArrayList<BgLayerView>()
        for ((i, l) in info.layers.withIndex()) {
            views.add(BgLayerView(i, l.path.substringAfterLast('\\').substringAfterLast('/'), bitmapFor(l.path), l.int("x") ?: 0, l.int("y") ?: 0, l.int("width") ?: 0, i == sel))
        }
        preview.layers = views
        msg.text = "Arena ${preview.worldW} px · ${info.layers.size} layer · jendela kamera ${preview.viewW}×${preview.viewH}. " +
            "Geser layer untuk mengubah x/y. Garis kuning = zboundary (perkiraan). Pengulangan (loop) dan paralaks tidak disimulasikan."
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
        "width" -> "Lebar arena"
        "zboundary" -> "Jalur berjalan"
        "shadow" -> "Gambar bayangan"
        "shadowsize" -> "Ukuran bayangan"
        "small" -> "Gambar kecil"
        else -> k
    }

    private fun headerDesc(k: String): String = when (k) {
        "name" -> "Nama latar yang tampil di game."
        "width" -> "Lebar seluruh arena dalam piksel; menentukan sejauh apa kamera bisa bergeser."
        "zboundary" -> "Batas atas dan bawah area jalan karakter (dua angka: min maks)."
        "shadow" -> "Gambar bayangan yang dipakai di bawah karakter."
        "shadowsize" -> "Lebar dan tinggi bayangan (dua angka)."
        "small" -> "Gambar kecil (thumbnail) latar ini."
        else -> k
    }

    private fun attrLabel(k: String): String = when (k) {
        "transparency" -> "Transparan"
        "width" -> "Lebar"
        "x" -> "X"
        "y" -> "Y"
        else -> k
    }

    private fun attrDesc(k: String): String = when (k) {
        "transparency" -> "0 = opak, 1 = transparan (piksel hitam tidak digambar)."
        "width" -> "Lebar layer saat digambar, dalam piksel."
        "x" -> "Posisi horizontal layer di arena (piksel)."
        "y" -> "Posisi vertikal layer di arena (piksel)."
        "loop" -> "Pengaturan pengulangan layer (tidak disimulasikan di pratinjau)."
        else -> "Pengaturan lanjutan layer (tidak disimulasikan di pratinjau)."
    }

    private fun headerSection(k: String): String = when (k) {
        "name" -> "LATAR"
        "width", "zboundary" -> "ARENA"
        "shadow", "shadowsize" -> "BAYANGAN"
        else -> "LAINNYA"
    }

    private fun buildForm() {
        form.removeAllViews()
        descTv.text = "Ketuk kolom untuk melihat fungsinya"
        val hk = ArrayList<String>()
        hk.addAll(knownHeader)
        for (kv in info.header) if (!hk.contains(kv.key)) hk.add(kv.key)
        for (secName in listOf("LATAR", "ARENA", "BAYANGAN", "LAINNYA")) {
            val ks = hk.filter { headerSection(it) == secName }
            if (ks.isEmpty()) continue
            form.addView(section(secName))
            var i = 0
            while (i < ks.size) {
                val cells = ArrayList<View>()
                for (j in i until minOf(i + 2, ks.size)) {
                    val k = ks[j]
                    val numeric = k == "width" || k == "left" || k == "right"
                    cells.add(ui.field(headerLabel(k), info.get(k) ?: "", if (numeric) 1 else 0,
                        { descTv.text = headerLabel(k) + " (" + k + "): " + headerDesc(k) },
                        { v -> commitHeader(k, v, numeric) }, k))
                }
                form.addView(row(*cells.toTypedArray()))
                i += 2
            }
        }
        val l = info.layers.getOrNull(sel)
        if (l == null) {
            form.addView(ui.tv("Belum ada layer. Ketuk + Layer untuk menambah.", 13f, V_DIM))
            return
        }
        form.addView(section("LAYER ${sel + 1}"))
        form.addView(row(ui.field("Gambar (path)", l.path, 0,
            { descTv.text = "Gambar (path): berkas gambar layer, relatif terhadap folder game." },
            { v -> commitPath(v) }, "path")))
        val keys = ArrayList<String>()
        keys.addAll(knownAttr)
        for (kv in l.attrs) if (!keys.contains(kv.key)) keys.add(kv.key)
        var n = 0
        while (n < keys.size) {
            val cells = ArrayList<View>()
            for (j in n until minOf(n + 2, keys.size)) {
                val k = keys[j]
                cells.add(ui.field(attrLabel(k), l.get(k) ?: "", 1,
                    { descTv.text = attrLabel(k) + " (" + k + "): " + attrDesc(k) },
                    { v -> commitAttr(k, v) }, k))
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
            val row = LinearLayout(ctx)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
            row.setBackgroundColor(if (position == sel) V_SEL else 0)
            val th = BmpThumb(ctx)
            th.bmp = bitmapFor(l.path)
            row.addView(th, ui.lp(ui.dp(64), ui.dp(40)))
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(ui.dp(10), 0, 0, 0)
            val name = l.path.substringAfterLast('\\').substringAfterLast('/')
            col.addView(ui.tv("${position + 1}  $name", 14f, V_FG))
            col.addView(ui.tv("x ${l.int("x") ?: 0}, y ${l.int("y") ?: 0}", 11f, V_DIM))
            row.addView(col, ui.lp(0, WRAP, 1f))
            return row
        }
    }
}
