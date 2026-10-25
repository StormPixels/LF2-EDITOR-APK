package com.lf2ide.mobile.core

class DataSyntaxError(msg: String) : Exception(msg)

/** Port dari LF2DataUtils.GetFrames / TokenizeFrame */
object Tokenizer {
    fun getFrames(dat: String): List<String> {
        val out = ArrayList<String>()
        var i = dat.indexOf("<frame>")
        while (i >= 0) {
            val j = dat.indexOf("<frame_end>", i + 7)
            if (j < 0) return out
            out.add(dat.substring(i, j + 11))
            i = dat.indexOf("<frame>", i + 7)
        }
        return out
    }

    fun tokenize(frame: String): List<String> {
        val t = ArrayList<String>()
        var inTok = false
        var start = 0
        for (i in frame.indices) {
            val c = frame[i]
            if (c == '<') {
                if (inTok) t.add(frame.substring(start, i))
                start = i
                inTok = true
            } else if (c == '>' || c == ':') {
                if (!inTok) throw DataSyntaxError("Syntax Error: Encountered token ending delimeter")
                t.add(frame.substring(start, i + 1))
                inTok = false
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                if (inTok) {
                    t.add(frame.substring(start, i))
                    inTok = false
                }
            } else if (!inTok) {
                start = i
                inTok = true
            }
        }
        return t
    }
}
