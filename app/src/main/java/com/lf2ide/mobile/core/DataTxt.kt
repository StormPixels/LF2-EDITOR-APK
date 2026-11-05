package com.lf2ide.mobile.core

/** Satu baris file di data.txt: bagian (object/background/...), id, tipe, dan jalur relatif terhadap folder game. */
class DataEntry(val section: String, val id: Int?, val type: Int?, val path: String) {
    var name: String? = null // nama dari header .dat, diisi setelah pemindaian

    val fileName: String get() = path.substringAfterLast('\\').substringAfterLast('/')
}

/** Pembaca data.txt LF2: daftar semua file .dat (objek, background, dst.) beserta lokasinya. */
object DataTxt {
    private val FILE = Regex("""(?<![A-Za-z_])file\s*:\s*(.+)$""")
    private val ID = Regex("""(?<![A-Za-z_])id\s*:\s*(\d+)""")
    private val TYPE = Regex("""(?<![A-Za-z_])type\s*:\s*(\d+)""")

    fun parse(t: String): List<DataEntry> {
        val out = ArrayList<DataEntry>()
        var section = ""
        for (raw in t.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("<") && line.endsWith(">")) {
                val tag = line.substring(1, line.length - 1).trim().lowercase()
                section = if (tag.endsWith("_end")) "" else tag
                continue
            }
            val m = FILE.find(line) ?: continue
            val path = m.groupValues[1].substringBefore("#").trim()
            if (path.isEmpty()) continue
            val before = line.substring(0, m.range.first)
            val id = ID.find(before)?.groupValues?.get(1)?.toIntOrNull()
            val type = TYPE.find(before)?.groupValues?.get(1)?.toIntOrNull()
            out.add(DataEntry(if (section.isEmpty()) "lainnya" else section, id, type, path))
        }
        return out
    }

    fun typeLabel(type: Int?): String = when (type) {
        0 -> "Karakter"
        1 -> "Senjata"
        2 -> "Senjata berat"
        3 -> "Serangan khusus"
        null -> ""
        else -> "Tipe $type"
    }

    fun sectionLabel(section: String, type: Int?): String = when (section) {
        "object" -> "Objek" + (if (type != null) " · " + typeLabel(type) else "")
        "background" -> "Latar (background)"
        "file_editing" -> "File yang diedit"
        "data" -> "Data"
        else -> section.replaceFirstChar { it.uppercase() }
    }

    /** Nama objek dari header .dat (baris `name: ...` sebelum frame pertama), atau null. */
    fun nameOf(datText: String): String? =
        FrameOps.headFields(datText).firstOrNull { it.key == "name" }?.value?.takeIf { it.isNotEmpty() }
}
