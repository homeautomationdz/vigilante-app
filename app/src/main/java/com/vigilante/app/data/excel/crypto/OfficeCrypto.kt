package com.vigilante.app.data.excel.crypto

import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory

/*
 * ECMA-376 document encryption (MS-OFFCRYPTO), implemented with nothing but
 * javax.crypto so it runs on Android.
 *
 *  • WRITE  → "Standard Encryption" (AES-128 ECB, SHA-1, 50 000 spins) — this
 *    is what Excel's own "Encrypt with Password" produced for years and every
 *    version of Excel/LibreOffice can open it.
 *  • READ   → Standard *and* Agile encryption, because Excel 2016+ re-encrypts
 *    with Agile when a supervisor edits the exported file and saves it. Both
 *    paths must import cleanly.
 */

internal object StandardCrypto {

    private const val SPIN_COUNT = 50_000
    private const val KEY_BITS = 128
    private const val ALG_ID_AES128 = 0x660E
    private const val ALG_ID_HASH_SHA1 = 0x8004
    private const val PROVIDER_AES = 0x18
    private const val CSP_NAME = "Microsoft Enhanced RSA and AES Cryptographic Provider"

    /** Parsed EncryptionVerifier + header fields we care about. */
    private data class Info(
        val keyBits: Int,
        val salt: ByteArray,
        val encryptedVerifier: ByteArray,
        val encryptedVerifierHash: ByteArray
    )

    fun buildEncryptionInfo(salt: ByteArray, verifier: ByteArray, key: ByteArray): ByteArray {
        val cspBytes = CSP_NAME.toByteArray(Charsets.UTF_16LE)
        val headerSize = 32 + cspBytes.size + 2

        val cipher = ecb(key, Cipher.ENCRYPT_MODE)
        val encryptedVerifier = cipher.doFinal(verifier)
        val verifierHash = MessageDigest.getInstance("SHA-1").digest(verifier)
        val encryptedVerifierHash = cipher.doFinal(verifierHash.copyOf(32))

        val out = ByteArray(8 + headerSize + 4 + 16 + 16 + 4 + 32)
        var p = 0
        out.putLe16(p, 4); p += 2                    // versionMajor
        out.putLe16(p, 2); p += 2                    // versionMinor  → Standard
        out.putLe32(p, 0x24); p += 4                 // fCryptoAPI | fAES
        out.putLe32(p, headerSize); p += 4

        out.putLe32(p, 0x24); p += 4                 // header flags
        out.putLe32(p, 0); p += 4                    // sizeExtra
        out.putLe32(p, ALG_ID_AES128); p += 4
        out.putLe32(p, ALG_ID_HASH_SHA1); p += 4
        out.putLe32(p, KEY_BITS); p += 4
        out.putLe32(p, PROVIDER_AES); p += 4
        out.putLe32(p, 0); p += 4                    // reserved1
        out.putLe32(p, 0); p += 4                    // reserved2
        cspBytes.copyInto(out, p); p += cspBytes.size
        out.putLe16(p, 0); p += 2                    // UTF-16 null terminator

        out.putLe32(p, salt.size); p += 4
        salt.copyInto(out, p); p += 16
        encryptedVerifier.copyInto(out, p); p += 16
        out.putLe32(p, 20); p += 4                   // SHA-1 hash size
        encryptedVerifierHash.copyInto(out, p); p += 32
        check(p == out.size) { "بنية EncryptionInfo غير متسقة" }
        return out
    }

    private fun parse(info: ByteArray): Info {
        val headerSize = info.le32(8)
        var p = 12
        val keyBits = info.le32(p + 16)
        p += headerSize                              // skip to EncryptionVerifier
        val saltSize = info.le32(p); p += 4
        require(saltSize == 16) { "حجم الملح غير مدعوم: $saltSize" }
        val salt = info.copyOfRange(p, p + 16); p += 16
        val encVerifier = info.copyOfRange(p, p + 16); p += 16
        val verifierHashSize = info.le32(p); p += 4
        val encHashLen = if (verifierHashSize <= 20) 32 else verifierHashSize
        val encVerifierHash = info.copyOfRange(p, minOf(p + encHashLen, info.size))
        return Info(keyBits, salt, encVerifier, encVerifierHash)
    }

