package com.lf2ide.mobile

import com.lf2ide.mobile.core.BgDoc
import com.lf2ide.mobile.core.Edit
import com.lf2ide.mobile.core.StageDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Tahap3Test {
    private fun ap(t: String, e: Edit?): String = if (e == null) t else t.substring(0, e.s) + e.text + t.substring(e.e)

    private val stage = "<stage> id: 50  #Survival Stage\n" +
        "        <phase> bound: 1500  # phase 0   bandit\n" +
        "                music: bgm\\stage4.wma\n" +
        "                id: 122  x: 100  #milk  ratio: 1\n" +
        "                id: 33  hp: 150 ratio: .3 <boss>\n" +
        "        <phase_end>\n\n" +
        "        <phase> bound: 2200\n" +
        "                id: 3000 hp: 50  <soldier>\n" +
        "        <phase_end>\n" +
        "<stage_end>\n<end>\n"

    @Test
    fun stageParse() {
        val s = StageDoc.parse(stage)
        assertEquals(50, s.id)
        assertEquals(2, s.phases.size)
        val p = s.phases[0]
        assertEquals(1500, p.bound)
        assertEquals("bgm\\stage4.wma", p.music)
        assertEquals("phase 0   bandit", p.comment)
        assertEquals(2, p.enemies.size)
        assertEquals(122, p.enemies[0].id)
        assertEquals(100, p.enemies[0].x)
        assertEquals("milk  ratio: 1", p.enemies[0].comment) // ratio di dalam komentar tidak terbaca
        assertNull(p.enemies[0].get("ratio"))
        assertTrue(p.enemies[1].boss)
        assertEquals(".3", p.enemies[1].get("ratio"))
        assertEquals(2200, s.phases[1].bound)
        assertTrue(s.phases[1].enemies[0].soldier)
    }

    @Test
    fun stageEnemyEdits() {
        var t = stage
        var s = StageDoc.parse(t)
        // ubah hp (sudah ada), hapus ratio, tambah times
        t = ap(t, StageDoc.setEnemy(s.phases[0].enemies[1], "hp", "99")); s = StageDoc.parse(t)
        assertEquals("99", s.phases[0].enemies[1].get("hp"))
        assertTrue(s.phases[0].enemies[1].boss)
        t = ap(t, StageDoc.setEnemy(s.phases[0].enemies[1], "ratio", null)); s = StageDoc.parse(t)
        assertNull(s.phases[0].enemies[1].get("ratio"))
        assertTrue(s.phases[0].enemies[1].boss)
        t = ap(t, StageDoc.setEnemy(s.phases[0].enemies[1], "times", "2")); s = StageDoc.parse(t)
        assertEquals("2", s.phases[0].enemies[1].get("times"))
        assertTrue(s.phases[0].enemies[1].boss)
        // komentar tetap utuh saat mengubah baris yang punya komentar
        t = ap(t, StageDoc.setEnemy(s.phases[0].enemies[0], "x", "250")); s = StageDoc.parse(t)
        assertEquals(250, s.phases[0].enemies[0].x)
        assertEquals("milk  ratio: 1", s.phases[0].enemies[0].comment)
        // tag
        t = ap(t, StageDoc.setEnemyTag(s.phases[0].enemies[0], "<boss>", true)); s = StageDoc.parse(t)
        assertTrue(s.phases[0].enemies[0].boss)
        assertEquals("milk  ratio: 1", s.phases[0].enemies[0].comment)
        t = ap(t, StageDoc.setEnemyTag(s.phases[0].enemies[0], "<boss>", false)); s = StageDoc.parse(t)
        assertTrue(!s.phases[0].enemies[0].boss)
    }

    @Test
    fun stageAddCopyDeleteEnemy() {
        var t = stage
        var s = StageDoc.parse(t)
        t = ap(t, StageDoc.addEnemy(t, s.phases[1])); s = StageDoc.parse(t)
        assertEquals(2, s.phases[1].enemies.size)
        assertEquals(30, s.phases[1].enemies[1].id)
        t = ap(t, StageDoc.copyEnemy(s.phases[1].enemies[0])); s = StageDoc.parse(t)
        assertEquals(3, s.phases[1].enemies.size)
        assertEquals(3000, s.phases[1].enemies[1].id)
        t = ap(t, StageDoc.deleteEnemy(t, s.phases[1].enemies[1])); s = StageDoc.parse(t)
        assertEquals(2, s.phases[1].enemies.size)
        assertEquals(2, s.phases.size) // phase lain tidak terganggu
        assertEquals(1500, s.phases[0].bound)
    }

    @Test
    fun stagePhaseOps() {
        var t = stage
        var s = StageDoc.parse(t)
        t = ap(t, StageDoc.setPhaseField(t, s.phases[1], "bound", "3000")); s = StageDoc.parse(t)
        assertEquals(3000, s.phases[1].bound)
        t = ap(t, StageDoc.setPhaseField(t, s.phases[1], "music", "bgm\\x.wma")); s = StageDoc.parse(t)
        assertEquals("bgm\\x.wma", s.phases[1].music)
        assertEquals(1, s.phases[1].enemies.size)
        t = ap(t, StageDoc.copyPhase(t, s.phases[0])); s = StageDoc.parse(t)
        assertEquals(3, s.phases.size)
        assertEquals(2, s.phases[1].enemies.size)
        t = ap(t, StageDoc.movePhase(t, s, 2, -1)); s = StageDoc.parse(t)
        assertEquals(listOf(1500, 3000, 1500).sorted(), s.phases.map { it.bound!! }.sorted())
        assertEquals(3000, s.phases[1].bound)
        t = ap(t, StageDoc.deletePhase(t, s.phases[1])); s = StageDoc.parse(t)
        assertEquals(2, s.phases.size)
        t = ap(t, StageDoc.addPhase(t)); s = StageDoc.parse(t)
        assertEquals(3, s.phases.size)
        assertEquals(1500, s.phases[2].bound)
        assertTrue(t.contains("<stage_end>") && t.indexOf("<stage_end>") > t.lastIndexOf("<phase_end>"))
        t = ap(t, StageDoc.setStageId(t, 77)); assertEquals(77, StageDoc.parse(t).id)
    }

    // ---------- BG ----------
    private val bg = "name: Template1\nwidth: 1600   zboundary: 370 510   \nshadow: bg\\t\\shadow.bmp  shadowsize: 37 9\n\n" +
        "layer:\n  bg\\t\\pic1.bmp\n  transparency: 0 width: 967  x: 0  y: 128\nlayer_end\n\n" +
        "layer:\n  bg\\t\\pic2.bmp   # komentar\n  transparency: 1 width: 1000  x: 500  y: 200 loop: 3\nlayer_end\n"

    @Test
    fun bgParse() {
        val b = BgDoc.parse(bg)
        assertEquals("Template1", b.get("name"))
        assertEquals(1600, b.int("width"))
        assertEquals("370 510", b.get("zboundary"))
        assertEquals("bg\\t\\shadow.bmp", b.get("shadow"))
        assertEquals("37 9", b.get("shadowsize"))
        assertEquals(2, b.layers.size)
        val l = b.layers[1]
        assertEquals("bg\\t\\pic2.bmp", l.path)
        assertEquals(500, l.int("x"))
        assertEquals(3, l.int("loop"))
        assertEquals(1, l.int("transparency"))
    }

    @Test
    fun bgEdits() {
        var t = bg
        var b = BgDoc.parse(t)
        t = ap(t, BgDoc.setHeader(t, b, "width", "2000")); b = BgDoc.parse(t)
        assertEquals(2000, b.int("width"))
        assertEquals("370 510", b.get("zboundary"))
        t = ap(t, BgDoc.setHeader(t, b, "zboundary", "300 400")); b = BgDoc.parse(t)
        assertEquals("300 400", b.get("zboundary"))
        t = ap(t, BgDoc.setHeader(t, b, "left", "10")); b = BgDoc.parse(t)
        assertEquals("10", b.get("left"))
        assertEquals(2, b.layers.size)
        t = ap(t, BgDoc.setLayerAttr(t, b.layers[0], "x", "42")); b = BgDoc.parse(t)
        assertEquals(42, b.layers[0].int("x"))
        assertEquals(967, b.layers[0].int("width"))
        t = ap(t, BgDoc.setLayerAttr(t, b.layers[0], "loop", "2")); b = BgDoc.parse(t)
        assertEquals(2, b.layers[0].int("loop"))
        t = ap(t, BgDoc.setLayerAttr(t, b.layers[1], "loop", null)); b = BgDoc.parse(t)
        assertNull(b.layers[1].get("loop"))
        assertEquals(200, b.layers[1].int("y"))
        t = ap(t, BgDoc.setLayerPath(b.layers[1], "bg\\t\\new.bmp")); b = BgDoc.parse(t)
        assertEquals("bg\\t\\new.bmp", b.layers[1].path)
        assertNull(BgDoc.setLayerAttr(t, b.layers[0], "cc", null))
    }

    @Test
    fun bgLayerOps() {
        var t = bg
        var b = BgDoc.parse(t)
        t = ap(t, BgDoc.addLayer(t, b)); b = BgDoc.parse(t)
        assertEquals(3, b.layers.size)
        assertEquals(1600, b.header.firstOrNull { it.key == "width" }?.value?.toInt())
        t = ap(t, BgDoc.copyLayer(t, b.layers[0])); b = BgDoc.parse(t)
        assertEquals(4, b.layers.size)
        assertEquals("bg\\t\\pic1.bmp", b.layers[1].path)
        t = ap(t, BgDoc.moveLayer(t, b, 1, 1)); b = BgDoc.parse(t)
        assertEquals("bg\\t\\pic2.bmp", b.layers[1].path)
        assertEquals("bg\\t\\pic1.bmp", b.layers[2].path)
        t = ap(t, BgDoc.deleteLayer(t, b.layers[2])); b = BgDoc.parse(t)
        assertEquals(3, b.layers.size)
        assertNull(BgDoc.moveLayer(t, b, 0, -1))
        assertNotNull(BgDoc.moveLayer(t, b, 0, 1))
    }
}
