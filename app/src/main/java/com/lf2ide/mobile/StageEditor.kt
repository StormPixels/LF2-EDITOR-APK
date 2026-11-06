package com.lf2ide.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.lf2ide.mobile.core.Edit
import com.lf2ide.mobile.core.StageDoc
import com.lf2ide.mobile.core.StageInfo

/** Penanda musuh pada peta phase; x null = posisi acak. */
class StageMarker(val idx: Int, val x: Int?, val label: String, val sel: Boolean)

/** Peta satu phase: batang 0..bound, penanda musuh ber-x yang bisa digeser. */
class StageMap(ctx: Context) : View(ctx) {
    var bound = 1500
    var markers: List<StageMarker> = emptyList()
    var onPick: ((Int) -> Unit)? = null
    var onMoved: ((Int, Int) -> Unit)? = null

    private var dragIdx = -1
    private var dragX = 0
    private var moved = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    private fun lo(): Int = minOf(0, markers.mapNotNull { it.x }.minOrNull() ?: 0)
    private fun hi(): Int = maxOf(bound, markers.mapNotNull { it.x }.maxOrNull() ?: 0)
    private fun pad() = 24f * d
    private fun px(x: Int): Float = pad() + (x - lo()) / maxOf(1, hi() - lo()).toFloat() * (width - 2 * pad())
    private fun wx(v: Float): Int = (lo() + (v - pad()) / (width - 2 * pad()) * (hi() - lo())).toInt()
    private fun xOf(m: StageMarker): Int? = if (m.idx == dragIdx) dragX else m.x

    override fun onDraw(c: Canvas) {
        c.drawColor(V_BG)
        val barTop = 56f * d
        val barH = 22f * d
        p.style = Paint.Style.FILL
        p.color = V_PANEL
        c.drawRect(px(0), barTop, px(bound), barTop + barH, p)
        p.style = Paint.Style.STROKE
        p.color = V_ACC
        p.strokeWidth = 2f * d
        c.drawRect(px(0), barTop, px(bound), barTop + barH, p)
        // skala
        p.style = Paint.Style.FILL
        p.textSize = 11f * d
        p.color = V_DIM
        val span = hi() - lo()
        var step = 250
        while (span / step > 10) step *= 2
        var v = (lo() / step) * step
        while (v <= hi()) {
            if (v >= lo()) {
                c.drawRect(px(v) - d, barTop + barH, px(v) + d, barTop + barH + 6f * d, p)
                c.drawText(v.toString(), px(v) - 12f * d, barTop + barH + 20f * d, p)
            }
            v += step
        }
        p.color = V_FG
        c.drawText("bound $bound", px(0), barTop - 30f * d, p)
        // penanda musuh ber-x
        var lane = 0
        for (m in markers) {
            val x = xOf(m) ?: continue
            val cx = px(x)
            p.color = if (m.sel) 0xFFFFFFFF.toInt() else V_ACC
            p.strokeWidth = (if (m.sel) 3f else 2f) * d
            p.style = Paint.Style.STROKE
            c.drawLine(cx, barTop - 6f * d, cx, barTop + barH + 6f * d, p)
            p.style = Paint.Style.FILL
            c.drawCircle(cx, barTop - 10f * d, 6f * d, p)
            p.color = if (m.sel) 0xFFFFFFFF.toInt() else V_FG
            c.drawText(m.label, cx - 8f * d, barTop - 18f * d - (lane % 2) * 13f * d, p)
            lane++
        }
        // musuh tanpa x
        val free = markers.filter { it.x == null }
        p.color = V_DIM
        val y0 = barTop + barH + 54f * d
        c.drawText(if (free.isEmpty()) "Semua musuh punya posisi x" else "Tanpa x (muncul acak): " + free.joinToString(", ") { it.label }, pad(), y0, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                var best = -1
                var bd = 28f * d
                for (m in markers) {
                    val x = m.x ?: continue
                    val dist = Math.abs(px(x) - e.x)
                    if (dist < bd) { bd = dist; best = m.idx }
                }
                if (best < 0) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                dragIdx = best
                dragX = markers.first { it.idx == best }.x ?: 0
                moved = false
                onPick?.invoke(best)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragIdx >= 0) {
                    dragX = wx(e.x)
                    moved = true
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val i = dragIdx
                dragIdx = -1
                if (i >= 0 && moved && e.actionMasked == MotionEvent.ACTION_UP) onMoved?.invoke(i, dragX)
                invalidate()
                return true
            }
        }
        return true
    }
}

