package com.lf2ide.mobile

import com.lf2ide.mobile.core.Crypto
import com.lf2ide.mobile.core.FrameParser
import com.lf2ide.mobile.core.FrameWriter
import com.lf2ide.mobile.core.Sprites
import com.lf2ide.mobile.core.Tokenizer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CoreTest {
    private fun tpl(n: String) = File("src/main/assets/templates/$n.dat").readText(Charsets.ISO_8859_1)

    @Test
    fun cryptoRoundtrip() {
        val t = "name: Test\r\n<frame> 0 standing\n   pic: 0  state: 0\n<frame_end>"
        assertEquals(t, Crypto.decrypt(Crypto.encrypt(t)))
    }

    @Test
    fun templatesRoundtrip() {
        var total = 0
        for (n in listOf("Char", "Weapon", "Stage", "Attack")) {
            for (raw in Tokenizer.getFrames(tpl(n))) {
                val f = FrameParser.read(raw)
                assertNotNull("$n: frame gagal dibaca", f)
                val s1 = FrameWriter.frameToString(f!!)
                val s2 = FrameWriter.frameToString(FrameParser.read(s1)!!)
                assertEquals("$n frame ${f.index}", s1, s2)
                total++
            }
        }
        assertEquals(251, total) // sama dengan hasil tes versi JS
    }

    @Test
    fun spriteCell() {
        // file(0-69): ... w: 79 h: 79 row: 10 col: 7  -> 10 sprite ke samping, 7 baris ke bawah
        val sheets = Sprites.parseSheets(tpl("Char"))
        val c = Sprites.cellFor(sheets, 0)!!
        assertEquals("bandit_0.bmp", c.s.name)
        assertEquals(0, c.sx)
        assertEquals(0, c.sy)
        val d = Sprites.cellFor(sheets, 12)!! // baris ke-2, kolom ke-3
        assertEquals(160, d.sx)
        assertEquals(80, d.sy)
        val e = Sprites.cellFor(sheets, 69)!! // sel terakhir: kolom ke-10, baris ke-7
        assertEquals(720, e.sx)
        assertEquals(480, e.sy)
    }

    @Test
    fun spriteContinuesAcrossSheets() {
        val head = "file(0-119): custom\\1genma\\genma.png w: 79 h: 79 row: 10 col: 12\n" +
            "file(120-239): custom\\1genma\\genma2.png w: 79 h: 79 row: 10 col: 12"
        val sheets = Sprites.parseSheets(head)
        val a = Sprites.cellFor(sheets, 119)!!
        assertEquals("genma.png", a.s.name)
        assertEquals(720, a.sx)
        assertEquals(880, a.sy)
        val b = Sprites.cellFor(sheets, 120)!! // lanjutan: kembali ke sel pertama lembar ke-2
        assertEquals("genma2.png", b.s.name)
        assertEquals(0, b.sx)
        assertEquals(0, b.sy)
    }
}
