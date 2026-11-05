package com.lf2ide.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.lf2ide.mobile.core.FrameInfo
import com.lf2ide.mobile.core.FrameOps
import com.lf2ide.mobile.core.Tokenizer

/** Salin/hapus frame dan salin/tempel move pada teks editor; `goto` memindahkan kursor ke posisi teks tertentu. */
class FrameActions(
    private val ctx: Context,
    private val ed: EditText,
    private val goto: (Int) -> Unit
) {
    private fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    fun copyFrame(cur: FrameInfo?) {
        if (cur == null) { toast("Pilih sebuah frame dulu"); return }
        val t = ed.text.toString()
        if (cur.e > t.length || !t.startsWith("<frame>", cur.s)) return
        val used = FrameOps.ranges(t).map { it.idx }.toSet()
        val ni = FrameOps.freeFrom(used, cur.idx + 1)
        if (ni == null) { toast("Tidak ada nomor frame yang kosong"); return }
        ed.text.insert(cur.e, "\n\n" + FrameOps.withIndex(t.substring(cur.s, cur.e), ni))
        goto(cur.e + 2 + 8)
        toast("Frame $ni dibuat dari ${cur.idx}")
    }

    fun delete(cur: FrameInfo?) {
        if (cur == null) { toast("Pilih sebuah frame dulu"); return }
        val refs = FrameOps.referrers(ed.text.toString(), cur.idx, cur.s)
        val m = "Hapus frame ${cur.idx} (${cur.caption})?" +
            if (refs > 0) "\n$refs frame lain masih menuju frame ini lewat next." else ""
        AlertDialog.Builder(ctx)
            .setMessage(m)
            .setPositiveButton("Hapus") { _, _ ->
                val t = ed.text.toString()
                if (cur.e <= t.length && t.startsWith("<frame>", cur.s)) {
                    ed.text.delete(cur.s, FrameOps.deleteEnd(t, cur.e))
                    ed.setSelection(minOf(cur.s, ed.text.length))
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun copyMove(cur: FrameInfo?) {
        if (cur == null) { toast("Pilih sebuah frame dulu"); return }
        if (cur.idx == 0) { toast("Mulai dari frame selain 0"); return }
        val raws = FrameOps.chain(ed.text.toString(), cur.idx)
        if (raws.isEmpty()) return
        MoveClip.frames = raws
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("lf2 move", raws.joinToString("\n\n")))
        } catch (ex: Exception) { }
        val ids = raws.map { FrameOps.indexOf(it) ?: -1 }
        toast("Move disalin: ${raws.size} frame (${ids.first()}…${ids.last()})")
    }

    fun pasteMove(cur: FrameInfo?) {
        var src = MoveClip.frames
        if (src.isEmpty()) {
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
                src = Tokenizer.getFrames(txt)
            } catch (ex: Exception) { }
        }
        if (src.isEmpty()) { toast("Belum ada move yang disalin"); return }
        val t = ed.text.toString()
        val used = FrameOps.ranges(t).map { it.idx }.toSet()
        val base = (cur?.idx ?: (used.maxOrNull() ?: 0)) + 1
        val out = FrameOps.relocate(src, used, base)
        if (out == null) { toast("Tidak cukup nomor frame yang kosong"); return }
        val at = cur?.e ?: t.length
        ed.text.insert(at, "\n\n" + out.joinToString("\n\n"))
        goto(at + 2 + 8)
        toast("Move ditempel: ${out.size} frame")
    }
}
