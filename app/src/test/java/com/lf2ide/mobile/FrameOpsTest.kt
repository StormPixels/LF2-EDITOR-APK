package com.lf2ide.mobile

import com.lf2ide.mobile.core.FrameOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameOpsTest {
    private fun fr(i: Int, cap: String, next: Int, extra: String = "") =
        "<frame> $i $cap\n   pic: $i  state: 3  wait: 2  next: $next  centerx: 40  centery: 79\n$extra<frame_end>"

    private val doc = listOf(
        fr(0, "standing", 0),
        fr(60, "punch_1", 61, "   itr:\n      kind: 0  x: 1  y: 2  w: 3  h: 4  catchingact: 62 63\n   itr_end:\n"),
        fr(61, "punch_2", 62, "   opoint:\n      kind: 1  x: 5  y: 6  action: 60  oid: 200\n   opoint_end:\n"),
        fr(62, "punch_3", 999, "   hit_a: 60  hit_d: 0\n"),
        fr(70, "other", 999)
    ).joinToString("\n\n")

    @Test
    fun rangesAndHeaders() {
        val r = FrameOps.ranges(doc)
        assertEquals(listOf(0, 60, 61, 62, 70), r.map { it.idx })
        assertTrue(doc.substring(r[1].s, r[1].e).startsWith("<frame> 60 punch_1"))
        assertTrue(doc.substring(r[1].s, r[1].e).endsWith("<frame_end>"))
        assertEquals("punch_2", FrameOps.headers(doc)[2].caption)
    }

    @Test
    fun freeIndex() {
        assertEquals(1, FrameOps.freeFrom(setOf(0, 60), 0))
        assertEquals(63, FrameOps.freeFrom(setOf(61, 62), 61))
        assertNull(FrameOps.freeFrom(setOf(998), 998))
    }

    @Test
    fun chainFollowsNextAndStops() {
        val c = FrameOps.chain(doc, 60)
        assertEquals(listOf(60, 61, 62), c.map { FrameOps.indexOf(it) })
        // berakhir di next >= 999; start dari frame yang tidak ada -> kosong
        assertEquals(0, FrameOps.chain(doc, 5).size)
        // next: 0 menghentikan rantai (frame 0 tidak ikut)
        val loop = fr(10, "a", 11) + "\n\n" + fr(11, "b", 0)
        assertEquals(2, FrameOps.chain(loop, 10).size)
    }

    @Test
    fun relocateRenumbersAndRelinks() {
        val chain = FrameOps.chain(doc, 60)
        val used = FrameOps.ranges(doc).map { it.idx }.toSet()
        val out = FrameOps.relocate(chain, used, 63)!!
        assertEquals(listOf(63, 64, 65), out.map { FrameOps.indexOf(it) })
        // next antar frame di dalam kelompok diarahkan ulang; 999 tetap
        assertEquals(64, FrameOps.nextOf(out[0]))
        assertEquals(65, FrameOps.nextOf(out[1]))
        assertEquals(999, FrameOps.nextOf(out[2]))
        // opoint action 60 -> 63, hit_a 60 -> 63, hit_d 0 tetap 0
        assertTrue(out[1].contains("action: 63"))
        assertTrue(out[2].contains("hit_a: 63"))
        assertTrue(out[2].contains("hit_d: 0"))
        // catchingact 62 63 (62 -> 65; 63 bukan bagian peta lama, tetap 63)
        assertTrue(out[0].contains("catchingact: 65 63"))
        // oid bukan nomor frame, tidak berubah
        assertTrue(out[1].contains("oid: 200"))
    }

    @Test
    fun relocateSkipsUsedAndFailsWhenFull() {
        val a = fr(1, "a", 2)
        val b = fr(2, "b", 999)
        val out = FrameOps.relocate(listOf(a, b), setOf(1, 2, 3, 5), 1)!!
        assertEquals(listOf(4, 6), out.map { FrameOps.indexOf(it) })
        assertEquals(6, FrameOps.nextOf(out[0]))
        val full = (1..FrameOps.MAX_INDEX).toSet()
        assertNull(FrameOps.relocate(listOf(a), full, 1))
    }

    @Test
    fun remapDoesNotTouchSimilarKeys() {
        val raw = "<frame> 1 x\n   next: 2  weaponact: 2  aaction: 2  fronthurtact: 2  oid: 2  dvx: 2\n<frame_end>"
        val out = FrameOps.remap(raw, mapOf(2 to 9))
        assertTrue(out.contains("next: 9"))
        assertTrue(out.contains("weaponact: 9"))
        assertTrue(out.contains("aaction: 9"))
        assertTrue(out.contains("fronthurtact: 9"))
        assertTrue(out.contains("oid: 2"))
        assertTrue(out.contains("dvx: 2"))
    }

    @Test
    fun copyFrameHeaderOnly() {
        val raw = fr(61, "punch_2", 62, "   unknown_field: 7\n")
        val c = FrameOps.withIndex(raw, 80)
        assertTrue(c.startsWith("<frame> 80 punch_2"))
        assertTrue(c.contains("unknown_field: 7")) // field tak dikenal tetap ada
        assertEquals(61, FrameOps.indexOf(raw))
    }

    @Test
    fun referrersAndDelete() {
        assertEquals(1, FrameOps.referrers(doc, 62, FrameOps.ranges(doc)[3].s)) // 61 -> 62
        assertEquals(0, FrameOps.referrers(doc, 70, -1))
        val r = FrameOps.ranges(doc)[1]
        val end = FrameOps.deleteEnd(doc, r.e)
        val after = doc.substring(0, r.s) + doc.substring(end)
        assertNotNull(FrameOps.ranges(after).firstOrNull { it.idx == 61 })
        assertEquals(listOf(0, 61, 62, 70), FrameOps.ranges(after).map { it.idx })
    }
}
