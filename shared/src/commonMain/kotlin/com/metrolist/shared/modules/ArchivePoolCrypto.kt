// Pure-Kotlin AES-256-GCM & wire helpers for ArchivePool v2 (works on Android, Desktop, and iOS).
// Based on Natune's ArchivePool wire protocol (GPL-3.0-or-later).

package com.metrolist.shared.modules

import com.metrolist.shared.deviceconnect.DeviceCrypto
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal object ArchivePoolCrypto {
    private const val MAX_ENVELOPE_BYTES = 1_048_576

    private val sBox: IntArray by lazy {
        IntArray(256) { x ->
            var y = if (x == 0) 0 else 1
            if (x != 0) {
                repeat(254) { y = gfMulByte(y, x) }
            }
            val r1 = ((y shl 1) or (y ushr 7)) and 0xFF
            val r2 = ((y shl 2) or (y ushr 6)) and 0xFF
            val r3 = ((y shl 3) or (y ushr 5)) and 0xFF
            val r4 = ((y shl 4) or (y ushr 4)) and 0xFF
            (y xor r1 xor r2 xor r3 xor r4 xor 0x63) and 0xFF
        }
    }

    fun derivePoolKey(readKey: String): ByteArray = DeviceCrypto.sha256("archivepool-client:$readKey".encodeToByteArray())

    /**
     * Decrypts an ArchivePool `enc:1:<ivB64>:<ciphertextAndTagB64>` field using AES-256-GCM.
     * Plaintext fields without the `enc:1:` prefix pass through unchanged.
     */
    fun decryptField(
        value: String?,
        key: ByteArray,
    ): String? {
        if (value == null) return null
        if (!value.startsWith("enc:1:")) return value
        val parts = value.split(":")
        require(parts.size == 4) { "Invalid ArchivePool encrypted field" }
        val iv = decodeBase64Flex(parts[2])
        val sealed = decodeBase64Flex(parts[3])
        return decryptAes256Gcm(key, iv, sealed).decodeToString()
    }

    /**
     * Authenticated AES-256-GCM decryption (96-bit IV, 128-bit tag, empty AAD).
     * Verifies the GHASH authentication tag in constant time before returning plaintext.
     */
    fun decryptAes256Gcm(
        key: ByteArray,
        iv: ByteArray,
        sealed: ByteArray,
    ): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        require(iv.size == 12 && sealed.size in 16..MAX_ENVELOPE_BYTES) {
            "Invalid ArchivePool AES-GCM envelope"
        }

        val expanded = expandAes256Key(key)
        val ctLen = sealed.size - 16
        val hBlock = aes256EncryptBlock(ByteArray(16), expanded)
        val hHi = readBigEndianLong(hBlock, 0)
        val hLo = readBigEndianLong(hBlock, 8)

        var authHi = 0L
        var authLo = 0L
        var offset = 0
        val block = ByteArray(16)
        while (offset < ctLen) {
            block.fill(0)
            val take = minOf(16, ctLen - offset)
            sealed.copyInto(block, 0, offset, offset + take)
            val xHi = authHi xor readBigEndianLong(block, 0)
            val xLo = authLo xor readBigEndianLong(block, 8)
            val prod = gcmMul128(xHi, xLo, hHi, hLo)
            authHi = prod.first
            authLo = prod.second
            offset += 16
        }

        // Final length block: 64-bit AAD bit length (0) || 64-bit ciphertext bit length.
        val lenProd = gcmMul128(authHi, authLo xor (ctLen.toLong() * 8L), hHi, hLo)
        authHi = lenProd.first
        authLo = lenProd.second

        val counter = ByteArray(16)
        iv.copyInto(counter, 0, 0, 12)
        counter[15] = 1
        val j0Mask = aes256EncryptBlock(counter, expanded)
        val expectedHi = authHi xor readBigEndianLong(j0Mask, 0)
        val expectedLo = authLo xor readBigEndianLong(j0Mask, 8)
        val tagHi = readBigEndianLong(sealed, ctLen)
        val tagLo = readBigEndianLong(sealed, ctLen + 8)
        require(((expectedHi xor tagHi) or (expectedLo xor tagLo)) == 0L) {
            "ArchivePool authentication failed; verify your read key"
        }

        val plain = ByteArray(ctLen)
        var pos = 0
        while (pos < ctLen) {
            incrementCounter32(counter)
            val keystream = aes256EncryptBlock(counter, expanded)
            val take = minOf(16, ctLen - pos)
            for (j in 0 until take) {
                plain[pos + j] = (sealed[pos + j].toInt() xor keystream[j].toInt()).toByte()
            }
            pos += 16
        }
        return plain
    }

    private fun incrementCounter32(counter: ByteArray) {
        for (i in 15 downTo 12) {
            val next = (counter[i].toInt() and 0xFF) + 1
            counter[i] = (next and 0xFF).toByte()
            if ((next and 0xFF) != 0) break
        }
    }

    private fun expandAes256Key(key: ByteArray): IntArray {
        val k = IntArray(240)
        for (i in 0 until 32) k[i] = key[i].toInt() and 0xFF
        var rcon = 1
        var at = 32
        while (at < 240) {
            var t0 = k[at - 4]
            var t1 = k[at - 3]
            var t2 = k[at - 2]
            var t3 = k[at - 1]
            if (at % 32 == 0) {
                val n0 = sBox[t1] xor rcon
                val n1 = sBox[t2]
                val n2 = sBox[t3]
                val n3 = sBox[t0]
                t0 = n0
                t1 = n1
                t2 = n2
                t3 = n3
                rcon = xtime(rcon)
            } else if (at % 32 == 16) {
                t0 = sBox[t0]
                t1 = sBox[t1]
                t2 = sBox[t2]
                t3 = sBox[t3]
            }
            k[at] = k[at - 32] xor t0
            k[at + 1] = k[at - 31] xor t1
            k[at + 2] = k[at - 30] xor t2
            k[at + 3] = k[at - 29] xor t3
            at += 4
        }
        return k
    }

    private fun aes256EncryptBlock(
        input: ByteArray,
        k: IntArray,
    ): ByteArray {
        val s = IntArray(16) { (input[it].toInt() and 0xFF) xor k[it] }
        val tmp = IntArray(16)
        for (r in 1..14) {
            for (i in 0 until 16) s[i] = sBox[s[i]]
            s.copyInto(tmp)
            for (row in 0 until 4) {
                for (col in 0 until 4) {
                    s[row + 4 * col] = tmp[row + 4 * ((col + row) % 4)]
                }
            }
            if (r != 14) {
                var c = 0
                while (c < 16) {
                    val a = s[c]
                    val b = s[c + 1]
                    val d = s[c + 2]
                    val e = s[c + 3]
                    val v = a xor b xor d xor e
                    s[c] = a xor v xor xtime(a xor b)
                    s[c + 1] = b xor v xor xtime(b xor d)
                    s[c + 2] = d xor v xor xtime(d xor e)
                    s[c + 3] = e xor v xor xtime(e xor a)
                    c += 4
                }
            }
            val base = r * 16
            for (i in 0 until 16) s[i] = s[i] xor k[base + i]
        }
        return ByteArray(16) { s[it].toByte() }
    }

    /** Multiplication in GF(2^128) with polynomial x^128 + x^7 + x^2 + x + 1. */
    private fun gcmMul128(
        xHi: Long,
        xLo: Long,
        yHi: Long,
        yLo: Long,
    ): Pair<Long, Long> {
        var zHi = 0L
        var zLo = 0L
        var vHi = yHi
        var vLo = yLo
        for (bit in 0 until 128) {
            val set =
                if (bit < 64) {
                    ((xHi ushr (63 - bit)) and 1L) != 0L
                } else {
                    ((xLo ushr (127 - bit)) and 1L) != 0L
                }
            if (set) {
                zHi = zHi xor vHi
                zLo = zLo xor vLo
            }
            val lsb = (vLo and 1L) != 0L
            vLo = (vLo ushr 1) or (vHi shl 63)
            vHi = vHi ushr 1
            if (lsb) vHi = vHi xor (0xE1L shl 56)
        }
        return zHi to zLo
    }

    private fun readBigEndianLong(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        }
        return result
    }

    private fun xtime(x: Int): Int = ((x shl 1) xor (if ((x and 0x80) != 0) 0x11B else 0)) and 0xFF

    private fun gfMulByte(
        a0: Int,
        b0: Int,
    ): Int {
        var a = a0
        var b = b0
        var r = 0
        repeat(8) {
            if ((b and 1) != 0) r = r xor a
            a = xtime(a)
            b = b ushr 1
        }
        return r
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeBase64Flex(input: String): ByteArray {
    val clean =
        input
            .trim()
            .replace('-', '+')
            .replace('_', '/')
            .filterNot { it.isWhitespace() }
    val padLen = (4 - clean.length % 4) % 4
    return Base64.Default.decode(clean + "=".repeat(padLen))
}

internal fun urlEncodeComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val out = StringBuilder(bytes.size * 2)
    val hex = "0123456789ABCDEF"
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        val unreserved =
            c in 'A'.code..'Z'.code ||
                c in 'a'.code..'z'.code ||
                c in '0'.code..'9'.code ||
                c == '-'.code ||
                c == '_'.code ||
                c == '.'.code ||
                c == '~'.code
        if (unreserved) {
            out.append(c.toChar())
        } else {
            out.append('%')
            out.append(hex[c ushr 4])
            out.append(hex[c and 0x0F])
        }
    }
    return out.toString()
}

internal fun normalizeWebBaseUrl(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "https://$text"
    val schemeEnd = text.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = text.substring(0, schemeEnd).lowercase()
    if (scheme != "https" && scheme != "http") return null
    val afterScheme = text.substring(schemeEnd + 3).substringBefore('#').substringBefore('?')
    val hostPart = afterScheme.substringBefore('/')
    if (hostPart.isBlank() || '@' in hostPart) return null
    val pathPart =
        afterScheme
            .substringAfter('/', "")
            .replace(Regex("/?[^/]*\\.json$"), "")
            .trimEnd('/')
    return if (pathPart.isEmpty()) "$scheme://$hostPart" else "$scheme://$hostPart/$pathPart"
}

internal fun extractHostName(url: String): String =
    url
        .substringAfter("://", url)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore(':')
        .lowercase()