    /** MS-OFFCRYPTO 2.3.4.7 — password → AES key. */
    fun deriveKey(password: String, salt: ByteArray, keyBits: Int): ByteArray {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(salt)
        var h = sha1.digest(password.toByteArray(Charsets.UTF_16LE))
        val counter = ByteArray(4)
        for (i in 0 until SPIN_COUNT) {
            counter.putLe32(0, i)
            sha1.reset()
            sha1.update(counter)
            sha1.update(h)
            h = sha1.digest()
        }
        counter.putLe32(0, 0)                        // block key 0
        sha1.reset()
        sha1.update(h)
        h = sha1.digest(counter)

        val x1 = sha1Xor(h, 0x36)
        val x2 = sha1Xor(h, 0x5C)
        return (x1 + x2).copyOf(keyBits / 8)
    }

    private fun sha1Xor(hash: ByteArray, fill: Int): ByteArray {
        val buf = ByteArray(64) { fill.toByte() }
        for (i in hash.indices) buf[i] = (buf[i].toInt() xor hash[i].toInt()).toByte()
        return MessageDigest.getInstance("SHA-1").digest(buf)
    }

    private fun ecb(key: ByteArray, mode: Int): Cipher =
        Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"))
        }

    /** Encrypts [plain] into a password-protected Office container at [target]. */
    fun encrypt(plain: File, target: File, password: String) {
        val plainLen = plain.length()
        require(plainLen > 0) { "الملف المراد تشفيره فارغ" }
        val encLen = ((plainLen + 15) / 16) * 16

        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val verifier = ByteArray(16).also { random.nextBytes(it) }
        val key = deriveKey(password, salt, KEY_BITS)
        val info = buildEncryptionInfo(salt, verifier, key)

        val tmp = File(target.parentFile, target.name + ".part")
        try {
            CfbWriter.write(tmp, info, 8 + encLen) { out ->
                val prefix = ByteArray(8)
                prefix.putLe64(0, plainLen)
                out.write(prefix)
                val cipher = ecb(key, Cipher.ENCRYPT_MODE)
                FileInputStream(plain).use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.readAtLeastOnce(buf)
                        if (n <= 0) break
                        val block = if (n % 16 == 0) n else ((n / 16) + 1) * 16
                        if (block > n) java.util.Arrays.fill(buf, n, block, 0)
                        out.write(cipher.update(buf, 0, block) ?: ByteArray(0))
                    }
                }
                cipher.doFinal()?.let { if (it.isNotEmpty()) out.write(it) }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun decrypt(cfb: CfbReader, info: ByteArray, password: String, target: File) {
        val parsed = parse(info)
        val key = deriveKey(password, parsed.salt, parsed.keyBits)
        val dec = ecb(key, Cipher.DECRYPT_MODE)
        val verifier = dec.doFinal(parsed.encryptedVerifier)
        val storedHash = dec.doFinal(parsed.encryptedVerifierHash)
        val calc = MessageDigest.getInstance("SHA-1").digest(verifier)
        check(calc.contentEquals(storedHash.copyOf(calc.size))) { "كلمة مرور ملف Excel غير صحيحة" }

        val cipher = ecb(key, Cipher.DECRYPT_MODE)
        cfb.openStream(CfbConst.ENCRYPTED_PACKAGE).use { input ->
            val head = ByteArray(8)
            input.readFullyOrFail(head)
            var remaining = head.le64(0)
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val n = input.readAtLeastOnce(buf)
                    if (n <= 0) break
                    val block = (n / 16) * 16
                    if (block == 0) break
                    val plain = cipher.update(buf, 0, block) ?: ByteArray(0)
                    val take = minOf(plain.size.toLong(), remaining).toInt()
                    out.write(plain, 0, take)
                    remaining -= take
                }
                out.flush()
            }
        }
    }
}

