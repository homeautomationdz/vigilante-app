package com.vigilante.app

import com.vigilante.app.data.excel.NotAnXlsxException
import com.vigilante.app.data.excel.XlsxWorkbook
import org.dhatim.fastexcel.Workbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.createTempDirectory

/**
 * The reader that replaced fastexcel-reader (which cannot run on Android).
 * These tests write real workbooks with the same writer the app ships and read
 * them back with our parser, so a regression here fails the build rather than
 * the phone.
 */
class XlsxReaderTest {

    private fun workbook(target: File, build: (Workbook) -> Unit) {
        FileOutputStream(target).use { out ->
            val wb = Workbook(out, "VigilanteTest", "1.0")
            build(wb)
            wb.finish()
        }
    }

    @Test
    fun readsSheetsColumnsAndArabicText() {
        val dir = createTempDirectory("xlsx").toFile()
        val file = File(dir, "book.xlsx")
        workbook(file) { wb ->
            val ws = wb.newWorksheet("Volunteers")
            ws.value(0, 0, "VolunteerID")
            ws.value(0, 1, "FirstName")
            ws.value(0, 2, "Phone1")
            ws.value(1, 0, "VOL-000001")
            ws.value(1, 1, "محمد")
            ws.value(1, 2, "0550223366")
            ws.value(2, 0, "VOL-000002")
            ws.value(2, 1, "عبد القادر")
            ws.value(2, 2, "0661122334")
            wb.newWorksheet("Metadata").value(0, 0, "Key")
        }

        XlsxWorkbook.open(file).use { wb ->
            assertTrue(wb.hasSheet("Volunteers"))
            assertTrue(wb.hasSheet("Metadata"))
            assertFalse(wb.hasSheet("Ghost"))

            val rows = mutableListOf<Map<Int, String>>()
            wb.readRows("Volunteers") { _, cells -> rows.add(cells) }

            assertEquals(3, rows.size)
            assertEquals("VolunteerID", rows[0][0])
            assertEquals("Phone1", rows[0][2])
            assertEquals("VOL-000001", rows[1][0])
            assertEquals("محمد", rows[1][1])
            assertEquals("0550223366", rows[1][2])
            assertEquals("عبد القادر", rows[2][1])
        }
    }

    @Test
    fun skipsEmptyCellsButKeepsColumnPositions() {
        val dir = createTempDirectory("xlsx2").toFile()
        val file = File(dir, "sparse.xlsx")
        workbook(file) { wb ->
            val ws = wb.newWorksheet("Volunteers")
            ws.value(0, 0, "A")
            ws.value(0, 3, "D")          // columns B and C intentionally empty
            ws.value(1, 3, "value-d")
        }

        XlsxWorkbook.open(file).use { wb ->
            val rows = mutableListOf<Map<Int, String>>()
            wb.readRows("Volunteers") { _, cells -> rows.add(cells) }
            assertEquals("A", rows[0][0])
            assertEquals("D", rows[0][3])
            assertEquals(null, rows[0][1])
            assertEquals("value-d", rows[1][3])
        }
    }

    @Test
    fun reportsRowNumbersAsExcelShowsThem() {
        val dir = createTempDirectory("xlsx3").toFile()
        val file = File(dir, "rows.xlsx")
        workbook(file) { wb ->
            val ws = wb.newWorksheet("Volunteers")
            ws.value(0, 0, "header")
            ws.value(1, 0, "first")
            ws.value(2, 0, "second")
        }

        XlsxWorkbook.open(file).use { wb ->
            val numbers = mutableListOf<Int>()
            wb.readRows("Volunteers") { n, _ -> numbers.add(n) }
            assertEquals(listOf(1, 2, 3), numbers)
        }
    }

    @Test
    fun rejectsNonXlsxWithAClearMessage() {
        val dir = createTempDirectory("xlsx4").toFile()
        val notExcel = File(dir, "notes.txt").apply { writeText("هذا ليس ملف إكسل") }

        val error = runCatching { XlsxWorkbook.open(notExcel) }.exceptionOrNull()

        assertTrue(error is NotAnXlsxException)
        assertTrue(error!!.message!!.contains("Excel"))
    }

    @Test
    fun columnLettersMapToIndexes() {
        assertEquals(0, XlsxWorkbook.columnOf("A1"))
        assertEquals(1, XlsxWorkbook.columnOf("B2"))
        assertEquals(25, XlsxWorkbook.columnOf("Z10"))
        assertEquals(26, XlsxWorkbook.columnOf("AA1"))
        assertEquals(53, XlsxWorkbook.columnOf("BB7"))
    }
}
