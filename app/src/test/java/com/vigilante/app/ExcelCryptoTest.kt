package com.vigilante.app

import com.vigilante.app.data.excel.ExcelCrypto
import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.crypt.EncryptionMode
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.io.path.createTempDirectory

/**
 * Cross-verification of our hand-rolled Office encryption against Apache POI,
 * which is the reference implementation of MS-OFFCRYPTO on the JVM.
 *
 * POI cannot run on Android (that is precisely why the app implements the
 * format itself), but it runs fine here — so these tests are what guarantees
 * that a file exported by the phone is one Microsoft Excel can open, and that
 * a file Excel re-encrypted can be imported back.
 */
class ExcelCryptoTest {

    private val password = "Vigilante-2026"

    // ---- helpers ----

    private fun writeSampleXlsx(target: File) {
        FileOutputStream(target).use { out ->
            val wb = Workbook(out, "VigilanteTest", "1.0")
            val ws = wb.newWorksheet("Volunteers")
            ws.value(0, 0, "VolunteerID")
            ws.value(0, 1, "FirstName")
            ws.value(1, 0, "VOL-000001")
            ws.value(1, 1, "محمد")
            wb.finish()
        }
    }

    private fun randomFile(target: File, size: Int) {
        val rnd = Random(42)
        val buf = ByteArray(64 * 1024)
        FileOutputStream(target).use { out ->
            var written = 0
            while (written < size) {
                rnd.nextBytes(buf)
                val n = minOf(buf.size, size - written)
                out.write(buf, 0, n)
                written += n
            }
        }
    }

    private fun poiDecrypt(encrypted: File, password: String): ByteArray {
        POIFSFileSystem(encrypted, true).use { fs ->
            val info = EncryptionInfo(fs)
            val decryptor = Decryptor.getInstance(info)
            assertTrue("POI must accept the password", decryptor.verifyPassword(password))
            return decryptor.getDataStream(fs).use { it.readBytes() }
        }
    }

    private fun poiEncrypt(plain: ByteArray, target: File, password: String, mode: EncryptionMode) {
        POIFSFileSystem().use { fs ->
            val info = EncryptionInfo(mode)
            val enc = info.encryptor
            enc.confirmPassword(password)
            enc.getDataStream(fs).use { it.write(plain) }
            FileOutputStream(target).use { fs.writeFilesystem(it) }
        }
    }

    // ---- our output must be readable by Excel (proxied by POI) ----

    @Test
    fun ourEncryptedFileIsReadableByPoi() {
        val dir = createTempDirectory("enc-poi").toFile()
        val plain = File(dir, "plain.xlsx").also { writeSampleXlsx(it) }
        val encrypted = File(dir, "protected.xlsx")

        ExcelCrypto.encrypt(plain, encrypted, password)

        assertTrue(ExcelCrypto.isEncrypted(encrypted))
        assertFalse(ExcelCrypto.isEncrypted(plain))
        assertArrayEquals(plain.readBytes(), poiDecrypt(encrypted, password))
    }

    /** > 6.8 MB forces extra DIFAT sectors in the OLE2 container. */
    @Test
    fun largeFileWithDifatSectorsIsReadableByPoi() {
        val dir = createTempDirectory("enc-big").toFile()
        val plain = File(dir, "big.bin").also { randomFile(it, 8 * 1024 * 1024) }
        val encrypted = File(dir, "big-protected.xlsx")

        ExcelCrypto.encrypt(plain, encrypted, password)

        assertArrayEquals(plain.readBytes(), poiDecrypt(encrypted, password))
    }

    /** Length that is not a multiple of the AES block size must round-trip too. */
    @Test
    fun unalignedLengthRoundTrips() {
        val dir = createTempDirectory("enc-odd").toFile()
        val plain = File(dir, "odd.bin").also { randomFile(it, 5000 + 7) }
        val encrypted = File(dir, "odd-protected.xlsx")

        ExcelCrypto.encrypt(plain, encrypted, password)

        assertArrayEquals(plain.readBytes(), poiDecrypt(encrypted, password))
    }

    // ---- files produced by Excel/POI must be importable by us ----

    @Test
    fun weCanDecryptStandardEncryptionFromPoi() {
        val dir = createTempDirectory("dec-std").toFile()
        val plain = File(dir, "plain.xlsx").also { writeSampleXlsx(it) }
        val encrypted = File(dir, "poi-standard.xlsx")
        poiEncrypt(plain.readBytes(), encrypted, password, EncryptionMode.standard)

        val decrypted = ExcelCrypto.decryptToTemp(encrypted, password, dir)

        assertArrayEquals(plain.readBytes(), decrypted.readBytes())
    }

    /** Excel 2016+ re-encrypts with Agile when a supervisor edits and saves. */
    @Test
    fun weCanDecryptAgileEncryptionFromPoi() {
        val dir = createTempDirectory("dec-agile").toFile()
        val plain = File(dir, "plain.xlsx").also { writeSampleXlsx(it) }
        val encrypted = File(dir, "poi-agile.xlsx")
        poiEncrypt(plain.readBytes(), encrypted, password, EncryptionMode.agile)

        val decrypted = ExcelCrypto.decryptToTemp(encrypted, password, dir)

        assertArrayEquals(plain.readBytes(), decrypted.readBytes())
    }

    @Test
    fun agileFileLargerThanOneSegmentRoundTrips() {
        val dir = createTempDirectory("dec-agile-big").toFile()
        val plain = File(dir, "big.bin").also { randomFile(it, 300 * 1024) }
        val encrypted = File(dir, "poi-agile-big.xlsx")
        poiEncrypt(plain.readBytes(), encrypted, password, EncryptionMode.agile)

        val decrypted = ExcelCrypto.decryptToTemp(encrypted, password, dir)

        assertArrayEquals(plain.readBytes(), decrypted.readBytes())
    }

    // ---- our own round trip + failure modes ----

    @Test
    fun ourEncryptDecryptRoundTripKeepsWorkbookReadable() {
        val dir = createTempDirectory("round").toFile()
        val plain = File(dir, "plain.xlsx").also { writeSampleXlsx(it) }
        val encrypted = File(dir, "protected.xlsx")

        ExcelCrypto.encrypt(plain, encrypted, password)
        val decrypted = ExcelCrypto.decryptToTemp(encrypted, password, dir)

        ReadableWorkbook(decrypted).use { wb ->
            val sheet = wb.sheets.filter { it.name == "Volunteers" }.findFirst()
            assertTrue("Volunteers sheet must survive the round trip", sheet.isPresent)
            val rows = sheet.get().read()
            assertEquals("VOL-000001", rows[1].getCellText(0))
        }
    }

    @Test
    fun wrongPasswordIsRejectedClearly() {
        val dir = createTempDirectory("wrong").toFile()
        val plain = File(dir, "plain.xlsx").also { writeSampleXlsx(it) }
        val encrypted = File(dir, "protected.xlsx")
        ExcelCrypto.encrypt(plain, encrypted, "correct-password")

        val result = runCatching { ExcelCrypto.decryptToTemp(encrypted, "wrong", dir) }

        assertTrue("wrong password must fail", result.isFailure)
        assertTrue(
            "message must be user-readable Arabic",
            result.exceptionOrNull()?.message?.contains("كلمة مرور") == true
        )
    }

    @Test
    fun plainXlsxIsAValidWorkbook() {
        val dir = createTempDirectory("plain").toFile()
        val plain = File(dir, "sample.xlsx").also { writeSampleXlsx(it) }
        ReadableWorkbook(plain).use { wb -> assertTrue(wb.sheets.count() >= 1) }
    }
}