internal object AgileCrypto {

    private val BLOCK_VERIFIER_INPUT =
        byteArrayOf(0xfe.toByte(), 0xa7.toByte(), 0xd2.toByte(), 0x76, 0x3b, 0x4b, 0x9e.toByte(), 0x79)
    private val BLOCK_VERIFIER_VALUE =
        byteArrayOf(0xd7.toByte(), 0xaa.toByte(), 0x0f, 0x6d, 0x30, 0x61, 0x34, 0x4e)
    private val BLOCK_KEY_VALUE =
        byteArrayOf(0x14, 0x6e, 0x0b, 0xe7.toByte(), 0xab.toByte(), 0xac.toByte(), 0xd0.toByte(), 0xd6.toByte())

    private const val SEGMENT = 4096

    private data class KeyData(
        val salt: ByteArray, val blockSize: Int, val keyBits: Int, val hashAlg: String
    )

    private data class PasswordKey(
        val salt: ByteArray, val blockSize: Int, val keyBits: Int, val hashAlg: String,
        val spinCount: Int, val encVerifierInput: ByteArray, val encVerifierValue: ByteArray,
        val encKeyValue: ByteArray
    )

    fun decrypt(cfb: CfbReader, info: ByteArray, password: String, target: File) {
        val xml = String(info, 8, info.size - 8, Charsets.UTF_8)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            // Imported files are untrusted input — never resolve external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))

        val keyDataEl = doc.findElement("keyData") ?: error("وصف التشفير غير مكتمل (keyData)")
        val encKeyEl = doc.findElement("encryptedKey") ?: error("وصف التشفير غير مكتمل (encryptedKey)")

        val keyData = KeyData(
            salt = b64(keyDataEl.getAttribute("saltValue")),
            blockSize = keyDataEl.getAttribute("blockSize").toInt(),
            keyBits = keyDataEl.getAttribute("keyBits").toInt(),
            hashAlg = jceHash(keyDataEl.getAttribute("hashAlgorithm"))
        )
        val pk = PasswordKey(
            salt = b64(encKeyEl.getAttribute("saltValue")),
            blockSize = encKeyEl.getAttribute("blockSize").toInt(),
            keyBits = encKeyEl.getAttribute("keyBits").toInt(),
            hashAlg = jceHash(encKeyEl.getAttribute("hashAlgorithm")),
            spinCount = encKeyEl.getAttribute("spinCount").toInt(),
            encVerifierInput = b64(encKeyEl.getAttribute("encryptedVerifierHashInput")),
            encVerifierValue = b64(encKeyEl.getAttribute("encryptedVerifierHashValue")),
            encKeyValue = b64(encKeyEl.getAttribute("encryptedKeyValue"))
        )

        val pwHash = hashPassword(password, pk.hashAlg, pk.salt, pk.spinCount)
        val iv = block0(pk.salt, pk.blockSize)

        val verifierInput = cbc(
            derive(pwHash, pk.hashAlg, BLOCK_VERIFIER_INPUT, pk.keyBits / 8),
            iv, Cipher.DECRYPT_MODE
        ).doFinal(pk.encVerifierInput)

        val expected = MessageDigest.getInstance(pk.hashAlg).digest(verifierInput)
        val actual = cbc(
            derive(pwHash, pk.hashAlg, BLOCK_VERIFIER_VALUE, pk.keyBits / 8),
            iv, Cipher.DECRYPT_MODE
        ).doFinal(pk.encVerifierValue)
        check(expected.contentEquals(actual.copyOf(expected.size))) { "كلمة مرور ملف Excel غير صحيحة" }

        val secretKey = cbc(
            derive(pwHash, pk.hashAlg, BLOCK_KEY_VALUE, pk.keyBits / 8),
            iv, Cipher.DECRYPT_MODE
        ).doFinal(pk.encKeyValue).copyOf(keyData.keyBits / 8)

        cfb.openStream(CfbConst.ENCRYPTED_PACKAGE).use { input ->
            val head = ByteArray(8)
            input.readFullyOrFail(head)
            var remaining = head.le64(0)
            val buf = ByteArray(SEGMENT)
            var segment = 0
            FileOutputStream(target).use { out ->
                while (remaining > 0) {
                    val n = input.readFullySegment(buf)
                    if (n <= 0) break
                    val usable = (n / 16) * 16
                    if (usable == 0) break
                    val blockKey = ByteArray(4).also { it.putLe32(0, segment) }
                    val segIv = block0(
                        MessageDigest.getInstance(keyData.hashAlg)
                            .apply { update(keyData.salt) }.digest(blockKey),
                        keyData.blockSize
                    )
                    val plain = cbc(secretKey, segIv, Cipher.DECRYPT_MODE).doFinal(buf, 0, usable)
                    val take = minOf(plain.size.toLong(), remaining).toInt()
                    out.write(plain, 0, take)
                    remaining -= take
                    segment++
                }
                out.flush()
            }
        }
    }

