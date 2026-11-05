package com.lf2ide.mobile.core

/** Definisi satu field pada panel: nama kunci di .dat, nama ramah, penjelasan, dan jenisnya. */
class FieldDef(
    val key: String,
    val label: String,
    val desc: String,
    val ref: Boolean = false,   // berisi nomor frame tujuan
    val text: Boolean = false   // teks biasa, bukan angka
)

class Section(val title: String, val fields: List<FieldDef>)

/** Teks antarmuka (bahasa Indonesia) untuk editor visual. */
object Labels {
    private val GROUP: Map<String, String> = mapOf(
        "standing" to "Berdiri",
        "walking" to "Berjalan",
        "running" to "Berlari",
        "heavy_obj_walk" to "Berjalan bawa benda berat",
        "heavy_obj_run" to "Berlari bawa benda berat",
        "heavy_stop_run" to "Berhenti lari bawa benda berat",
        "normal_weapon_atck" to "Serang pakai senjata",
        "jump_weapon_atck" to "Serang senjata di udara",
        "run_weapon_atck" to "Serang senjata sambil lari",
        "dash_weapon_atck" to "Serang senjata saat dash",
        "light_weapon_thw" to "Lempar senjata ringan",
        "heavy_weapon_thw" to "Lempar benda berat",
        "sky_lgt_wp_thw" to "Lempar senjata di udara",
        "weapon_drink" to "Minum",
        "punch" to "Pukulan",
        "super_punch" to "Pukulan kuat",
        "jump_attack" to "Serang saat lompat",
        "run_attack" to "Serang saat lari",
        "dash_attack" to "Serang saat dash",
        "dash_defend" to "Tangkis saat dash",
        "rowing" to "Berguling",
        "defend" to "Bertahan",
        "broken_defend" to "Pertahanan pecah",
        "picking_light" to "Ambil benda ringan",
        "picking_heavy" to "Ambil benda berat",
        "catching" to "Menangkap",
        "picked_caught" to "Tertangkap",
        "falling" to "Jatuh",
        "ice" to "Membeku",
        "fire" to "Terbakar",
        "tired" to "Kelelahan",
        "jump" to "Lompat",
        "dash" to "Dash",
        "crouch" to "Jongkok",
        "stop_running" to "Berhenti lari",
        "injured" to "Terluka",
        "lying" to "Terbaring",
        "throw_lying_man" to "Lempar lawan yang terbaring",
        "dummy" to "Frame kosong",
        // senjata
        "in_the_sky" to "Di udara",
        "on_hand" to "Dipegang",
        "throwing" to "Dilempar",
        "on_ground" to "Di tanah",
        "just_on_ground" to "Baru mendarat",
        "just_on_on_ground" to "Baru mendarat (2)",
        // serangan / proyektil
        "flying" to "Terbang",
        "hiting" to "Mengenai sasaran",
        "hit" to "Terkena",
        "rebounding" to "Memantul",
        "disappearing" to "Menghilang",
        "dflying" to "Terbang ke bawah",
        "uflying" to "Terbang ke atas"
    )

