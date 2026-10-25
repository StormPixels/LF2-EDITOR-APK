package com.lf2ide.mobile.core

/** Port dari UnsafeLF2DataAlgorithm / LF2DataUtils.Encrypt+Decrypt */
object Crypto {
    const val DEFAULT_KEY = "odBearBecauseHeIsVeryGoodSiuHungIsAGo"
    private const val HEADER = 123

    fun decrypt(bytes: ByteArray, key: String = DEFAULT_KEY): String {
        if (key.isEmpty()) return String(bytes, Charsets.ISO_8859_1)
        val n = maxOf(0, bytes.size - HEADER)
        val out = ByteArray(n)
        for (i in 0 until n) out[i] = (bytes[HEADER + i] - key[i % key.length].code).toByte()
        return String(out, Charsets.ISO_8859_1)
    }

    fun encrypt(text: String, key: String = DEFAULT_KEY): ByteArray {
        val out = ByteArray(HEADER + text.length)
        for (i in text.indices) {
            val k = if (key.isEmpty()) 0 else key[i % key.length].code
            out[HEADER + i] = ((text[i].code + k) and 255).toByte()
        }
        return out
    }
}
