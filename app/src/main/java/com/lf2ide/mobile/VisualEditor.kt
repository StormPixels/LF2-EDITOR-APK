package com.lf2ide.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.lf2ide.mobile.core.Cell
import com.lf2ide.mobile.core.FieldDef
import com.lf2ide.mobile.core.Frame
import com.lf2ide.mobile.core.FrameGroup
import com.lf2ide.mobile.core.FrameInfo
import com.lf2ide.mobile.core.FrameOps
import com.lf2ide.mobile.core.FrameParser
import com.lf2ide.mobile.core.FrameWriter
import com.lf2ide.mobile.core.HeadField
import com.lf2ide.mobile.core.Labels
import com.lf2ide.mobile.core.Schema
import com.lf2ide.mobile.core.Sect
import com.lf2ide.mobile.core.Sheet
import com.lf2ide.mobile.core.Sprites
import com.lf2ide.mobile.core.Tokenizer
import java.io.File

private const val V_BG = 0xFF0B1220.toInt()
private const val V_PANEL = 0xFF111B2E.toInt()
private const val V_LINE = 0xFF22324D.toInt()
private const val V_FG = 0xFFE2E8F0.toInt()
private const val V_DIM = 0xFF94A3B8.toInt()
private const val V_ACC = 0xFF22D3EE.toInt()
private const val V_SEL = 0xFF14365A.toInt()
private const val V_SEC = 0xFFD4A72C.toInt()
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private val V_POINTS = listOf("wpoint", "opoint", "cpoint", "bpoint")

/** Satu baris pada daftar frame kiri: judul grup (info == null) atau satu frame. */
private class Row(val group: FrameGroup, val info: FrameInfo?, val gid: String)

/** Potongan sprite kecil untuk daftar dan filmstrip. */
private class ThumbView(ctx: Context) : View(ctx) {
    var bmp: Bitmap? = null
    var cell: Cell? = null
    private val p = Paint().apply { isFilterBitmap = false }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        val ce = cell ?: return
        if (ce.s.w <= 0 || ce.s.h <= 0 || width <= 0 || height <= 0) return
        val sc = minOf(width / ce.s.w.toFloat(), height / ce.s.h.toFloat())
        val w = ce.s.w * sc
        val h = ce.s.h * sc
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        c.drawBitmap(b, Rect(ce.sx, ce.sy, ce.sx + ce.s.w, ce.sy + ce.s.h), RectF(l, t, l + w, t + h), p)
    }
}

/**
 * Editor visual (landscape tiga kolom): daftar frame berkelompok, kanvas, panel field, dan filmstrip.
 * Sumber data tetap teks di EditText `ed`; kursor di dalam sebuah frame menentukan frame yang aktif.
 */
