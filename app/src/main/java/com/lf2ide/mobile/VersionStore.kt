package com.lf2ide.mobile

import java.io.File

/** Riwayat versi lokal per file: satu salinan "asli" (saat pertama dibuka) dan salinan setiap kali disimpan. */
class VersionStore(private val dir: File) {
    private fun sub(key: String): File {
        val d = File(dir, Integer.toHexString(key.hashCode()))
        d.mkdirs()
        return d
    }

    fun saveOriginal(key: String, text: String) {
        try {
            val f = File(sub(key), "orig.txt")
            if (!f.exists()) f.writeText(text, Charsets.ISO_8859_1)
        } catch (e: Exception) { }
    }

    fun setOriginal(key: String, text: String) {
        try { File(sub(key), "orig.txt").writeText(text, Charsets.ISO_8859_1) } catch (e: Exception) { }
    }

    fun original(key: String): String? = try {
        val f = File(sub(key), "orig.txt")
        if (f.exists()) f.readText(Charsets.ISO_8859_1) else null
    } catch (e: Exception) { null }

    /** Simpan salinan baru (dilewati bila sama dengan salinan terakhir); simpan paling banyak 25. */
    fun add(key: String, text: String) {
        try {
            val d = sub(key)
            val cur = list(key)
            if (cur.isNotEmpty() && read(cur[0].second) == text) return
            var ts = System.currentTimeMillis()
            if (cur.isNotEmpty() && ts <= cur[0].first) ts = cur[0].first + 1
            File(d, "v_$ts.txt").writeText(text, Charsets.ISO_8859_1)
            for ((_, f) in list(key).drop(25)) f.delete()
        } catch (e: Exception) { }
    }

    /** Daftar salinan (waktu, file), terbaru dulu. */
    fun list(key: String): List<Pair<Long, File>> {
        val files = sub(key).listFiles() ?: return emptyList()
        return files.filter { it.name.startsWith("v_") && it.name.endsWith(".txt") }
            .mapNotNull { f -> f.name.removePrefix("v_").removeSuffix(".txt").toLongOrNull()?.let { it to f } }
            .sortedByDescending { it.first }
    }

    fun read(f: File): String = f.readText(Charsets.ISO_8859_1)
}