    private fun hashPassword(password: String, alg: String, salt: ByteArray, spin: Int): ByteArray {
        val md = MessageDigest.getInstance(alg)
        md.update(salt)
        var h = md.digest(password.toByteArray(Charsets.UTF_16LE))
        val counter = ByteArray(4)
        for (i in 0 until spin) {
            counter.putLe32(0, i)
            md.reset(); md.update(counter); md.update(h); h = md.digest()
        }
        return h
    }

    private fun derive(pwHash: ByteArray, alg: String, blockKey: ByteArray, keyLen: Int): ByteArray {
        val md = MessageDigest.getInstance(alg)
        md.update(pwHash)
        return block0(md.digest(blockKey), keyLen)
    }

    /** Truncate to [len], or zero-pad with 0x36 as the spec requires. */
    private fun block0(data: ByteArray, len: Int): ByteArray {
        if (data.size >= len) return data.copyOf(len)
        val out = ByteArray(len) { 0x36 }
        data.copyInto(out)
        return out
    }

    private fun cbc(key: ByteArray, iv: ByteArray, mode: Int): Cipher =
        Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }

    private fun b64(s: String): ByteArray = Base64.getDecoder().decode(s)

    private fun jceHash(name: String): String = when (name.uppercase()) {
        "SHA1", "SHA-1" -> "SHA-1"
        "SHA256", "SHA-256" -> "SHA-256"
        "SHA384", "SHA-384" -> "SHA-384"
        "SHA512", "SHA-512" -> "SHA-512"
        else -> error("خوارزمية تجزئة غير مدعومة: $name")
    }

    private fun org.w3c.dom.Document.findElement(localName: String): Element? {
        val all = getElementsByTagName("*")
        for (i in 0 until all.length) {
            val n = all.item(i) as? Element ?: continue
            val tag = n.tagName
            if (tag == localName || tag.endsWith(":$localName")) return n
        }
        return null
    }
}

// ---- small stream helpers ----

internal fun InputStream.readAtLeastOnce(buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
        val n = read(buf, total, buf.size - total)
        if (n <= 0) break
        total += n
    }
    return total
}

/** Fills [buf] completely unless the stream ends (agile needs full 4096 segments). */
internal fun InputStream.readFullySegment(buf: ByteArray): Int = readAtLeastOnce(buf)

internal fun InputStream.readFullyOrFail(buf: ByteArray) {
    val n = readAtLeastOnce(buf)
    check(n == buf.size) { "الملف تالف أو غير مكتمل" }
}
