package com.lf2ide.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Sheet sprite: dimuat di thread latar, hitam = transparan (seperti IDE asli), disalin ke penyimpanan aplikasi. */
class SpriteStore(private val ctx: Context) {
    private val mem = HashMap<String, Bitmap>()
    private val busy = HashSet<String>()
    private val missing = HashSet<String>()
    private val dir = File(ctx.filesDir, "sprites").apply { mkdirs() }
    private val main = Handler(Looper.getMainLooper())

    /** Pencari tambahan (mis. dari folder yang dibuka); dipanggil di thread latar. */
    @Volatile
    var finder: ((String) -> ByteArray?)? = null

    fun has(name: String) = mem.containsKey(name)
    fun peek(name: String): Bitmap? = mem[name]
    fun resetMissing() { missing.clear() }

    fun request(name: String, onReady: () -> Unit) {
        if (mem.containsKey(name) || missing.contains(name) || !busy.add(name)) return
        Thread {
            var bmp: Bitmap? = null
            try {
                val f = File(dir, name)
                var bytes: ByteArray? = if (f.exists()) f.readBytes() else null
                if (bytes == null) {
                    bytes = finder?.invoke(name)
                    if (bytes != null) f.writeBytes(bytes)
                }
                if (bytes != null) bmp = decode(bytes)
            } catch (e: Exception) { }
            val out = bmp
            main.post {
                busy.remove(name)
                if (out != null) { mem[name] = out; onReady() } else missing.add(name)
            }
        }.start()
    }

    fun addFromUri(uri: Uri, onReady: () -> Unit) {
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
            main.post { if (bmp != null) { mem[name] = bmp; missing.remove(name); onReady() } }
        }.start()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) if ((px[i] and 0xFFFFFF) == 0) px[i] = 0
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return bmp
    }
}
