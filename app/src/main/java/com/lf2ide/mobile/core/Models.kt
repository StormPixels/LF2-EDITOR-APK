package com.lf2ide.mobile.core

/** Satu seksi (itr/bdy/opoint/...): nilai disimpan sebagai Int (bool = 0/1), urutan field mengikuti Schema. */
class Sect(val type: String, val v: LinkedHashMap<String, Int> = LinkedHashMap())

class Frame(var index: Int, var caption: String) {
    val top = LinkedHashMap<String, Int>()
    var sound: String? = null
    val points = LinkedHashMap<String, Sect>() // bpoint, opoint, cpoint, wpoint
    val itrs = ArrayList<Sect>()
    val bdys = ArrayList<Sect>()
}

object Schema {
    val FRAME_FIELDS = listOf(
        "pic", "state", "wait", "next", "dvx", "dvy", "dvz", "centerx", "centery", "mp",
        "hit_a", "hit_d", "hit_j", "hit_Fa", "hit_Ua", "hit_Da", "hit_Fj", "hit_Uj", "hit_Dj", "hit_ja"
    )
    // 'i' = int, 'b' = bool (ditandai ? di definisi)
    private fun f(s: String) = s.split(" ").map { if (it.endsWith("?")) it.dropLast(1) to 'b' else it to 'i' }
    val fields: Map<String, List<Pair<String, Char>>> = mapOf(
        "opoint" to f("kind x y action dvx dvy oid facing"),
        "bpoint" to f("x y"),
        "cpoint" to f("kind x y injury fronthurtact cover backhurtact vaction aaction jaction daction throwvx throwvy hurtable? decrease dircontrol? taction throwinjury throwvz"),
        "wpoint" to f("kind x y weaponact attacking cover? dvx dvy dvz"),
        "bdy" to f("kind x y w h"),
        "itr" to f("kind x y w h dvx dvy fall arest vrest effect bdefend injury zwidth")
    )
    val pairs: Map<String, List<Triple<String, String, String>>> = mapOf(
        "itr" to listOf(
            Triple("catchingact", "catchingact1", "catchingact2"),
            Triple("caughtact", "caughtact1", "caughtact2")
        )
    )
}