/** Editor stage: daftar phase, peta posisi musuh, dan panel field (bound, musik, musuh). */
class StageEditor(
    private val ctx: Context,
    private val ed: EditText,
    private val nameOf: (Int) -> String?
) {
    val root = LinearLayout(ctx)

    private val ui = UiKit(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val loadRun = Runnable { load() }
    private var info: StageInfo = StageDoc.parse("")
    private var ph = 0
    private var en = -1
    private var applying = false

    private val listPanel = LinearLayout(ctx)
    private val centerPanel = LinearLayout(ctx)
    private val rightPanel = LinearLayout(ctx)
    private val phaseList = ListView(ctx)
    private val map = StageMap(ctx)
    private val mapMsg = TextView(ctx)
    private val form = LinearLayout(ctx)
    private val adapter = PhaseAdapter()

    init {
        root.orientation = LinearLayout.HORIZONTAL
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
        bar.addView(ui.btn("+ Phase") { addPhase() }, ui.lp(0, WRAP, 1.2f))
        bar.addView(ui.btn("Salin") { copyPhase() }, ui.lp(0, WRAP, 1f))
        bar.addView(ui.btn("Hapus") { deletePhase() }, ui.lp(0, WRAP, 1f))
        listPanel.addView(bar, ui.lp(MATCH, WRAP))
        val bar2 = LinearLayout(ctx)
        bar2.addView(ui.btn("▲ Naik") { movePhase(-1) }, ui.lp(0, WRAP, 1f))
        bar2.addView(ui.btn("▼ Turun") { movePhase(1) }, ui.lp(0, WRAP, 1f))
        listPanel.addView(bar2, ui.lp(MATCH, WRAP))
        phaseList.adapter = adapter
        phaseList.divider = null
        phaseList.setOnItemClickListener { _, _, pos, _ -> ph = pos; en = -1; load() }
        listPanel.addView(phaseList, ui.lp(MATCH, 0, 1f))

        centerPanel.orientation = LinearLayout.VERTICAL
        centerPanel.addView(map, ui.lp(MATCH, 0, 1f))
        mapMsg.textSize = 12f
        mapMsg.setTextColor(V_DIM)
        mapMsg.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(6))
        centerPanel.addView(mapMsg, ui.lp(MATCH, WRAP))
        map.onPick = { i -> en = i; buildForm(); updateMap() }
        map.onMoved = { i, x ->
            val e = info.phases.getOrNull(ph)?.enemies?.getOrNull(i)
            if (e != null) apply(StageDoc.setEnemy(e, "x", x.toString()))
        }

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
        info = StageDoc.parse(ed.text.toString())
        if (ph >= info.phases.size) ph = info.phases.size - 1
        if (ph < 0) ph = 0
        val p = info.phases.getOrNull(ph)
        if (en >= (p?.enemies?.size ?: 0)) en = -1
        adapter.notifyDataSetChanged()
        updateMap()
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

    private fun updateMap() {
        val p = info.phases.getOrNull(ph)
        if (p == null) {
            map.bound = 1500
            map.markers = emptyList()
            mapMsg.text = "Belum ada phase."
        } else {
            map.bound = p.bound ?: 1500
            map.markers = p.enemies.mapIndexed { i, e -> StageMarker(i, e.x, enemyLabel(e.id), i == en) }
            mapMsg.text = "Phase ${ph + 1} · bound ${p.bound ?: "—"} · ${p.enemies.size} musuh. Seret penanda untuk mengubah x; musuh tanpa x muncul acak."
        }
        map.invalidate()
    }

    private fun enemyLabel(id: Int?): String = if (id == null) "?" else (nameOf(id) ?: id.toString())

    // ---------- operasi phase ----------
    private fun addPhase() {
        ph = info.phases.size
        en = -1
        apply(StageDoc.addPhase(ed.text.toString()))
    }

    private fun copyPhase() {
        val p = info.phases.getOrNull(ph) ?: return
        ph += 1
        en = -1
        apply(StageDoc.copyPhase(ed.text.toString(), p))
    }

    private fun deletePhase() {
        val p = info.phases.getOrNull(ph) ?: return
        AlertDialog.Builder(ctx)
            .setMessage("Hapus phase ${ph + 1} beserta ${p.enemies.size} musuhnya?")
            .setPositiveButton("Hapus") { _, _ ->
                val cur = info.phases.getOrNull(ph)
                if (cur != null) {
                    val e = StageDoc.deletePhase(ed.text.toString(), cur)
                    en = -1
                    ph = maxOf(0, ph - 1)
                    apply(e)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun movePhase(dir: Int) {
        val e = StageDoc.movePhase(ed.text.toString(), info, ph, dir) ?: return
        ph += dir
        apply(e)
    }

    // ---------- operasi musuh ----------
    private fun addEnemy() {
        val p = info.phases.getOrNull(ph) ?: return
        en = p.enemies.size
        apply(StageDoc.addEnemy(ed.text.toString(), p))
    }

    private fun copyEnemy() {
        val e = info.phases.getOrNull(ph)?.enemies?.getOrNull(en) ?: return
        en += 1
        apply(StageDoc.copyEnemy(e))
    }

    private fun deleteEnemy() {
        val e = info.phases.getOrNull(ph)?.enemies?.getOrNull(en) ?: return
        val edit = StageDoc.deleteEnemy(ed.text.toString(), e)
        en = -1
        apply(edit)
    }

    private fun commitPhase(key: String, v: String, numeric: Boolean) {
        val p = info.phases.getOrNull(ph) ?: return
        val s = v.trim()
        if (numeric && s.isNotEmpty() && s.toIntOrNull() == null) return
        apply(StageDoc.setPhaseField(ed.text.toString(), p, key, if (s.isEmpty()) null else s))
    }

    private fun commitEnemy(key: String, v: String, kind: Int) {
        val e = info.phases.getOrNull(ph)?.enemies?.getOrNull(en) ?: return
        val s = v.trim()
        if (key == "id" && s.isEmpty()) return
        if (s.isNotEmpty() && kind == 1 && s.toIntOrNull() == null) return
        if (s.isNotEmpty() && kind == 2 && s.toDoubleOrNull() == null) return
        apply(StageDoc.setEnemy(e, key, if (s.isEmpty()) null else s))
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

    private fun buildForm() {
        form.removeAllViews()
        form.addView(section("STAGE"))
        form.addView(row(ui.field("ID stage", info.id?.toString() ?: "", 1, { }, { v ->
            val n = v.trim().toIntOrNull()
            if (n != null) apply(StageDoc.setStageId(ed.text.toString(), n))
        })))
        val p = info.phases.getOrNull(ph)
        if (p == null) {
            form.addView(ui.tv("Belum ada phase. Ketuk + Phase untuk menambah.", 13f, V_DIM))
            return
        }
        form.addView(section("PHASE ${ph + 1}"))
        form.addView(row(
            ui.field("Bound (batas lebar)", p.bound?.toString() ?: "", 1, { }, { v -> commitPhase("bound", v, true) }),
            ui.field("Musik", p.music ?: "", 0, { }, { v -> commitPhase("music", v, false) })
        ))
        form.addView(section("MUSUH (${p.enemies.size})"))
        val bar = LinearLayout(ctx)
        bar.addView(ui.btn("+ Musuh") { addEnemy() }, ui.lp(0, WRAP, 1f))
        bar.addView(ui.btn("Salin") { copyEnemy() }, ui.lp(0, WRAP, 1f))
        bar.addView(ui.btn("Hapus") { deleteEnemy() }, ui.lp(0, WRAP, 1f))
        form.addView(bar)
        for ((i, e) in p.enemies.withIndex()) {
            val sel = i == en
            val parts = ArrayList<String>()
            parts.add(enemyLabel(e.id))
            e.get("hp")?.let { parts.add("hp $it") }
            e.get("times")?.let { parts.add("×$it") }
            e.get("ratio")?.let { parts.add("ratio $it") }
            e.get("x")?.let { parts.add("x $it") }
            if (e.boss) parts.add("[boss]")
            if (e.soldier) parts.add("[soldier]")
            val r = ui.tv(parts.joinToString("  "), 13f, if (sel) V_BG else V_FG)
            r.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            r.setBackgroundColor(if (sel) V_ACC else V_BG)
            r.setOnClickListener { en = i; updateMap(); buildForm() }
            val box = LinearLayout(ctx)
            box.setPadding(0, ui.dp(2), 0, ui.dp(2))
            box.addView(r, ui.lp(MATCH, WRAP))
            form.addView(box)
        }
        val e = p.enemies.getOrNull(en) ?: return
        form.addView(section("MUSUH TERPILIH"))
        form.addView(row(
            ui.field("ID objek", e.get("id") ?: "", 1, { }, { v -> commitEnemy("id", v, 1) }),
            ui.field("Posisi x", e.get("x") ?: "", 1, { }, { v -> commitEnemy("x", v, 1) })
        ))
        val nm = e.id?.let { nameOf(it) }
        if (nm != null) form.addView(ui.tv("→ $nm", 11f, V_DIM))
        form.addView(row(
            ui.field("HP", e.get("hp") ?: "", 1, { }, { v -> commitEnemy("hp", v, 1) }),
            ui.field("Jumlah (times)", e.get("times") ?: "", 1, { }, { v -> commitEnemy("times", v, 1) })
        ))
        form.addView(row(ui.field("Rasio (ratio)", e.get("ratio") ?: "", 2, { }, { v -> commitEnemy("ratio", v, 2) })))
        val tags = LinearLayout(ctx)
        tags.setPadding(0, ui.dp(6), 0, 0)
        tags.addView(ui.chip("boss", e.boss) { on -> apply(StageDoc.setEnemyTag(currentEnemy() ?: e, "<boss>", on)) })
        tags.addView(View(ctx), ui.lp(ui.dp(8), 1))
        tags.addView(ui.chip("soldier", e.soldier) { on -> apply(StageDoc.setEnemyTag(currentEnemy() ?: e, "<soldier>", on)) })
        form.addView(tags)
    }

    private fun currentEnemy() = info.phases.getOrNull(ph)?.enemies?.getOrNull(en)

    private inner class PhaseAdapter : BaseAdapter() {
        override fun getCount(): Int = info.phases.size
        override fun getItem(position: Int): Any = info.phases[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val p = info.phases[position]
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
            col.setBackgroundColor(if (position == ph) V_SEL else 0)
            col.addView(ui.tv("Phase ${position + 1}", 14f, V_FG))
            col.addView(ui.tv("bound ${p.bound ?: "—"} · ${p.enemies.size} musuh", 12f, V_DIM))
            if (p.comment.isNotEmpty()) col.addView(ui.tv(p.comment, 11f, V_DIM))
            col.gravity = Gravity.CENTER_VERTICAL
            return col
        }
    }
}
