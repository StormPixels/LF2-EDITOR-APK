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
        val sheets = Sprites.parseSheets(tpl("Char"))
        val c = Sprites.cellFor(sheets, 0)!!
        assertEquals("bandit_0.bmp", c.s.name)
        assertEquals(0, c.sx)
        val d = Sprites.cellFor(sheets, 8)!! // kolom 1, baris 1 (col=7)
        assertEquals(80, d.sx)
        assertEquals(80, d.sy)
    }
}
