package com.lf2ide.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Sheet sprite, diminta dengan jalur persis seperti tertulis di bmp_begin (mis. sprite\sys\bandit_0.bmp).
 * Dimuat di thread latar; hitam = transparan (seperti IDE asli) kecuali gambar sudah punya alpha (PNG).
 * Hasil disalin ke penyimpanan aplikasi sebagai cache.
 */
class SpriteStore(private val ctx: Context) {
    private val mem = HashMap<String, Bitmap>()
    private val busy = HashSet<String>()
    private val missing = HashSet<String>()
    private val dir = File(ctx.filesDir, "sprites").apply { mkdirs() }
    private val main = Handler(Looper.getMainLooper())

    /** Pencari dari folder game; menerima jalur asli dari .dat; dipanggil di thread latar. */
    @Volatile
    var finder: ((String) -> ByteArray?)? = null

    private fun norm(p: String) = p.trim().replace('\\', '/').lowercase()
    private fun base(k: String) = k.substringAfterLast('/')
    private fun disk(k: String) = k.replace('/', '_')

    fun has(path: String): Boolean { val k = norm(path); return mem.containsKey(k) || mem.containsKey(base(k)) }
    fun peek(path: String): Bitmap? { val k = norm(path); return mem[k] ?: mem[base(k)] }
    fun isMissing(path: String) = missing.contains(norm(path))

    fun reset() {
        mem.clear(); missing.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** onDone dipanggil setelah selesai, berhasil atau tidak (cek has()/isMissing()). */
    fun request(path: String, onDone: () -> Unit) {
        val key = norm(path)
        if (has(path) || missing.contains(key) || !busy.add(key)) return
        Thread {
            var bmp: Bitmap? = null
            try {
                var f = File(dir, disk(key))
                if (!f.exists()) f = File(dir, base(key))      // sprite yang dipilih manual
                var bytes: ByteArray? = if (f.exists()) f.readBytes() else null
                if (bytes == null) {
                    bytes = finder?.invoke(path)
                    if (bytes != null) File(dir, disk(key)).writeBytes(bytes)
                }
                if (bytes != null) bmp = decode(bytes)
            } catch (e: Exception) { }
            val out = bmp
            main.post {
                busy.remove(key)
                if (out != null) mem[key] = out else missing.add(key)
                onDone()
            }
        }.start()
    }

    /** Sprite dipilih manual: disimpan dengan nama file saja. */
    fun addFromUri(uri: Uri, onDone: () -> Unit) {
        Thread {
            var out: Bitmap? = null
            var nm = ""
            try {
                nm = (DocumentFile.fromSingleUri(ctx, uri)?.name ?: "").lowercase()
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (nm.isNotEmpty() && bytes != null) {
                    File(dir, nm).writeBytes(bytes)
                    out = decode(bytes)
                }
            } catch (e: Exception) { }
            val bmp = out
            val name = nm
            main.post {
                if (bmp != null) { mem[name] = bmp; missing.clear() }
                onDone()
            }
        }.start()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var hasAlpha = false
        for (p in px) if ((p ushr 24) < 255) { hasAlpha = true; break }
        if (!hasAlpha) for (i in px.indices) if ((px[i] and 0xFFFFFF) == 0) px[i] = 0
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return bmp
    }
}