class VisualEditor(
    private val ctx: Context,
    private val sprites: SpriteStore,
    private val ed: EditText,
    private val pickSprite: () -> Unit,
    private val openFolder: () -> Unit
) {
    val root = LinearLayout(ctx)
    var folderSet = false

    private val handler = Handler(Looper.getMainLooper())
    private val loadRun = Runnable { load(true) }
    private val thumbRun = Runnable { listAdapter.notifyDataSetChanged(); rebuildStrip() }

    // data
    private var infos: List<FrameInfo> = emptyList()
    private var groups: List<FrameGroup> = emptyList()
    private var cur: FrameInfo? = null
    private var frame: Frame? = null
    private var sheets: List<Sheet> = emptyList()
    private var headKey = ""
    private var applying = false
    private var tab = 0
    private var lastCurIdx = -1
    private val expanded = HashSet<String>()
    private val rows = ArrayList<Row>()
    private val hints = HashMap<String, TextView>()
    private val boxFields = HashMap<String, EditText>()

    // tampilan
    private val canvas = DesignCanvas(ctx)
    private val search = EditText(ctx)
    private val list = ListView(ctx)
    private val listPanel = LinearLayout(ctx)
    private val centerPanel = LinearLayout(ctx)
    private val rightPanel = LinearLayout(ctx)
    private val form = LinearLayout(ctx)
    private val descTv = TextView(ctx)
    private val msgTv = TextView(ctx)
    private val strip = LinearLayout(ctx)
    private val stripScroll = HorizontalScrollView(ctx)
    private val body = LinearLayout(ctx)
    private val bottom = LinearLayout(ctx)
    private val tabBtns = ArrayList<TextView>()
    private var btnPlay: Button? = null
    private var btnSpeed: Button? = null
    private var btnFind: Button? = null
    private var btnZoom: Button? = null
    private val listAdapter = ListAdapter()
    private val actions = FrameActions(ctx, ed) { off -> gotoOffset(off) }

    // pratinjau animasi
    private var playing = false
    private val speeds = floatArrayOf(0.25f, 0.5f, 1f, 2f)
    private var speedI = 2
    private var moveOn = false
    private var soundOn = false
    private var seq: Map<Int, Frame> = emptyMap()
    private var startIdx = 0
    private var curIdx = 0
    private var playAx = 0
    private var playAy = 0
    private var playMinPx = 0
    private var pxMap: Map<Int, Int> = emptyMap()
    private val playRun = Runnable { playTick() }

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(V_BG)
        buildLeft()
        buildCenter()
        buildRight()
        buildBottom()
        root.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(bottom, LinearLayout.LayoutParams(MATCH, WRAP))
        layoutFor(true)

        canvas.panOnEmpty = true
        canvas.vis["center"] = true
        canvas.onTouchStart = { if (playing) { stopPlay(); true } else false }
        canvas.onSelect = { if (tab == 1) buildTab() }
        canvas.onMoved = { syncBoxFields() }
        canvas.onDone = { applyFrame(); if (tab == 1) buildTab() }
    }

    // ---------- API untuk MainActivity ----------
    fun setLandscape(land: Boolean) = layoutFor(land)

    fun refresh() { if (root.isShown && !playing) load(true) }

    fun follow() {
        if (applying || !root.isShown) return
        if (playing) { stopPlay(); return }
        handler.removeCallbacks(loadRun)
        handler.postDelayed(loadRun, 120)
    }

    // ---------- helper tampilan ----------
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun sbtn(label: String, onClick: () -> Unit): Button = Button(ctx).apply {
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

    private fun chip(label: String, on: Boolean, change: (Boolean) -> Unit): TextView {
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

    private fun inputBox(value: String, numeric: Boolean, onFocus: () -> Unit, onCommit: (String) -> Unit): EditText =
        EditText(ctx).apply {
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
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

    // ---------- membangun panel ----------
    private fun buildLeft() {
        listPanel.orientation = LinearLayout.VERTICAL
        listPanel.setBackgroundColor(V_PANEL)
        search.hint = "Cari frame"
        search.setSingleLine()
        search.textSize = 13f
        search.setTextColor(V_FG)
        search.setHintTextColor(V_DIM)
        search.setBackgroundColor(V_BG)
        search.setPadding(dp(8), dp(6), dp(8), dp(6))
        search.doAfterTextChanged { rebuildRows() }
        listPanel.addView(search, lp(MATCH, WRAP).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        val acts = LinearLayout(ctx)
        acts.addView(sbtn("Salin") { actions.copyFrame(cur) }, lp(0, WRAP, 1f))
        acts.addView(sbtn("Hapus") { actions.delete(cur) }, lp(0, WRAP, 1f))
        acts.addView(sbtn("Salin move") { actions.copyMove(cur) }, lp(0, WRAP, 1.4f))
        acts.addView(sbtn("Tempel") { actions.pasteMove(cur) }, lp(0, WRAP, 1f))
        listPanel.addView(acts, lp(MATCH, WRAP))
        list.adapter = listAdapter
        list.divider = null
        list.setOnItemClickListener { _, _, pos, _ -> onRowClick(pos) }
        listPanel.addView(list, lp(MATCH, 0, 1f))
    }

    private fun buildCenter() {
        centerPanel.orientation = LinearLayout.VERTICAL
        val chips = LinearLayout(ctx)
        chips.gravity = Gravity.CENTER_VERTICAL
        chips.setPadding(dp(4), dp(4), dp(4), dp(4))
        fun add(label: String, on: Boolean, keys: List<String>) {
            for (k in keys) canvas.vis[k] = on
            val c = chip(label, on) { v ->
                for (k in keys) canvas.vis[k] = v
                if (keys[0] == "prev") load(false) else canvas.invalidate()
            }
            chips.addView(c, lp(WRAP, WRAP).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        add("tubuh", true, listOf("bdy"))
        add("serangan", true, listOf("itr"))
        add("titik", true, V_POINTS)
        add("sebelumnya", false, listOf("prev"))
        add("cermin", false, listOf("mirror"))
        val z = sbtn("Zoom 1×") {
            val n = if (canvas.zoom >= 4f) 1f else canvas.zoom + 1f
            canvas.zoom = n
            btnZoom?.text = "Zoom ${n.toInt()}×"
            canvas.requestLayout()
        }
        btnZoom = z
        chips.addView(z, lp(WRAP, WRAP).apply { setMargins(dp(6), 0, dp(2), 0) })
        val cs = HorizontalScrollView(ctx)
        cs.isHorizontalScrollBarEnabled = false
        cs.addView(chips)
        centerPanel.addView(cs, lp(MATCH, WRAP))

        val holder = FrameLayout(ctx)
        holder.addView(canvas, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        val hs = HorizontalScrollView(ctx)
        hs.isFillViewport = true
        hs.addView(holder, FrameLayout.LayoutParams(WRAP, MATCH))
        val vs = ScrollView(ctx)
        vs.isFillViewport = true
        vs.addView(hs, FrameLayout.LayoutParams(MATCH, MATCH))
        vs.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l
            val h = b - t
            if (w > 0 && h > 0 && (w != canvas.fitW || h != canvas.fitH)) {
                canvas.fitW = w
                canvas.fitH = h
                canvas.post { canvas.requestLayout() }
            }
        }
        centerPanel.addView(vs, lp(MATCH, 0, 1f))

        msgTv.textSize = 12f
        msgTv.setTextColor(V_DIM)
        msgTv.setPadding(dp(8), dp(2), dp(8), dp(2))
        centerPanel.addView(msgTv, lp(MATCH, WRAP))
    }

    private fun buildRight() {
        rightPanel.orientation = LinearLayout.VERTICAL
        rightPanel.setBackgroundColor(V_PANEL)
        val tabs = LinearLayout(ctx)
        val names = listOf("Frame", "Kotak", "Gambar", "Karakter")
        for ((i, n) in names.withIndex()) {
            val v = TextView(ctx)
            v.text = n
            v.textSize = 13f
            v.gravity = Gravity.CENTER
            v.setOnClickListener { tab = i; paintTabs(); buildTab() }
            tabBtns.add(v)
            tabs.addView(v, lp(0, dp(38), 1f))
        }
        paintTabs()
        rightPanel.addView(tabs, lp(MATCH, WRAP))
        descTv.textSize = 12f
        descTv.setTextColor(V_ACC)
        descTv.setPadding(dp(10), dp(6), dp(10), dp(4))
        rightPanel.addView(descTv, lp(MATCH, WRAP))
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(10), dp(4), dp(10), dp(16))
        val sv = ScrollView(ctx)
        sv.addView(form, FrameLayout.LayoutParams(MATCH, WRAP))
        rightPanel.addView(sv, lp(MATCH, 0, 1f))
    }

    private fun paintTabs() {
        for ((i, v) in tabBtns.withIndex()) {
            v.setTextColor(if (i == tab) V_BG else V_FG)
            v.setBackgroundColor(if (i == tab) V_ACC else V_PANEL)
        }
    }

    private fun buildBottom() {
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.setBackgroundColor(V_PANEL)
        bottom.setPadding(dp(4), dp(4), dp(4), dp(4))
        bottom.addView(sbtn("|◀") { navFrame(-1) }, lp(WRAP, dp(44)))
        val bp = sbtn("Putar") { togglePlay() }
        btnPlay = bp
        bottom.addView(bp, lp(WRAP, dp(44)))
        bottom.addView(sbtn("▶|") { navFrame(1) }, lp(WRAP, dp(44)))
        val bs = sbtn(speedText()) { speedI = (speedI + 1) % speeds.size; btnSpeed?.text = speedText() }
        btnSpeed = bs
        bottom.addView(bs, lp(WRAP, dp(44)))
        val cm = chip("dengan gerak", false) { v -> moveOn = v; if (playing) { stopPlay(); startPlay() } }
        bottom.addView(cm, lp(WRAP, WRAP).apply { setMargins(dp(4), 0, dp(2), 0) })
        val cs = chip("suara", false) { v -> soundOn = v }
        bottom.addView(cs, lp(WRAP, WRAP).apply { setMargins(dp(2), 0, dp(4), 0) })
        val bf = sbtn("Cari") { searchDialog() }
        btnFind = bf
        bottom.addView(bf, lp(WRAP, dp(44)))
        stripScroll.isHorizontalScrollBarEnabled = false
        stripScroll.addView(strip, FrameLayout.LayoutParams(WRAP, MATCH))
        strip.orientation = LinearLayout.HORIZONTAL
        bottom.addView(stripScroll, lp(0, dp(68), 1f))
    }

    private fun layoutFor(land: Boolean) {
        body.removeAllViews()
        if (land) {
            body.orientation = LinearLayout.HORIZONTAL
            body.addView(listPanel, lp(0, MATCH, 2.2f))
            body.addView(centerPanel, lp(0, MATCH, 4.2f))
            body.addView(rightPanel, lp(0, MATCH, 3.4f))
        } else {
            body.orientation = LinearLayout.VERTICAL
            body.addView(centerPanel, lp(MATCH, 0, 5f))
            body.addView(rightPanel, lp(MATCH, 0, 4f))
        }
        btnFind?.visibility = if (land) View.GONE else View.VISIBLE
    }

    // ---------- memuat frame aktif ----------
    private fun parse(t: String, f: FrameInfo): Frame? =
        try { FrameParser.read(t.substring(f.s, f.e)) } catch (ex: Exception) { null }

    private fun load(rebuildForm: Boolean) {
        val t = ed.text.toString()
        infos = FrameOps.infos(t)
        groups = FrameOps.group(infos)
        val off = maxOf(0, ed.selectionStart)
        val c0 = infos.firstOrNull { off >= it.s && off < it.e }
            ?: infos.lastOrNull { it.s <= off }
            ?: infos.firstOrNull()
        cur = c0
        val he = t.indexOf("<bmp_end>")
        val hk = t.substring(0, if (he < 0) minOf(4000, t.length) else he + 9)
        if (hk != headKey) {
            headKey = hk
            sheets = Sprites.parseSheets(hk)
            // muat semua lembar gambar yang disebut di header (jalur dicari dari folder game)
            for (sh in sheets) if (!sprites.has(sh.file)) sprites.request(sh.file) { follow() }
        }
        val fr = if (c0 != null) parse(t, c0) else null
        frame = fr
        val pic = fr?.top?.get("pic")
        val cl = if (pic != null) Sprites.cellFor(sheets, pic) else null
        if (cl != null && !sprites.has(cl.s.file)) sprites.request(cl.s.file) { refresh() }
        canvas.frame = fr
        canvas.cell = cl
        canvas.bmp = if (cl != null) sprites.peek(cl.s.file) else null
        if (fr?.obj(canvas.sel) == null) canvas.sel = null
        loadGhost(c0, fr, t)
        canvas.requestLayout()
        canvas.invalidate()
        msgTv.text = if (c0 == null) "Tidak ada frame — gunakan mode Kode untuk menambah"
        else "Frame ${c0.idx} · ${c0.caption} · ${Labels.groupName(FrameOps.groupKey(c0.caption))}"

        var scroll = false
        if (c0 != null && c0.idx != lastCurIdx) {
            lastCurIdx = c0.idx
            val g = groups.firstOrNull { gr -> gr.frames.any { it.s == c0.s } }
            if (g != null) expanded.add(g.key + "@" + g.frames.first().idx)
            scroll = true
        }
        rebuildRows()
        if (scroll && c0 != null) {
            val pos = rows.indexOfFirst { it.info?.s == c0.s }
            if (pos >= 0) list.post { list.setSelectionFromTop(pos, dp(80)) }
        }
        rebuildStrip()
        if (rebuildForm) buildTab() else updateHints()
    }

    private fun loadGhost(c0: FrameInfo?, fr: Frame?, t: String) {
        canvas.ghost = null
        canvas.ghostCell = null
        if (canvas.vis["prev"] != true || c0 == null || fr == null) return
        val i = infos.indexOfFirst { it.s == c0.s }
        if (i <= 0) return
        val pf = parse(t, infos[i - 1]) ?: return
        val pp = pf.top["pic"] ?: return
        val pc = Sprites.cellFor(sheets, pp) ?: return
        if (!sprites.has(pc.s.file)) sprites.request(pc.s.file) { refresh() }
        canvas.ghost = sprites.peek(pc.s.file)
        canvas.ghostCell = pc
        canvas.ghostDx = (fr.top["centerx"] ?: 0) - (pf.top["centerx"] ?: 0)
        canvas.ghostDy = (fr.top["centery"] ?: 0) - (pf.top["centery"] ?: 0)
    }

    private fun gotoOffset(off: Int) {
        if (playing) stopPlay()
        ed.setSelection(off.coerceIn(0, ed.text.length))
        follow()
    }

    private fun gotoInfo(inf: FrameInfo) = gotoOffset(inf.s + 8)

    private fun navFrame(dir: Int) {
        if (playing) stopPlay()
        val c0 = cur ?: return
        val i = infos.indexOfFirst { it.s == c0.s }
        val t = infos.getOrNull(i + dir) ?: return
        gotoInfo(t)
    }

    // ---------- daftar frame ----------
    private fun rebuildRows() {
        val q = search.text.toString().trim().lowercase()
        rows.clear()
        for (g in groups) {
            val label = Labels.groupName(g.key)
            val gid = g.key + "@" + g.frames.first().idx
            val matches = if (q.isEmpty()) g.frames else g.frames.filter {
                it.idx.toString().startsWith(q) || it.caption.lowercase().contains(q) || label.lowercase().contains(q)
            }
            if (matches.isEmpty()) continue
            rows.add(Row(g, null, gid))
            if (q.isNotEmpty() || expanded.contains(gid)) for (f in matches) rows.add(Row(g, f, gid))
        }
        listAdapter.notifyDataSetChanged()
    }

    private fun onRowClick(pos: Int) {
        val r = rows.getOrNull(pos) ?: return
        val inf = r.info
        if (inf == null) {
            if (!expanded.add(r.gid)) expanded.remove(r.gid)
            rebuildRows()
        } else {
            gotoInfo(inf)
        }
    }

    private fun thumb(pic: Int?): ThumbView {
        val v = ThumbView(ctx)
        val c = if (pic != null) Sprites.cellFor(sheets, pic) else null
        if (c != null) {
            val b = sprites.peek(c.s.file)
            if (b != null) {
                v.bmp = b
                v.cell = c
            } else if (!sprites.has(c.s.file)) {
                sprites.request(c.s.file) { handler.removeCallbacks(thumbRun); handler.postDelayed(thumbRun, 80) }
            }
        }
        return v
    }

    private inner class ListAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val r = rows[position]
            val inf = r.info
            val row = LinearLayout(ctx)
            row.gravity = Gravity.CENTER_VERTICAL
            if (inf == null) {
                row.setPadding(dp(8), dp(10), dp(8), dp(10))
                val open = expanded.contains(r.gid) || search.text.isNotEmpty()
                row.addView(tv(if (open) "▾" else "▸", 14f, V_DIM), lp(dp(18), WRAP))
                row.addView(tv(Labels.groupName(r.group.key), 14f, V_FG), lp(0, WRAP, 1f))
                row.addView(tv(r.group.frames.size.toString(), 12f, V_DIM), lp(WRAP, WRAP))
            } else {
                row.setPadding(dp(22), dp(2), dp(8), dp(2))
                row.setBackgroundColor(if (inf.s == cur?.s) V_SEL else 0)
                row.addView(thumb(inf.pic), lp(dp(30), dp(30)))
                row.addView(tv(inf.idx.toString(), 13f, V_FG).apply { setPadding(dp(8), 0, dp(8), 0) }, lp(0, WRAP, 1f))
                row.addView(tv("waktu ${inf.wait}", 12f, V_DIM), lp(WRAP, WRAP))
            }
            return row
        }
    }

    private fun rebuildStrip() {
        strip.removeAllViews()
        val c0 = cur ?: return
        val g = groups.firstOrNull { gr -> gr.frames.any { it.s == c0.s } } ?: return
        var curView: View? = null
        for (inf in g.frames) {
            val cv = LinearLayout(ctx)
            cv.orientation = LinearLayout.VERTICAL
            cv.gravity = Gravity.CENTER_HORIZONTAL
            cv.setPadding(dp(4), dp(2), dp(4), dp(2))
            cv.setBackgroundColor(if (inf.s == c0.s) V_SEL else V_PANEL)
            cv.addView(thumb(inf.pic), lp(dp(34), dp(34)))
            cv.addView(tv(inf.idx.toString(), 11f, V_FG), lp(WRAP, WRAP))
            cv.addView(tv("waktu ${inf.wait}", 10f, V_DIM), lp(WRAP, WRAP))
            cv.setOnClickListener { gotoInfo(inf) }
            strip.addView(cv, lp(WRAP, MATCH).apply { setMargins(dp(2), 0, dp(2), 0) })
            if (inf.s == c0.s) curView = cv
        }
        val sel = curView
        if (sel != null) stripScroll.post { stripScroll.smoothScrollTo(maxOf(0, sel.left - dp(80)), 0) }
    }

    private fun searchDialog() {
        val all = FrameOps.headers(ed.text.toString())
        if (all.isEmpty()) return
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), 0)
        val input = EditText(ctx)
        input.hint = "nomor atau nama frame"
        input.setSingleLine()
        val lv = ListView(ctx)
        val labels = ArrayList<String>()
        val adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels)
        lv.adapter = adapter
        var shown = all
        fun refill() {
            val q = input.text.toString().trim().lowercase()
            shown = if (q.isEmpty()) all else all.filter { it.idx.toString().startsWith(q) || it.caption.lowercase().contains(q) }
            labels.clear()
            labels.addAll(shown.take(300).map { "${it.idx}   ${it.caption}" })
            adapter.notifyDataSetChanged()
        }
        input.doAfterTextChanged { refill() }
        box.addView(input)
        box.addView(lv, lp(MATCH, dp(280)))
        val dlg = AlertDialog.Builder(ctx).setTitle("Cari frame").setView(box).setNegativeButton("Tutup", null).create()
        lv.setOnItemClickListener { _, _, pos, _ ->
            shown.getOrNull(pos)?.let { gotoOffset(it.pos + 8) }
            dlg.dismiss()
        }
        refill()
        dlg.show()
    }

    // ---------- panel kanan ----------
    private fun buildTab() {
        form.removeAllViews()
        hints.clear()
        boxFields.clear()
        descTv.text = ""
        when (tab) {
            0 -> buildFrameTab()
            1 -> buildBoxTab()
            2 -> buildPicTab()
            else -> buildHeadTab()
        }
    }

    private fun updateHints() {
        val f = frame
        for ((k, v) in hints) {
            val value = f?.top?.get(k)
            v.text = if (k == "state") (value?.let { Labels.STATE[it] } ?: "") else Labels.refHint(k, value, infos)
        }
    }

    private fun buildFrameTab() {
        val c0 = cur
        val f = frame
        if (c0 == null || f == null) {
            form.addView(tv("Tidak ada frame yang bisa dibaca di file ini.", 13f, V_DIM))
            return
        }
        descTv.text = "Ketuk kolom untuk melihat fungsinya"
        form.addView(tv("Frame ${c0.idx}", 18f, V_FG))
        for (sec in Labels.FRAME_SECTIONS) {
            val h = tv(sec.title, 13f, V_SEC)
            h.setPadding(0, dp(10), 0, dp(2))
            form.addView(h)
            var i = 0
            while (i < sec.fields.size) {
                val r = LinearLayout(ctx)
                for (j in i until minOf(i + 2, sec.fields.size)) {
                    r.addView(fieldCell(sec.fields[j], c0, f), lp(0, WRAP, 1f).apply { setMargins(0, 0, dp(6), 0) })
                }
                if (i + 1 >= sec.fields.size) r.addView(View(ctx), lp(0, 1, 1f))
                form.addView(r)
                i += 2
            }
        }
        updateHints()
    }

    private fun fieldCell(def: FieldDef, c0: FrameInfo, f: Frame): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(4), 0, dp(2))
        val top = LinearLayout(ctx)
        top.addView(tv(def.label, 13f, V_FG), lp(0, WRAP, 1f))
        top.addView(tv(def.key, 10f, V_DIM), lp(WRAP, WRAP))
        col.addView(top)
        val value = when {
            def.key == "caption" -> c0.caption
            def.key == "sound" -> f.sound ?: ""
            else -> f.top[def.key]?.toString() ?: ""
        }
        val et = inputBox(value, !def.text,
            { descTv.text = def.label + " (" + def.key + "): " + def.desc },
            { v -> commitField(def, v) })
        col.addView(et, lp(MATCH, WRAP))
        if (def.ref || def.key == "state") {
            val h = tv("", 11f, V_DIM)
            hints[def.key] = h
            col.addView(h)
        }
        return col
    }

    private fun commitField(def: FieldDef, v: String) {
        val t = v.trim()
        when {
            def.key == "caption" -> {
                val cap = t.replace(Regex("\\s+"), "_")
                if (cap.isEmpty()) return
                editRaw { FrameOps.withCaption(it, cap) }
            }
            def.text -> {
                val s = t.replace(Regex("\\s+"), "")
                editRaw { FrameOps.setTop(it, def.key, if (s.isEmpty()) null else s) }
            }
            t.isEmpty() -> editRaw { FrameOps.setTop(it, def.key, null) }
            else -> {
                val n = t.toIntOrNull() ?: return
                editRaw { FrameOps.setTop(it, def.key, n.toString()) }
            }
        }
    }

    /** Ubah teks mentah frame aktif (field yang tak dikenal parser tetap utuh). */
    private fun editRaw(tr: (String) -> String) {
        val c0 = cur ?: return
        val t = ed.text.toString()
        if (c0.e > t.length || !t.startsWith("<frame>", c0.s)) return
        val raw = t.substring(c0.s, c0.e)
        val nr = tr(raw)
        if (nr == raw) return
        applying = true
        try {
            ed.text.replace(c0.s, c0.e, nr)
            ed.setSelection(minOf(c0.s + 8, ed.text.length))
        } catch (ex: Exception) {
        } finally {
            applying = false
        }
        load(false)
    }

    // ---------- tab Kotak ----------
    private fun buildBoxTab() {
        val f = frame
        if (f == null) {
            form.addView(tv("Pilih sebuah frame dulu.", 13f, V_DIM))
            return
        }
        val bar = LinearLayout(ctx)
        bar.addView(sbtn("+ tubuh") { addBox("bdy") }, lp(0, WRAP, 1f))
        bar.addView(sbtn("+ serangan") { addBox("itr") }, lp(0, WRAP, 1f))
        bar.addView(sbtn("Hapus") { deleteSel() }, lp(0, WRAP, 1f))
        form.addView(bar)
        val bar2 = LinearLayout(ctx)
        for (p in V_POINTS) {
            val has = f.points.containsKey(p)
            bar2.addView(sbtn((if (has) "− " else "+ ") + p) { togglePoint(p) }, lp(0, WRAP, 1f))
        }
        form.addView(bar2)
        val s = canvas.sel
        for ((i, b) in f.bdys.withIndex()) form.addView(boxRow("bdy", i, "tubuh #${i + 1}", b, s))
        for ((i, b) in f.itrs.withIndex()) form.addView(boxRow("itr", i, "serangan #${i + 1}", b, s))
        for (p in V_POINTS) f.points[p]?.let { form.addView(boxRow(p, 0, p, it, s)) }
        val o = f.obj(s)
        if (s == null || o == null) {
            descTv.text = "Ketuk kotak atau titik, di daftar atau di kanvas, untuk mengubahnya"
            return
        }
        descTv.text = ""
        val items = ArrayList<Pair<String, String>>()
        for ((k, _) in Schema.fields[s.type] ?: emptyList()) items.add(k to k)
        for (p in Schema.pairs[s.type] ?: emptyList()) { items.add(p.first to p.second); items.add("" to p.third) }
        val h = tv("Ubah " + s.type, 13f, V_SEC)
        h.setPadding(0, dp(10), 0, dp(2))
        form.addView(h)
        var i = 0
        while (i < items.size) {
            val r = LinearLayout(ctx)
            for (j in i until minOf(i + 2, items.size)) {
                val (lab, key) = items[j]
                val cellBox = LinearLayout(ctx)
                cellBox.orientation = LinearLayout.VERTICAL
                cellBox.setPadding(0, dp(3), dp(6), dp(3))
                cellBox.addView(tv(if (lab.isEmpty()) " " else lab, 11f, V_DIM))
                val et = inputBox(o.v[key]?.toString() ?: "", true, { }, { v -> commitBox(key, v) })
                boxFields[key] = et
                cellBox.addView(et, lp(MATCH, WRAP))
                r.addView(cellBox, lp(0, WRAP, 1f))
            }
            form.addView(r)
            i += 2
        }
    }

    private fun boxRow(type: String, i: Int, label: String, o: Sect, sel: com.lf2ide.mobile.Sel?): View {
        val on = sel != null && sel.type == type && sel.i == i
        val parts = ArrayList<String>()
        for (k in listOf("x", "y", "w", "h")) o.v[k]?.let { parts.add("$k $it") }
        val r = tv(label + "   " + parts.joinToString(" "), 13f, if (on) V_BG else V_FG)
        r.setPadding(dp(8), dp(8), dp(8), dp(8))
        r.setBackgroundColor(if (on) V_ACC else V_BG)
        r.setOnClickListener {
            canvas.sel = com.lf2ide.mobile.Sel(type, i)
            canvas.invalidate()
            buildTab()
        }
        val box = LinearLayout(ctx)
        box.setPadding(0, dp(2), 0, dp(2))
        box.addView(r, lp(MATCH, WRAP))
        return box
    }

    private fun syncBoxFields() {
        val o = frame?.obj(canvas.sel) ?: return
        for ((k, et) in boxFields) {
            if (k == "x" || k == "y") et.setText((o.v[k] ?: 0).toString())
        }
    }

    private fun commitBox(key: String, v: String) {
        val o = frame?.obj(canvas.sel) ?: return
        val t = v.trim()
        if (t.isEmpty()) {
            o.v.remove(key)
        } else {
            val n = t.toIntOrNull() ?: return
            o.v[key] = n
        }
        applyFrame()
        canvas.invalidate()
    }

    private fun addBox(t: String) {
        val f = frame ?: return
        val s = Sect(t)
        s.v["kind"] = 0
        s.v["x"] = 20
        s.v["y"] = 20
        s.v["w"] = 30
        s.v["h"] = 30
        if (t == "itr") s.v["injury"] = 10
        val l = if (t == "itr") f.itrs else f.bdys
        l.add(s)
        canvas.sel = com.lf2ide.mobile.Sel(t, l.size - 1)
        applyFrame()
        canvas.invalidate()
        buildTab()
    }

    private fun togglePoint(t: String) {
        val f = frame ?: return
        if (f.points.containsKey(t)) {
            f.points.remove(t)
            if (canvas.sel?.type == t) canvas.sel = null
        } else {
            val s = Sect(t)
            for ((k, _) in Schema.fields[t] ?: emptyList()) s.v[k] = 0
            if (s.v.containsKey("kind")) s.v["kind"] = 1
            s.v["x"] = 20
            s.v["y"] = 20
            f.points[t] = s
            canvas.sel = com.lf2ide.mobile.Sel(t, 0)
        }
        applyFrame()
        canvas.invalidate()
        buildTab()
    }

    private fun deleteSel() {
        val f = frame ?: return
        val s = canvas.sel ?: return
        when (s.type) {
            "itr" -> if (s.i < f.itrs.size) f.itrs.removeAt(s.i)
            "bdy" -> if (s.i < f.bdys.size) f.bdys.removeAt(s.i)
            else -> f.points.remove(s.type)
        }
        canvas.sel = null
        applyFrame()
        canvas.invalidate()
        buildTab()
    }

    /** Tulis ulang frame aktif dari modelnya (dipakai untuk perubahan kotak/titik). */
    private fun applyFrame() {
        val f = frame ?: return
        val c0 = cur ?: return
        val t = ed.text.toString()
        if (c0.e > t.length || !t.startsWith("<frame>", c0.s)) return
        applying = true
        try {
            ed.text.replace(c0.s, c0.e, FrameWriter.frameToString(f))
            ed.setSelection(minOf(c0.s + 8, ed.text.length))
        } catch (ex: Exception) {
        } finally {
            applying = false
        }
        val t2 = ed.text.toString()
        infos = FrameOps.infos(t2)
        groups = FrameOps.group(infos)
        cur = infos.firstOrNull { it.s == c0.s } ?: cur
        rebuildRows()
        rebuildStrip()
    }

    // ---------- tab Gambar ----------
    private fun buildPicTab() {
        val pic = frame?.top?.get("pic")
        form.addView(tv(if (pic != null) "Sprite nomor $pic" else "Frame ini belum punya nomor gambar", 16f, V_FG))
        val cl = if (pic != null) Sprites.cellFor(sheets, pic) else null
        val info = if (cl != null) {
            "Lembar: ${cl.s.name}  (${cl.s.w}×${cl.s.h}, ${cl.s.row}×${cl.s.col})" +
                if (sprites.has(cl.s.file)) "" else "\nGambar belum dimuat"
        } else "Lembar untuk nomor ini tidak ditemukan di header"
        form.addView(tv(info, 13f, V_DIM))
        val bar = LinearLayout(ctx)
        bar.addView(sbtn("Pilih sprite…") { pickSprite() }, lp(0, WRAP, 1f))
        bar.addView(sbtn("Folder game…") { openFolder() }, lp(0, WRAP, 1f))
        form.addView(bar)
        if (!folderSet) form.addView(tv("Pilih folder game agar sprite dimuat otomatis dari jalur di header.", 12f, V_DIM))
        val h = tv("LEMBAR DI HEADER", 13f, V_SEC)
        h.setPadding(0, dp(10), 0, dp(2))
        form.addView(h)
        for (s in sheets) form.addView(tv("${s.start}–${s.end}   ${s.name}   ${s.w}×${s.h}   ${s.row}×${s.col}", 12f, V_FG))
    }

    // ---------- tab Karakter ----------
    private fun buildHeadTab() {
        val hf = FrameOps.headFields(ed.text.toString())
        if (hf.isEmpty()) {
            form.addView(tv("Tidak ada data header di file ini.", 13f, V_DIM))
            return
        }
        descTv.text = "Ketuk kolom lalu ubah nilainya"
        val h = tv("HEADER", 13f, V_SEC)
        h.setPadding(0, dp(4), 0, dp(2))
        form.addView(h)
        for (f in hf) form.addView(headRow(f))
    }

    private fun headRow(h: HeadField): View {
        val r = LinearLayout(ctx)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(3), 0, dp(3))
        val name = Labels.headName(h.key)
        r.addView(tv(name, 12f, V_DIM), lp(0, WRAP, 1f))
        val et = inputBox(h.value, false, { descTv.text = name + " (" + h.key + ")" }, { v -> commitHead(h.key, v) })
        r.addView(et, lp(dp(150), WRAP))
        return r
    }

    private fun commitHead(key: String, v: String) {
        val t = v.trim()
        val f = FrameOps.headFields(ed.text.toString()).firstOrNull { it.key == key } ?: return
        if (f.value == t) return
        applying = true
        try {
            ed.text.replace(f.s, f.e, t)
        } catch (ex: Exception) {
        } finally {
            applying = false
        }
        load(false)
    }

    // ---------- pratinjau animasi ----------
    private fun speedText() = "${speeds[speedI]}×".replace(".0×", "×")

    private fun togglePlay() { if (playing) stopPlay() else startPlay() }

    private fun startPlay() {
        val f = frame
        if (f == null) return
        val m = LinkedHashMap<Int, Frame>()
        for (raw in Tokenizer.getFrames(ed.text.toString())) {
            val fr = try { FrameParser.read(raw) } catch (ex: Exception) { null } ?: continue
            if (!m.containsKey(fr.index)) m[fr.index] = fr
        }
        if (!m.containsKey(f.index)) m[f.index] = f
        seq = m
        startIdx = f.index
        val chain = ArrayList<Frame>()
        val seen = HashSet<Int>()
        var i = startIdx
        while (true) {
            val fr = seq[i] ?: break
            if (!seen.add(i)) break
            chain.add(fr)
            val n = fr.top["next"] ?: break
            if (n >= 999 || (n == 0 && startIdx != 0)) break
            i = n
        }
        playAx = chain.maxOf { it.top["centerx"] ?: 0 }
        playAy = chain.maxOf { it.top["centery"] ?: 0 }
        // perkiraan gerak horizontal (dvx x lama frame) bila "dengan gerak" aktif
        val pm = HashMap<Int, Int>()
        var px = 0
        var minPx = 0
        var maxPx = 0
        if (moveOn) {
            for (fr in chain) {
                pm[fr.index] = px
                val dv = fr.top["dvx"]
                if (dv != null && dv > -500 && dv < 500) px += dv * ((fr.top["wait"] ?: 0) + 1)
                px = px.coerceIn(-300, 300)
                minPx = minOf(minPx, px)
                maxPx = maxOf(maxPx, px)
            }
        }
        pxMap = pm
        playMinPx = minPx
        var mw = 0
        var mh = 0
        for (fr in chain) {
            val pic = fr.top["pic"] ?: continue
            val c = Sprites.cellFor(sheets, pic) ?: continue
            mw = maxOf(mw, playAx - (fr.top["centerx"] ?: 0) + c.s.w)
            mh = maxOf(mh, playAy - (fr.top["centery"] ?: 0) + c.s.h)
            if (!sprites.has(c.s.file)) sprites.request(c.s.file) { canvas.invalidate() }
        }
        canvas.boxW = maxOf(120, mw + (maxPx - minPx) + 10)
        canvas.boxH = maxOf(110, mh + 10)
        canvas.sel = null
        canvas.ghost = null
        canvas.requestLayout()
        playing = true
        btnPlay?.text = "Stop"
        curIdx = startIdx
        playTick()
    }

    private fun stopPlay() {
        if (!playing) return
        playing = false
        handler.removeCallbacks(playRun)
        btnPlay?.text = "Putar"
        canvas.offX = 0
        canvas.offY = 0
        canvas.boxW = null
        canvas.boxH = null
        load(true)
    }

    private fun playTick() {
        if (!playing) return
        if (!root.isShown) { stopPlay(); return }
        val fr = seq[curIdx] ?: seq[startIdx]
        if (fr == null) { stopPlay(); return }
        val pic = fr.top["pic"]
        val c = if (pic != null) Sprites.cellFor(sheets, pic) else null
        canvas.frame = fr
        canvas.cell = c
        canvas.bmp = if (c != null) sprites.peek(c.s.file) else null
        canvas.offX = playAx - (fr.top["centerx"] ?: 0) + ((pxMap[fr.index] ?: 0) - playMinPx)
        canvas.offY = playAy - (fr.top["centery"] ?: 0)
        msgTv.text = "▶ ${fr.index} ${fr.caption} · waktu ${fr.top["wait"] ?: 0} · ${speedText()}"
        canvas.invalidate()
        val snd = fr.sound
        if (soundOn && snd != null) playSound(snd)
        val wait = (fr.top["wait"] ?: 0).coerceIn(0, 999)
        var ms = ((wait + 1) * 1000f / 30f / speeds[speedI]).toLong().coerceAtLeast(16L)
        val n = fr.top["next"]
        if (n == null || n >= 999 || !seq.containsKey(n) || (n == 0 && startIdx != 0)) {
            curIdx = startIdx
            ms += 300L // jeda singkat sebelum mengulang
        } else {
            curIdx = n
        }
        handler.postDelayed(playRun, ms)
    }

    private fun playSound(path: String) {
        val fnd = sprites.finder ?: return
        Thread {
            try {
                val bytes = fnd(path) ?: return@Thread
                val tmp = File(ctx.cacheDir, "snd_" + path.hashCode() + ".wav")
                if (!tmp.exists()) tmp.writeBytes(bytes)
                handler.post {
                    try {
                        val mp = MediaPlayer()
                        mp.setDataSource(tmp.absolutePath)
                        mp.setOnCompletionListener { it.release() }
                        mp.setOnPreparedListener { it.start() }
                        mp.prepareAsync()
                    } catch (ex: Exception) { }
                }
            } catch (ex: Exception) { }
        }.start()
    }
}
