package com.lf2ide.mobile.core

// Dihasilkan dari data.lang.xml: daftar keyword per bahasa (indeks daftar -> kata)
object Lang {
    val keywords: Map<String, Map<String, Set<String>>> = mapOf(
        "default" to mapOf(

        ),
        "object_dat" to mapOf(
            "0" to setOf("frame", "frame_end", "bmp_begin", "bmp_end", "weapon_strength_list", "weapon_strength_list_end", "object", "object_end"),
            "1" to setOf("bdy", "bdy_end", "wpoint", "wpoint_end", "itr", "itr_end", "cpoint", "cpoint_end", "bpoint", "bpoint_end", "opoint", "opoint_end"),
            "2" to setOf("name", "head", "small", "file", "x", "y", "w", "h", "row", "col", "walking_frame_rate", "walking_speed", "walking_speedz", "running_frame_rate", "running_speed", "running_speedz", "heavy_walking_speed", "heavy_walking_speedz", "heavy_running_speed", "heavy_running_speedz", "jump_height", "jump_distance", "jump_distancez", "dash_height", "dash_distance", "dash_distancez", "rowing_height", "rowing_distance", "kind", "pic", "state", "wait", "next", "dvx", "dvy", "dvz", "centerx", "centery", "sound", "hit_fa", "hit_ua", "hit_da", "hit_fj", "hit_uj", "hit_dj", "hit_a", "hit_d", "hit_j", "hit_ja", "arest", "vrest", "fall", "bdefend", "injury", "zwidth", "effect", "catchingact", "caughtact", "weaponact", "attacking", "cover", "action", "oid", "facing", "vaction", "aaction", "taction", "throwvx", "throwvy", "throwvz", "hurtable", "throwinjury", "decrease", "dircontrol", "fronthurtact", "backhurtact", "weapon_hit_sound", "weapon_drop_sound", "weapon_broken_sound", "weapon_hp", "entry", "weapon_drop_hurt", "id", "mp")
        ),
        "stage_dat" to mapOf(
            "0" to setOf("stage", "stage_end", "phase", "phase_end", "boss", "soldier", "end"),
            "2" to setOf("id", "bound", "music", "hp", "times", "ratio", "when_clear_goto_phase", "reserve", "mp", "act", "x", "y", "join")
        ),
        "bg_dat" to mapOf(
            "1" to setOf("layer", "layer_end"),
            "2" to setOf("name", "x", "y", "width", "height", "loop", "rect", "zboundary", "shadow", "shadowsize", "transparency", "cc", "c1", "c2")
        ),
        "data_txt" to mapOf(
            "0" to setOf("background", "background_end", "file_editing", "file_editing_end", "object", "object_end"),
            "2" to setOf("file", "id", "type")
        ),
        "as" to mapOf(
            "0" to setOf("abstract", "as", "base", "break", "case", "catch", "checked", "continue", "default", "delegate", "do", "else", "event", "explicit", "extern", "false", "finally", "fixed", "for", "foreach", "goto", "if", "implicit", "in", "interface", "internal", "is", "lock", "namespace", "new", "null", "object", "operator", "out", "override", "params", "private", "protected", "public", "readonly", "ref", "return", "sealed", "sizeof", "stackalloc", "switch", "this", "throw", "true", "try", "typeof", "unchecked", "unsafe", "using", "virtual", "while"),
            "1" to setOf("bool", "byte", "char", "class", "const", "decimal", "double", "enum", "float", "int", "long", "sbyte", "short", "static", "string", "struct", "uint", "ulong", "ushort", "void")
        )
    )
}
