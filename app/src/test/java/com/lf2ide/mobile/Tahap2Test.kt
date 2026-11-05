package com.lf2ide.mobile

import com.lf2ide.mobile.core.Diff
import com.lf2ide.mobile.core.FrameOps
import com.lf2ide.mobile.core.Validator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class Tahap2Test {
    private fun fr(i: Int, cap: String, next: Int, extra: String = "") =
        "<frame> $i $cap\n   pic: $i  state: 3  wait: 2  next: $next  centerx: 40  centery: 79\n$extra<frame_end>"

    private fun doc(vararg f: String) = f.joinToString("\n\n")

    // ---------- Validator ----------
    @Test
    fun validatorFindsProblems() {
        val t = doc(
            fr(0, "standing", 1),
            fr(1, "standing", 77),                                   // next ke frame yang tidak ada
            fr(1, "dup", 999),                                       // nomor ganda
            "<frame> 5 walk\n   pic: 5  state: 1  wait: 3  hit_a: 88\n   bdy:\n      kind: 0  x: 1  y: 2  w: 0\n   bdy_end:\n<frame_end>"
        )
        val p = Validator.check(t, listOf(0..3))
        val texts = p.map { it.text }
        assertTrue(texts.any { it.contains("next menuju frame 77") })
        assertTrue(texts.any { it.contains("dipakai lebih dari sekali") })
        assertTrue(texts.any { it.contains("hit_a menuju frame 88") })
        assertTrue(texts.any { it.contains("bdy tanpa field h") })
        assertTrue(texts.any { it.contains("w = 0") })
        assertTrue(texts.any { it.contains("field next belum ada") })
        assertTrue(texts.any { it.contains("pic 5 tidak ada di lembar") })
        // tingkat: lompatan ke frame hilang = kesalahan
        assertEquals(0, p.first { it.text.contains("next menuju frame 77") }.level)
    }

    @Test
    fun validatorCleanAndBalance() {
        val ok = doc(fr(0, "standing", 1), fr(1, "standing", 999), fr(2, "x", 0))
        assertEquals(0, Validator.check(ok, listOf(0..10)).size)
        val broken = ok.replaceFirst("<frame_end>", "")
        assertTrue(Validator.check(broken).any { it.text.contains("tidak sama") })
        // action/weaponact menuju objek lain, tidak diperiksa
        val other = doc(fr(0, "a", 999, "   opoint:\n      kind: 1  x: 1  y: 2  action: 500  oid: 9\n   opoint_end:\n"))
        assertEquals(0, Validator.check(other).size)
    }

    // ---------- Diff ----------
    private val orig = doc(
        fr(0, "standing", 1),
        fr(1, "punch_1", 2, "   itr:\n      kind: 0  x: 1  y: 2  w: 3  h: 4\n   itr_end:\n"),
        fr(2, "punch_2", 999)
    )

    @Test
    fun diffListsChanges() {
        var now = orig.replace("wait: 2  next: 2", "wait: 5  next: 2")               // frame 1 berubah
        now = now.replace("w: 3", "w: 9")                                          // box berubah
        now = now.replace(fr(2, "punch_2", 999), "")                                // frame 2 dihapus
        now += "\n\n" + fr(7, "baru", 999)                                         // frame 7 baru
        val ch = Diff.frames(orig, now)
        assertEquals(listOf('~', '-', '+'), ch.map { it.kind })
        assertEquals(listOf(1, 2, 7), ch.map { it.idx })
        assertTrue(ch[0].lines.contains("wait: 2 → 5"))
        assertTrue(ch[0].lines.any { it.startsWith("− itr:") })
        assertTrue(ch[0].lines.any { it.startsWith("+ itr:") && it.contains("w: 9") })
        assertEquals(0, Diff.frames(orig, orig.replace("\n\n", "\n\n\n")).size) // spasi saja bukan perubahan
    }

    @Test
    fun diffRestoreEachKind() {
        var now = orig.replace("wait: 2  next: 2", "wait: 5  next: 2")
        now = now.replace(fr(2, "punch_2", 999), "")
        now += "\n\n" + fr(7, "baru", 999)
        val ch = Diff.frames(orig, now)
        var t = now
        // pulihkan dari belakang agar posisi tidak bergeser
        for (c in ch.sortedByDescending { it.idx }) {
            val e = Diff.restore(t, orig, c)
            assertNotNull(e)
            t = t.substring(0, e!!.s) + e.text + t.substring(e.e)
        }
        assertEquals(0, Diff.frames(orig, t).size)
        assertEquals(listOf(0, 1, 2), FrameOps.ranges(t).map { it.idx })
    }

    @Test
    fun headerChangesListed() {
        val h = "<bmp_begin>\nname: Davis\nwalking_speed 4.0\n<bmp_end>\n\n" + fr(0, "a", 999)
        val h2 = h.replace("Davis", "Zero").replace("4.0", "5.0")
        assertEquals(listOf("name: Davis → Zero", "walking_speed: 4.0 → 5.0"), Diff.headerChanges(h, h2))
        assertTrue(Diff.headerChanges(h, h).isEmpty())
        assertNull(Diff.restore(h, h, Diff.frames(h, h).firstOrNull() ?: com.lf2ide.mobile.core.Change('~', 99, "", 0, emptyList())))
    }

    // ---------- VersionStore ----------
    @Test
    fun versionStoreKeepsOriginalAndPrunes() {
        val dir = Files.createTempDirectory("lf2hist").toFile()
        val vs = VersionStore(dir)
        vs.saveOriginal("a|1", "asli")
        vs.saveOriginal("a|1", "diganti?")                 // tidak menimpa
        assertEquals("asli", vs.original("a|1"))
        vs.setOriginal("a|1", "asli2")
        assertEquals("asli2", vs.original("a|1"))
        assertNull(vs.original("b|2"))
        vs.add("a|1", "v1")
        vs.add("a|1", "v1")                                // sama dengan terakhir -> dilewati
        vs.add("a|1", "v2")
        val l = vs.list("a|1")
        assertEquals(2, l.size)
        assertEquals("v2", vs.read(l[0].second))
        for (i in 0 until 40) vs.add("a|1", "x$i")
        assertEquals(25, vs.list("a|1").size)
        dir.deleteRecursively()
    }
}
