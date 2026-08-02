package com.vigilante.app

import com.vigilante.app.data.excel.ExcelCrypto
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.createTempDirectory

/**
 * Guards the export pipeline: a workbook written by fastexcel must survive
 * POI standard encryption and decrypt back into a readable xlsx. If this
 * breaks, exports on the device are broken — CI must fail loudly.
 */
class ExcelCryptoTest {

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

    @Test
    fun encryptDecryptRoundTrip() {
        val dir = createTempDirectory("crypto").toFile()
        val plain = File(dir, "plain.xlsx").also { writeSampleXlsx(it) }
        val encrypted = File(dir, "protected.xlsx")

        ExcelCrypto.encrypt(plain, encrypted, "0550223366")

        assertTrue("encrypted file must exist", encrypted.exists() && encrypted.length() > 0)
        assertTrue("must be detected as encrypted", ExcelCrypto.isEncrypted(encrypted))
        assertFalse("plain file must not be detected as encrypted", ExcelCrypto.isEncrypted(plain))

        val decrypted = ExcelCrypto.decryptToTemp(encrypted, "0550223366", dir)
        ReadableWorkbook(decrypted).use { wb ->
            val sheet = wb.sheets.filter { it.name == "Volunteers" }.findFirst()
            assertTrue("Volunteers sheet must survive the round trip", sheet.isPresent)
            val rows = sheet.get().read()
            assertEquals("VOL-000001", rows[1].getCellText(0))
        }
    }

    @Test
    fun wrongPasswordIsRejectedClearly() {
        val dir = createTempDirectory("crypto2").toFile()
        val plain = File(dir, "plain.xlsx").also { writeSampleXlsx(it) }
        val encrypted = File(dir, "protected.xlsx")
        ExcelCrypto.encrypt(plain, encrypted, "correct-password")

        val result = runCatching { ExcelCrypto.decryptToTemp(encrypted, "wrong", dir) }
        assertTrue("wrong password must throw", result.isFailure)
    }

    @Test
    fun plainXlsxOpensInExcelReaders() {
        // Acceptance: the exported workbook must be a valid xlsx that
        // spreadsheet apps can open (fastexcel-reader stands in for Excel).
        val dir = createTempDirectory("plain").toFile()
        val plain = File(dir, "sample.xlsx").also { writeSampleXlsx(it) }
        ReadableWorkbook(plain).use { wb ->
            assertTrue(wb.sheets.count() >= 1)
        }
    }
}