    fun groupName(key: String): String {
        if (key.isEmpty()) return "Frame"
        return GROUP[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    val STATE: Map<Int, String> = mapOf(
        0 to "berdiri", 1 to "jalan", 2 to "lari", 3 to "serang", 4 to "lompat", 5 to "dash",
        6 to "berguling", 7 to "bertahan", 8 to "pertahanan pecah", 9 to "menangkap",
        10 to "tertangkap", 11 to "terluka", 12 to "jatuh", 13 to "membeku", 14 to "terbaring"
    )

    private fun f(key: String, label: String, desc: String, ref: Boolean = false, text: Boolean = false) =
        FieldDef(key, label, desc, ref, text)

    val FRAME_SECTIONS: List<Section> = listOf(
        Section("FRAME", listOf(
            f("caption", "Nama", "Nama frame. Hanya label untuk memudahkan, tidak memengaruhi permainan.", text = true),
            f("state", "Status", "Jenis perilaku frame (0 berdiri, 1 jalan, 2 lari, 3 serang, 4 lompat, 5 dash, 6 berguling, 7 bertahan, dst.).")
        )),
        Section("GAMBAR", listOf(
            f("pic", "Gambar", "Nomor sprite yang ditampilkan dari lembar gambar."),
            f("centerx", "Tengah x", "Titik poros sprite secara horizontal (di sekitar kaki karakter)."),
            f("centery", "Tengah y", "Titik poros sprite secara vertikal (biasanya telapak kaki).")
        )),
        Section("WAKTU", listOf(
            f("wait", "Waktu", "Lama frame tampil, dalam tick (30 tick = 1 detik)."),
            f("next", "Berikutnya", "Frame yang dituju setelah waktu habis. 999 = akhir gerakan, 1000 = objek hilang.", ref = true)
        )),
        Section("GERAKAN", listOf(
            f("dvx", "Gerak x", "Perubahan kecepatan horizontal saat frame ini mulai (550 = tidak diubah)."),
            f("dvy", "Gerak y", "Perubahan kecepatan vertikal saat frame ini mulai (550 = tidak diubah)."),
            f("dvz", "Gerak z", "Perubahan kecepatan ke arah kedalaman (550 = tidak diubah).")
        )),
        Section("TRANSISI", listOf(
            f("hit_a", "Setelah A", "Frame tujuan bila tombol serang (A) ditekan.", ref = true),
            f("hit_d", "Setelah D", "Frame tujuan bila tombol bertahan (D) ditekan.", ref = true),
            f("hit_j", "Setelah J", "Frame tujuan bila tombol lompat (J) ditekan.", ref = true),
            f("hit_Fa", "D>A", "Frame tujuan untuk kombo bertahan, maju, lalu serang.", ref = true),
            f("hit_Ua", "D^A", "Frame tujuan untuk kombo bertahan, atas, lalu serang.", ref = true),
            f("hit_Da", "DvA", "Frame tujuan untuk kombo bertahan, bawah, lalu serang.", ref = true),
            f("hit_Fj", "D>J", "Frame tujuan untuk kombo bertahan, maju, lalu lompat.", ref = true),
            f("hit_Uj", "D^J", "Frame tujuan untuk kombo bertahan, atas, lalu lompat.", ref = true),
            f("hit_Dj", "DvJ", "Frame tujuan untuk kombo bertahan, bawah, lalu lompat.", ref = true),
            f("hit_ja", "DJA", "Frame tujuan untuk kombo bertahan, lompat, serang bersamaan.", ref = true)
        )),
        Section("LAINNYA", listOf(
            f("mp", "Mana", "Perubahan MP saat frame ini mulai; nilai negatif memakai MP."),
            f("sound", "Suara", "Berkas suara yang dimainkan saat frame ini tampil.", text = true)
        ))
    )

    private val HEAD: Map<String, String> = mapOf(
        "name" to "Nama",
        "head" to "Gambar kepala",
        "small" to "Gambar kecil",
        "walking_frame_rate" to "Laju frame jalan",
        "walking_speed" to "Kecepatan jalan",
        "walking_speedz" to "Kecepatan jalan (z)",
        "running_frame_rate" to "Laju frame lari",
        "running_speed" to "Kecepatan lari",
        "running_speedz" to "Kecepatan lari (z)",
        "heavy_walking_speed" to "Kecepatan jalan bawa berat",
        "heavy_walking_speedz" to "Kecepatan jalan bawa berat (z)",
        "heavy_running_speed" to "Kecepatan lari bawa berat",
        "heavy_running_speedz" to "Kecepatan lari bawa berat (z)",
        "jump_height" to "Tinggi lompat",
        "jump_distance" to "Jarak lompat",
        "jump_distancez" to "Jarak lompat (z)",
        "dash_height" to "Tinggi dash",
        "dash_distance" to "Jarak dash",
        "dash_distancez" to "Jarak dash (z)",
        "rowing_height" to "Tinggi berguling",
        "rowing_distance" to "Jarak berguling"
    )

    fun headName(key: String): String = HEAD[key] ?: key

    /** Keterangan di bawah field yang berisi nomor frame tujuan. */
    fun refHint(key: String, v: Int?, frames: List<FrameInfo>): String {
        if (v == null) return ""
        if (v == 999) return "999 = akhir gerakan"
        if (v == 1000) return "1000 = objek hilang"
        if (v == 0 && key != "next") return ""
        val t = frames.firstOrNull { it.idx == v } ?: return "⚠ frame $v tidak ada"
        return "→ frame $v ${t.caption}"
    }
}
