package com.vigilante.app.data.excel.crypto

import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/*
 * Minimal Compound File Binary (OLE2 / MS-CFB) reader and writer.
 *
 * Why hand-rolled: Apache POI cannot run on Android (it pulls log4j2 and
 * desktop-JVM APIs and dies with NoClassDefFoundError at runtime), yet an
 * encrypted .xlsx that Excel can open MUST be wrapped in a CFB container.
 * This file implements exactly the subset needed for that container — two
 * streams, "EncryptionInfo" (mini stream) and "EncryptedPackage" (FAT chain).
 *
 * Correctness is verified in CI by unit tests that decrypt our output with
 * Apache POI on the JVM (and decrypt POI's output with our reader).
 */

// ---- little-endian helpers ----

internal fun ByteArray.putLe16(off: Int, v: Int) {
    this[off] = (v and 0xFF).toByte()
    this[off + 1] = ((v ushr 8) and 0xFF).toByte()
}

internal fun ByteArray.putLe32(off: Int, v: Int) {
    this[off] = (v and 0xFF).toByte()
    this[off + 1] = ((v ushr 8) and 0xFF).toByte()
    this[off + 2] = ((v ushr 16) and 0xFF).toByte()
    this[off + 3] = ((v ushr 24) and 0xFF).toByte()
}

internal fun ByteArray.putLe64(off: Int, v: Long) {
    for (i in 0 until 8) this[off + i] = ((v ushr (8 * i)) and 0xFF).toByte()
}

internal fun ByteArray.le16(off: Int): Int =
    (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.le32(off: Int): Int =
    (this[off].toInt() and 0xFF) or
        ((this[off + 1].toInt() and 0xFF) shl 8) or
        ((this[off + 2].toInt() and 0xFF) shl 16) or
        ((this[off + 3].toInt() and 0xFF) shl 24)

internal fun ByteArray.le64(off: Int): Long {
    var v = 0L
    for (i in 7 downTo 0) v = (v shl 8) or (this[off + i].toLong() and 0xFF)
    return v
}

internal object CfbConst {
    const val HEADER_SIZE = 512
    const val SECTOR = 512
    const val MINI_SECTOR = 64
    const val MINI_CUTOFF = 4096
    const val DIR_ENTRY_SIZE = 128

    const val FREESECT = -1
    const val ENDOFCHAIN = -2
    const val FATSECT = -3
    const val DIFSECT = -4
    const val NOSTREAM = -1

    const val TYPE_EMPTY = 0
    const val TYPE_STREAM = 2
    const val TYPE_ROOT = 5

    const val ENCRYPTION_INFO = "EncryptionInfo"
    const val ENCRYPTED_PACKAGE = "EncryptedPackage"

    val MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )
}

/** Writes a two-stream CFB container (version 3, 512-byte sectors). */
internal object CfbWriter {

