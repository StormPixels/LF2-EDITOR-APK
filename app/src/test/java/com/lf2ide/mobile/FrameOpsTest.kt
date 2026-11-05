package com.lf2ide.mobile

import com.lf2ide.mobile.core.DataTxt
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

    // ---------- editor visual ----------
    private val head = "<bmp_begin>\nname: Davis\nhead: sprite\\sys\\davis_f.bmp\nfile(0-69): sprite\\sys\\davis_0.bmp  w: 79  h: 79  row: 10  col: 7\nwalking_speed 4.000000\n<bmp_end>\n\n"
    private val vdoc = head + listOf(
        fr(0, "standing", 1), fr(1, "standing", 2), fr(2, "standing", 0),
        "<frame> 5 walking_1\n   pic: 5  state: 1  wait: 3  next: 6  dvx: 4\n   bdy:\n      kind: 0  x: 1  y: 2  w: 3  h: 4\n   bdy_end:\n<frame_end>",
        "<frame> 6 walking_2\n   pic: 6  state: 1  wait: 3  next: 5\n<frame_end>"
    ).joinToString("\n\n")

    @Test
    fun infosAndGroups() {
        val inf = FrameOps.infos(vdoc)
        assertEquals(listOf(0, 1, 2, 5, 6), inf.map { it.idx })
        assertEquals(5, inf[3].pic)
        assertEquals(3, inf[3].wait)
        assertEquals(6, inf[3].next)
        assertEquals("walking_1", inf[3].caption)
        val g = FrameOps.group(inf)
        assertEquals(listOf("standing", "walking"), g.map { it.key })
        assertEquals(listOf(3, 2), g.map { it.frames.size })
    }

    @Test
    fun setTopEditsInsertsAndRemoves() {
        val raw = "<frame> 5 walking_1\n   pic: 5  state: 1  wait: 3  next: 6\n   bdy:\n      kind: 0  x: 1  y: 2  w: 3  h: 4\n   bdy_end:\n<frame_end>"
        val a = FrameOps.setTop(raw, "wait", "9")
        assertTrue(a.contains("wait: 9"))
        assertTrue(a.contains("w: 3")) // w di dalam bdy tidak tersentuh
        val b = FrameOps.setTop(raw, "dvx", "4")
        assertTrue(b.startsWith("<frame> 5 walking_1\n   dvx: 4\n"))
        val c = FrameOps.setTop(raw, "state", null)
        assertTrue(!c.contains("state"))
        assertTrue(c.contains("pic: 5") && c.contains("wait: 3"))
        // field di dalam seksi tidak ikut terbaca sebagai field utama
        assertNull(FrameOps.intIn(raw, "kind"))
        // frame tanpa seksi apa pun
        val d = FrameOps.setTop("<frame> 6 x\n   pic: 6\n<frame_end>", "next", "7")
        assertTrue(d.contains("next: 7") && d.endsWith("<frame_end>"))
        // teks (sound)
        val e = FrameOps.setTop(raw, "sound", "data\\001.wav")
        assertEquals("data\\001.wav", Regex("sound: (\\S+)").find(e)!!.groupValues[1])
    }

    @Test
    fun captionAndHeader() {
        assertTrue(FrameOps.withCaption("<frame> 5 walking_1\n   pic: 5\n<frame_end>", "jalan").startsWith("<frame> 5 jalan\n"))
        val hf = FrameOps.headFields(vdoc)
        assertEquals(listOf("name", "head", "walking_speed"), hf.map { it.key })
        assertEquals("Davis", hf[0].value)
        assertEquals("4.000000", hf[2].value)
        val f = hf[0]
        val t2 = vdoc.substring(0, f.s) + "Zero" + vdoc.substring(f.e)
        assertEquals("Zero", FrameOps.headFields(t2)[0].value)
    }

    // ---------- data.txt ----------
    @Test
    fun dataTxtParse() {
        val txt = "# komentar\r\n<object>\r\nid: 1  type: 0  file: data\\louis.dat\r\nid: 200 type: 3 file: data\\ball.dat # ket\r\n<object_end>\r\n" +
            "<background>\r\nid: 0  file: bg\\sys\\hwn\\bg.dat\r\n<background_end>\r\n<file_editing>\r\nfile: data\\x.dat\r\n<file_editing_end>\r\n"
        val l = DataTxt.parse(txt)
        assertEquals(4, l.size)
        assertEquals("object", l[0].section)
        assertEquals(1, l[0].id)
        assertEquals(0, l[0].type)
        assertEquals("data\\louis.dat", l[0].path)
        assertEquals("louis.dat", l[0].fileName)
        assertEquals("data\\ball.dat", l[1].path) // komentar di belakang dibuang
        assertEquals(3, l[1].type)
        assertEquals("background", l[2].section)
        assertNull(l[2].type)
        assertEquals("file_editing", l[3].section)
        assertNull(l[3].id)
        assertEquals("Davis", DataTxt.nameOf(vdoc))
        assertNull(DataTxt.nameOf("<frame> 0 a\n<frame_end>"))
        assertEquals("Objek · Karakter", DataTxt.sectionLabel("object", 0))
    }
}

