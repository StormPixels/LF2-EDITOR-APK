package com.lf2ide.mobile

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.lf2ide.mobile.core.DataEntry
import com.lf2ide.mobile.core.DataTxt

/** Daftar semua file dari data.txt (dikelompokkan), dengan pencarian; ketuk untuk membuka. */
class ProjectBrowser(private val ctx: Context, private val onPick: (DataEntry) -> Unit) {
    private class PRow(val header: String?, val entry: DataEntry?)

    private var entries: List<DataEntry> = emptyList()
    private val rows = ArrayList<PRow>()
    private var query = ""
    private var dlg: AlertDialog? = null
    private val adapter = PAdapter()
    private val dataTxt = DataEntry("data", null, null, "data/data.txt")

    fun setEntries(list: List<DataEntry>) {
        entries = list
        rebuild()
        adapter.notifyDataSetChanged()
    }

    /** Dipanggil saat nama objek bertambah (hasil pemindaian). */
    fun refresh() {
        rebuild()
        adapter.notifyDataSetChanged()
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun matches(e: DataEntry): Boolean {
        if (query.isEmpty()) return true
        return (e.name ?: "").lowercase().contains(query) || e.path.lowercase().contains(query) || (e.id?.toString() ?: "") == query
    }

    private fun rebuild() {
        rows.clear()
        rows.add(PRow(null, dataTxt))
        val order = listOf("object", "background", "file_editing")
        val sections = entries.map { it.section }.distinct().sortedBy { val i = order.indexOf(it); if (i < 0) 99 else i }
        for (sec in sections) {
            val inSec = entries.filter { it.section == sec }
            val types = if (sec == "object") inSec.map { it.type }.distinct().sortedBy { it ?: 99 } else listOf<Int?>(null)
            for (ty in types) {
                val list = inSec.filter { (sec != "object" || it.type == ty) && matches(it) }
                    .sortedBy { it.id ?: Int.MAX_VALUE }
                if (list.isEmpty()) continue
                rows.add(PRow(DataTxt.sectionLabel(sec, ty) + "  (" + list.size + ")", null))
                for (e in list) rows.add(PRow(null, e))
            }
        }
    }

    fun show() {
        rebuild()
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(0xFF111B2E.toInt())
        box.setPadding(dp(12), dp(8), dp(12), 0)
        val input = EditText(ctx)
        input.hint = "Cari nama, file, atau id"
        input.setSingleLine()
        input.setTextColor(0xFFE2E8F0.toInt())
        input.setHintTextColor(0xFF94A3B8.toInt())
        input.doAfterTextChanged {
            query = input.text.toString().trim().lowercase()
            refresh()
        }
        box.addView(input)
        val lv = ListView(ctx)
        lv.adapter = adapter
        lv.divider = null
        lv.setOnItemClickListener { _, _, pos, _ ->
            val e = rows.getOrNull(pos)?.entry
            if (e != null) {
                dlg?.dismiss()
                onPick(e)
            }
        }
        box.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)))
        query = ""
        val d = AlertDialog.Builder(ctx)
            .setTitle("Proyek · ${entries.size} file")
            .setView(box)
            .setNegativeButton("Tutup", null)
            .create()
        dlg = d
        d.show()
    }

    private inner class PAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val r = rows[position]
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            val h = r.header
            val e = r.entry
            if (h != null) {
                col.setPadding(dp(4), dp(12), dp(4), dp(4))
                col.addView(TextView(ctx).apply { text = h; textSize = 13f; setTextColor(0xFFD4A72C.toInt()) })
            } else if (e != null) {
                col.setPadding(dp(8), dp(6), dp(8), dp(6))
                val title = if (e.section == "data") "data.txt (buka sebagai teks)" else (e.name ?: e.fileName)
                col.addView(TextView(ctx).apply { text = title; textSize = 15f; setTextColor(0xFFE2E8F0.toInt()) })
                val sub = (if (e.id != null) "id ${e.id} · " else "") + e.path
                col.addView(TextView(ctx).apply { text = sub; textSize = 11f; setTextColor(0xFF94A3B8.toInt()) })
            }
            return col
        }
    }
}