    /**
     * @param encryptionInfo the small "EncryptionInfo" stream (< 4096 bytes)
     * @param packageLength exact byte count [writePackage] will produce
     * @param writePackage streams the "EncryptedPackage" content (8-byte
     *        original-length prefix + ciphertext); never buffered in memory
     */
    fun write(
        target: File,
        encryptionInfo: ByteArray,
        packageLength: Long,
        writePackage: (OutputStream) -> Unit
    ) {
        require(encryptionInfo.isNotEmpty()) { "EncryptionInfo فارغ" }
        require(encryptionInfo.size < CfbConst.MINI_CUTOFF) { "EncryptionInfo أكبر من المتوقع" }
        require(packageLength > 0) { "حزمة البيانات فارغة" }

        val sector = CfbConst.SECTOR
        val entriesPerFat = sector / 4          // 128
        val entriesPerDifat = entriesPerFat - 1 // 127

        // MS-CFB: a stream shorter than the cutoff MUST live in the mini stream.
        // A nearly-empty database exports to well under 4 KB, so this path is real.
        val smallPackage: ByteArray? = if (packageLength < CfbConst.MINI_CUTOFF) {
            val bos = java.io.ByteArrayOutputStream(packageLength.toInt())
            writePackage(bos)
            check(bos.size().toLong() == packageLength) {
                "حجم الحزمة غير مطابق: ${bos.size()} بدل $packageLength"
            }
            bos.toByteArray()
        } else null

        val infoMini = ceilDiv(encryptionInfo.size.toLong(), CfbConst.MINI_SECTOR.toLong()).toInt()
        val pkgMini = if (smallPackage != null) {
            ceilDiv(packageLength, CfbConst.MINI_SECTOR.toLong()).toInt()
        } else 0
        val nMini = infoMini + pkgMini
        val miniStreamLen = nMini * CfbConst.MINI_SECTOR
        val nMiniStreamBig = ceilDiv(miniStreamLen.toLong(), sector.toLong()).toInt()
        val nMiniFat = ceilDiv(nMini.toLong(), entriesPerFat.toLong()).toInt()
        val nPkg = if (smallPackage == null) ceilDiv(packageLength, sector.toLong()).toInt() else 0
        val nDir = 1

        val base = nDir + nMiniFat + nMiniStreamBig + nPkg

        // FAT sectors must also cover themselves and the DIFAT sectors.
        var nFat = 1
        var nDifat = 0
        while (true) {
            val total = base + nFat + nDifat
            val needFat = ceilDiv(total.toLong(), entriesPerFat.toLong()).toInt().coerceAtLeast(1)
            val needDifat = if (needFat <= 109) 0
            else ceilDiv((needFat - 109).toLong(), entriesPerDifat.toLong()).toInt()
            if (needFat == nFat && needDifat == nDifat) break
            nFat = needFat
            nDifat = needDifat
        }

        val fatStart = 0
        val difatStart = fatStart + nFat
        val dirStart = difatStart + nDifat
        val miniFatStart = dirStart + nDir
        val miniStreamStart = miniFatStart + nMiniFat
        val pkgStart = miniStreamStart + nMiniStreamBig

        // ---- FAT ----
        val fat = IntArray(nFat * entriesPerFat) { CfbConst.FREESECT }
        for (i in 0 until nFat) fat[fatStart + i] = CfbConst.FATSECT
        for (i in 0 until nDifat) fat[difatStart + i] = CfbConst.DIFSECT
        fat[dirStart] = CfbConst.ENDOFCHAIN
        chain(fat, miniFatStart, nMiniFat)
        chain(fat, miniStreamStart, nMiniStreamBig)
        chain(fat, pkgStart, nPkg)

        // ---- mini FAT ----
        val miniFat = IntArray(nMiniFat * entriesPerFat) { CfbConst.FREESECT }
        chain(miniFat, 0, infoMini)
        chain(miniFat, infoMini, pkgMini)

        BufferedOutputStream(FileOutputStream(target), 64 * 1024).use { out ->
            out.write(buildHeader(nFat, nDifat, difatStart, dirStart, miniFatStart, nMiniFat))

            // FAT sectors
            val fatBytes = ByteArray(nFat * sector)
            for (i in fat.indices) fatBytes.putLe32(i * 4, fat[i])
            out.write(fatBytes)

            // DIFAT sectors (FAT locations 109.. onwards)
            if (nDifat > 0) {
                val difatBytes = ByteArray(nDifat * sector)
                var fatIndex = 109
                for (s in 0 until nDifat) {
                    val off = s * sector
                    for (e in 0 until entriesPerDifat) {
                        val v = if (fatIndex < nFat) fatStart + fatIndex else CfbConst.FREESECT
                        difatBytes.putLe32(off + e * 4, v)
                        fatIndex++
                    }
                    val next = if (s + 1 < nDifat) difatStart + s + 1 else CfbConst.ENDOFCHAIN
                    difatBytes.putLe32(off + entriesPerDifat * 4, next)
                }
                out.write(difatBytes)
            }

            // Directory sector: Root Entry + the two streams (+ one empty slot)
            val dir = ByteArray(sector)
            dirEntry(
                name = "Root Entry", type = CfbConst.TYPE_ROOT, child = 1,
                left = CfbConst.NOSTREAM, right = CfbConst.NOSTREAM,
                start = if (nMiniStreamBig > 0) miniStreamStart else CfbConst.ENDOFCHAIN,
                size = miniStreamLen.toLong()
            ).copyInto(dir, 0)
            // Sibling order in CFB is by (name length, then upper-case name):
            // "EncryptionInfo" (14) sorts before "EncryptedPackage" (16).
            dirEntry(
                name = CfbConst.ENCRYPTION_INFO, type = CfbConst.TYPE_STREAM, child = CfbConst.NOSTREAM,
                left = CfbConst.NOSTREAM, right = 2, start = 0, size = encryptionInfo.size.toLong()
            ).copyInto(dir, CfbConst.DIR_ENTRY_SIZE)
            dirEntry(
                name = CfbConst.ENCRYPTED_PACKAGE, type = CfbConst.TYPE_STREAM, child = CfbConst.NOSTREAM,
                left = CfbConst.NOSTREAM, right = CfbConst.NOSTREAM,
                start = if (smallPackage != null) infoMini else pkgStart,
                size = packageLength
            ).copyInto(dir, CfbConst.DIR_ENTRY_SIZE * 2)
            emptyDirEntry().copyInto(dir, CfbConst.DIR_ENTRY_SIZE * 3)
            out.write(dir)

            // mini FAT sectors
            if (nMiniFat > 0) {
                val mf = ByteArray(nMiniFat * sector)
                for (i in miniFat.indices) mf.putLe32(i * 4, miniFat[i])
                out.write(mf)
            }

            // mini stream (EncryptionInfo, plus the package when it is small),
            // zero-padded out to whole big sectors
            if (nMiniStreamBig > 0) {
                val ms = ByteArray(nMiniStreamBig * sector)
                encryptionInfo.copyInto(ms, 0)
                smallPackage?.copyInto(ms, infoMini * CfbConst.MINI_SECTOR)
                out.write(ms)
            }

            // EncryptedPackage as its own sector chain, streamed then padded
            if (smallPackage == null) {
                val counting = CountingOutputStream(out)
                writePackage(counting)
                check(counting.count == packageLength) {
                    "حجم الحزمة غير مطابق: ${counting.count} بدل $packageLength"
                }
                val pad = (nPkg.toLong() * sector - packageLength).toInt()
                if (pad > 0) out.write(ByteArray(pad))
            }
            out.flush()
        }
    }

