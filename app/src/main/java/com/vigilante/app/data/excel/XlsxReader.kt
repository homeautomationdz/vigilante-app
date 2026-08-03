package com.vigilante.app.data.excel

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/**
 * Minimal, Android-safe .xlsx reader.
 *
 * Why hand-rolled (again): fastexcel-reader parses through StAX/aalto-xml,
 * which does not exist on Android — after R8 the missing class surfaced as
 * "Failed resolution of: La4/a" and every import failed with a misleading
 * "ملف Excel غير صالح", encrypted or not. This reader uses only
 * java.util.zip and javax.xml.parsers.SAXParser, both of which ship with the
 * Android platform *and* the JVM, so the same code path is unit-tested in CI.
 *
 * It reads what an xlsx actually is: a zip holding xl/workbook.xml (sheet
 * names), xl/_rels/workbook.xml.rels (where each sheet lives),
 * xl/sharedStrings.xml (the string pool) and one XML file per sheet.
 */
internal class XlsxWorkbook private constructor(
    private val zip: ZipFile,
    private val sharedStrings: List<String>,
    private val sheetEntries: Map<String, String>
) : Closeable {

    val sheetNames: Set<String> get() = sheetEntries.keys

    fun hasSheet(name: String): Boolean = sheetEntries.containsKey(name)

    /**
     * Streams a sheet row by row. [onRow] receives the 1-based row number as
     * Excel shows it and a map of 0-based column index → trimmed cell text.
     * Empty cells are simply absent from the map.
     */
    fun readRows(sheetName: String, onRow: (rowNumber: Int, cells: Map<Int, String>) -> Unit) {
        val path = sheetEntries[sheetName] ?: error("الورقة $sheetName غير موجودة")
        val entry = zip.getEntry(path) ?: error("تعذر فتح الورقة $sheetName داخل الملف")
        zip.getInputStream(entry).use { input ->
            parse(input, SheetHandler(sharedStrings, onRow))
        }
    }

    override fun close() = zip.close()

    // ---------- SAX handlers ----------

    private class SheetHandler(
        private val sharedStrings: List<String>,
        private val onRow: (Int, Map<Int, String>) -> Unit
    ) : DefaultHandler() {

        private var rowNumber = 0
        private var cells = HashMap<Int, String>()
        private var columnIndex = 0
        private var cellType: String? = null
        private var capturing = false
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            when (qName.substringAfter(':')) {
                "row" -> {
                    rowNumber = attrs.getValue("r")?.toIntOrNull() ?: (rowNumber + 1)
                    cells = HashMap()
                }
                "c" -> {
                    columnIndex = columnOf(attrs.getValue("r"))
                    cellType = attrs.getValue("t")
                }
                // <v> holds the value; <t> holds inline / shared string text.
                "v", "t" -> { capturing = true; text.setLength(0) }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capturing) text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (val tag = qName.substringAfter(':')) {
                "v", "t" -> {
                    if (!capturing) return
                    capturing = false
                    val raw = text.toString()
                    val value = if (tag == "v" && cellType == "s") {
                        raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                    } else raw
                    if (value.isNotBlank()) {
                        // Rich text splits one cell across several <t> runs.
                        val existing = cells[columnIndex]
                        cells[columnIndex] = if (existing.isNullOrEmpty()) value.trim()
                        else (existing + value).trim()
                    }
                }
                "row" -> onRow(rowNumber, cells)
            }
        }
    }

    private class SharedStringsHandler : DefaultHandler() {
        val strings = ArrayList<String>()
        private val current = StringBuilder()
        private var capturing = false
        private var inItem = false

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            when (qName.substringAfter(':')) {
                "si" -> { inItem = true; current.setLength(0) }
                "t" -> capturing = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capturing) current.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName.substringAfter(':')) {
                "t" -> capturing = false
                "si" -> {
                    if (inItem) strings.add(current.toString())
                    inItem = false
                }
            }
        }
    }

    private class WorkbookHandler : DefaultHandler() {
        /** sheet name → relationship id */
        val sheets = LinkedHashMap<String, String>()

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            if (qName.substringAfter(':') != "sheet") return
            val name = attrs.getValue("name") ?: return
            val rid = attrs.getValue("r:id")
                ?: attrs.getValue("id")
                ?: (0 until attrs.length).firstOrNull { attrs.getQName(it).endsWith(":id") }
                    ?.let { attrs.getValue(it) }
                ?: return
            sheets[name] = rid
        }
    }

    private class RelsHandler : DefaultHandler() {
        /** relationship id → target path */
        val targets = HashMap<String, String>()

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            if (qName.substringAfter(':') != "Relationship") return
            val id = attrs.getValue("Id") ?: return
            val target = attrs.getValue("Target") ?: return
            targets[id] = target
        }
    }

    companion object {
        private const val MAX_SHARED_STRINGS = 2_000_000

        /** Throws [NotAnXlsxException] when the file is not a readable xlsx. */
        fun open(file: File): XlsxWorkbook {
            val zip = try {
                ZipFile(file)
            } catch (e: Exception) {
                throw NotAnXlsxException("الملف ليس ملف Excel بصيغة xlsx", e)
            }
            try {
                if (zip.getEntry("xl/workbook.xml") == null) {
                    throw NotAnXlsxException("الملف ليس مصنّف Excel صالحًا (xl/workbook.xml مفقود)")
                }

                val workbook = WorkbookHandler().also { h ->
                    zip.entryStream("xl/workbook.xml")?.use { parse(it, h) }
                }
                val rels = RelsHandler().also { h ->
                    zip.entryStream("xl/_rels/workbook.xml.rels")?.use { parse(it, h) }
                }
                val shared = zip.entryStream("xl/sharedStrings.xml")?.use { input ->
                    SharedStringsHandler().also { parse(input, it) }.strings
                } ?: emptyList()
                require(shared.size <= MAX_SHARED_STRINGS) { "ملف Excel كبير بشكل غير معتاد" }

                val entries = LinkedHashMap<String, String>()
                for ((name, rid) in workbook.sheets) {
                    val target = rels.targets[rid] ?: continue
                    val path = normalizeTarget(target)
                    if (zip.getEntry(path) != null) entries[name] = path
                }
                if (entries.isEmpty()) {
                    // Some producers omit rels; fall back to positional sheets.
                    workbook.sheets.keys.forEachIndexed { i, name ->
                        val guess = "xl/worksheets/sheet${i + 1}.xml"
                        if (zip.getEntry(guess) != null) entries[name] = guess
                    }
                }
                if (entries.isEmpty()) {
                    throw NotAnXlsxException("لم يُعثر على أي ورقة عمل داخل الملف")
                }
                return XlsxWorkbook(zip, shared, entries)
            } catch (e: Throwable) {
                runCatching { zip.close() }
                throw e
            }
        }

        private fun normalizeTarget(target: String): String {
            val clean = target.removePrefix("/")
            return if (clean.startsWith("xl/")) clean else "xl/$clean"
        }

        private fun ZipFile.entryStream(path: String): InputStream? =
            getEntry(path)?.let { e: ZipEntry -> getInputStream(e) }

        private fun parse(input: InputStream, handler: DefaultHandler) {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                // Imported files are untrusted — never resolve external entities.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            factory.newSAXParser().parse(input, handler)
        }

        /** "BC12" → 54. Falls back to sequential order when `r` is absent. */
        fun columnOf(reference: String?): Int {
            if (reference.isNullOrBlank()) return 0
            var index = 0
            var seen = false
            for (ch in reference) {
                val upper = ch.uppercaseChar()
                if (upper !in 'A'..'Z') break
                index = index * 26 + (upper - 'A' + 1)
                seen = true
            }
            return if (seen) index - 1 else 0
        }
    }
}

/** The picked file is not an xlsx at all (wrong type, corrupt zip, …). */
internal class NotAnXlsxException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