    private fun chain(fat: IntArray, start: Int, count: Int) {
        for (i in 0 until count) {
            fat[start + i] = if (i + 1 < count) start + i + 1 else CfbConst.ENDOFCHAIN
        }
    }

    private fun buildHeader(
        nFat: Int, nDifat: Int, difatStart: Int,
        dirStart: Int, miniFatStart: Int, nMiniFat: Int
    ): ByteArray {
        val h = ByteArray(CfbConst.HEADER_SIZE)
        CfbConst.MAGIC.copyInto(h, 0)
        h.putLe16(0x18, 0x003E)          // minor version
        h.putLe16(0x1A, 0x0003)          // major version 3 → 512-byte sectors
        h.putLe16(0x1C, 0xFFFE)          // little-endian byte order
        h.putLe16(0x1E, 9)               // sector shift  (1 << 9  = 512)
        h.putLe16(0x20, 6)               // mini sector shift (1 << 6 = 64)
        h.putLe32(0x28, 0)               // directory sector count (0 for v3)
        h.putLe32(0x2C, nFat)
        h.putLe32(0x30, dirStart)
        h.putLe32(0x34, 0)               // transaction signature
        h.putLe32(0x38, CfbConst.MINI_CUTOFF)
        h.putLe32(0x3C, if (nMiniFat > 0) miniFatStart else CfbConst.ENDOFCHAIN)
        h.putLe32(0x40, nMiniFat)
        h.putLe32(0x44, if (nDifat > 0) difatStart else CfbConst.ENDOFCHAIN)
        h.putLe32(0x48, nDifat)
        for (i in 0 until 109) {
            h.putLe32(0x4C + i * 4, if (i < nFat) i else CfbConst.FREESECT)
        }
        return h
    }

    private fun dirEntry(
        name: String, type: Int, left: Int, right: Int, child: Int, start: Int, size: Long
    ): ByteArray {
        val e = ByteArray(CfbConst.DIR_ENTRY_SIZE)
        val nameBytes = name.toByteArray(Charsets.UTF_16LE)
        require(nameBytes.size <= 62) { "اسم تدفق طويل جدًا" }
        nameBytes.copyInto(e, 0)
        e.putLe16(0x40, nameBytes.size + 2)   // includes the UTF-16 null terminator
        e[0x42] = type.toByte()
        e[0x43] = 1                            // colour: black
        e.putLe32(0x44, left)
        e.putLe32(0x48, right)
        e.putLe32(0x4C, child)
        e.putLe32(0x74, start)
        e.putLe64(0x78, size)
        return e
    }

    private fun emptyDirEntry(): ByteArray {
        val e = ByteArray(CfbConst.DIR_ENTRY_SIZE)
        e[0x42] = CfbConst.TYPE_EMPTY.toByte()
        e.putLe32(0x44, CfbConst.NOSTREAM)
        e.putLe32(0x48, CfbConst.NOSTREAM)
        e.putLe32(0x4C, CfbConst.NOSTREAM)
        return e
    }

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
}

private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(b: Int) {
        delegate.write(b); count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len); count += len
    }

    override fun flush() = delegate.flush()
    override fun close() { /* the owner closes the delegate */ }
}

/** Reads streams out of a CFB container (handles 512- and 4096-byte sectors). */
internal class CfbReader private constructor(
    private val raf: RandomAccessFile,
    private val sectorSize: Int,
    private val miniCutoff: Int,
    private val fat: IntArray,
    private val miniFat: IntArray,
    private val entries: List<Entry>,
    private val miniStream: ByteArray
) : Closeable {

    data class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    fun hasStream(name: String): Boolean = find(name) != null

    fun readStream(name: String, maxBytes: Int = 8 * 1024 * 1024): ByteArray {
        val e = find(name) ?: error("التدفق $name غير موجود داخل الملف")
        require(e.size <= maxBytes) { "التدفق $name أكبر من الحد المسموح" }
        openStream(e).use { input ->
            val out = ByteArray(e.size.toInt())
            var read = 0
            while (read < out.size) {
                val n = input.read(out, read, out.size - read)
                if (n <= 0) break
                read += n
            }
            check(read == out.size) { "الملف مقطوع أثناء قراءة $name" }
            return out
        }
    }

    fun openStream(name: String): InputStream =
        openStream(find(name) ?: error("التدفق $name غير موجود داخل الملف"))

    private fun openStream(e: Entry): InputStream {
        if (e.size < miniCutoff) {
            val chain = resolveChain(miniFat, e.start, CfbConst.MINI_SECTOR.toLong(), e.size)
            val buf = ByteArray(e.size.toInt())
            var written = 0
            for (sec in chain) {
                val from = sec * CfbConst.MINI_SECTOR
                val n = minOf(CfbConst.MINI_SECTOR, buf.size - written)
                if (n <= 0) break
                miniStream.copyInto(buf, written, from, from + n)
                written += n
            }
            return ByteArrayInputStream(buf)
        }
        val chain = resolveChain(fat, e.start, sectorSize.toLong(), e.size)
        return ChainInputStream(raf, chain, sectorSize, e.size)
    }

    private fun find(name: String): Entry? =
        entries.firstOrNull { it.type == CfbConst.TYPE_STREAM && it.name == name }

    override fun close() = raf.close()

    private class ChainInputStream(
        private val raf: RandomAccessFile,
        private val chain: IntArray,
        private val sectorSize: Int,
        size: Long
    ) : InputStream() {
        private var remaining = size
        private var index = 0
        private val buf = ByteArray(sectorSize)
        private var pos = sectorSize

        private fun fill(): Boolean {
            if (index >= chain.size) return false
            raf.seek(sectorSize.toLong() * (chain[index] + 1))
            raf.readFully(buf)
            index++
            pos = 0
            return true
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == 1) one[0].toInt() and 0xFF else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            if (pos >= sectorSize && !fill()) return -1
            val n = minOf(len.toLong(), (sectorSize - pos).toLong(), remaining).toInt()
            buf.copyInto(b, off, pos, pos + n)
            pos += n
            remaining -= n
            return n
        }
    }

    companion object {
        fun open(file: File): CfbReader {
            val raf = RandomAccessFile(file, "r")
            try {
                val header = ByteArray(CfbConst.HEADER_SIZE)
                raf.readFully(header)
                require(header.copyOf(8).contentEquals(CfbConst.MAGIC)) { "الملف ليس بصيغة Office مشفرة" }
                val sectorSize = 1 shl header.le16(0x1E)
                require(sectorSize == 512 || sectorSize == 4096) { "حجم قطاع غير مدعوم: $sectorSize" }
                val miniCutoff = header.le32(0x38).let { if (it > 0) it else CfbConst.MINI_CUTOFF }
                val nFat = header.le32(0x2C)
                val dirStart = header.le32(0x30)
                val miniFatStart = header.le32(0x3C)
                val nMiniFat = header.le32(0x40)
                var difatSector = header.le32(0x44)
                val nDifat = header.le32(0x48)

                fun sectorOffset(s: Int): Long = sectorSize.toLong() * (s + 1)

                fun readSector(s: Int): ByteArray {
                    val b = ByteArray(sectorSize)
                    raf.seek(sectorOffset(s))
                    raf.readFully(b)
                    return b
                }

                // DIFAT → list of FAT sector locations
                val fatSectors = ArrayList<Int>(nFat.coerceAtMost(1 shl 20))
                for (i in 0 until 109) {
                    if (fatSectors.size >= nFat) break
                    val v = header.le32(0x4C + i * 4)
                    if (v >= 0) fatSectors.add(v)
                }
                var guard = 0
                while (fatSectors.size < nFat && difatSector >= 0 && guard++ <= nDifat + 1) {
                    val d = readSector(difatSector)
                    val perSector = sectorSize / 4 - 1
                    for (i in 0 until perSector) {
                        if (fatSectors.size >= nFat) break
                        val v = d.le32(i * 4)
                        if (v >= 0) fatSectors.add(v)
                    }
                    difatSector = d.le32(perSector * 4)
                }

                val fat = IntArray(fatSectors.size * (sectorSize / 4))
                fatSectors.forEachIndexed { si, sec ->
                    val b = readSector(sec)
                    for (i in 0 until sectorSize / 4) fat[si * (sectorSize / 4) + i] = b.le32(i * 4)
                }

                // Directory entries
                val dirBytes = readChainBytes(raf, fat, dirStart, sectorSize, ::sectorOffset)
                val entries = ArrayList<Entry>()
                var off = 0
                while (off + CfbConst.DIR_ENTRY_SIZE <= dirBytes.size) {
                    val nameLen = dirBytes.le16(off + 0x40)
                    val type = dirBytes[off + 0x42].toInt() and 0xFF
                    val name = if (nameLen > 2) {
                        String(dirBytes, off, nameLen - 2, Charsets.UTF_16LE)
                    } else ""
                    entries.add(
                        Entry(
                            name = name,
                            type = type,
                            start = dirBytes.le32(off + 0x74),
                            size = dirBytes.le64(off + 0x78)
                        )
                    )
                    off += CfbConst.DIR_ENTRY_SIZE
                }
                val root = entries.firstOrNull { it.type == CfbConst.TYPE_ROOT }
                    ?: error("جذر الملف غير موجود")

                // mini FAT + mini stream (small; always fits comfortably in memory)
                val miniFat = if (nMiniFat > 0 && miniFatStart >= 0) {
                    val b = readChainBytes(raf, fat, miniFatStart, sectorSize, ::sectorOffset)
                    IntArray(b.size / 4) { b.le32(it * 4) }
                } else IntArray(0)

                val miniStream = if (root.size > 0 && root.start >= 0) {
                    val all = readChainBytes(raf, fat, root.start, sectorSize, ::sectorOffset)
                    val len = minOf(root.size, all.size.toLong()).toInt()
                    all.copyOf(len)
                } else ByteArray(0)

                return CfbReader(raf, sectorSize, miniCutoff, fat, miniFat, entries, miniStream)
            } catch (e: Throwable) {
                runCatching { raf.close() }
                throw e
            }
        }

        private fun readChainBytes(
            raf: RandomAccessFile,
            fat: IntArray,
            start: Int,
            sectorSize: Int,
            offsetOf: (Int) -> Long
        ): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var sec = start
            var guard = 0
            val buf = ByteArray(sectorSize)
            while (sec >= 0 && sec < fat.size && guard++ < MAX_CHAIN) {
                raf.seek(offsetOf(sec))
                raf.readFully(buf)
                out.write(buf)
                sec = fat[sec]
            }
            return out.toByteArray()
        }

        private const val MAX_CHAIN = 1 shl 22
    }
}

private fun resolveChain(fat: IntArray, start: Int, unitSize: Long, size: Long): IntArray {
    val needed = ((size + unitSize - 1) / unitSize).toInt()
    val out = IntArray(needed)
    var sec = start
    for (i in 0 until needed) {
        require(sec >= 0 && sec < fat.size) { "سلسلة القطاعات تالفة داخل الملف" }
        out[i] = sec
        sec = fat[sec]
    }
    return out
}
