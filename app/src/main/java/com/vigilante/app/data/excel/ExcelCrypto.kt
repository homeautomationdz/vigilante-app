package com.vigilante.app.data.excel

import com.vigilante.app.data.excel.crypto.AgileCrypto
import com.vigilante.app.data.excel.crypto.CfbConst
import com.vigilante.app.data.excel.crypto.CfbReader
import com.vigilante.app.data.excel.crypto.StandardCrypto
import com.vigilante.app.data.excel.crypto.le16
import java.io.File
import java.io.FileInputStream

/**
 * Office-native password protection for exported .xlsx files.
 *
 * Writing uses ECMA-376 **Standard Encryption** (AES-128 / SHA-1) inside an
 * OLE2 container — exactly what Excel's own "Encrypt with Password" produced
 * for years, so opening the exported file on a PC makes Excel or LibreOffice
 * prompt for the password. Reading additionally understands **Agile
 * Encryption**, because Excel 2016+ switches to it when a supervisor edits the
 * exported file and saves it; such a file must still import cleanly.
 *
 * Implemented on plain javax.crypto: Apache POI cannot run on Android (it
 * pulls log4j2 and desktop-JVM classes and fails with NoClassDefFoundError at
 * runtime). CI unit tests cross-check this implementation against POI on the
 * JVM in both directions, which is what guarantees Excel compatibility.
 */
object ExcelCrypto {

    private val OLE2_MAGIC = CfbConst.MAGIC

    /** True when [file] is an encrypted Office container rather than a plain xlsx (zip). */
    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        val head = ByteArray(8)
        FileInputStream(file).use { if (it.read(head) != 8) return false }
        return head.contentEquals(OLE2_MAGIC)
    }

    /** Encrypts [plainXlsx] into [target], protected by [password]. */
    fun encrypt(plainXlsx: File, target: File, password: String) {
        require(password.isNotBlank()) { "كلمة مرور التشفير فارغة" }
        StandardCrypto.encrypt(plainXlsx, target, password)
    }

    /**
     * Decrypts [encrypted] into a plain temporary xlsx inside [tempDir].
     * Throws with a clear Arabic message when the password is wrong.
     */
    fun decryptToTemp(encrypted: File, password: String, tempDir: File): File {
        require(password.isNotBlank()) { "كلمة مرور التشفير فارغة" }
        if (!tempDir.exists()) tempDir.mkdirs()
        val out = File(tempDir, "decrypted_${System.currentTimeMillis()}.xlsx")
        try {
            CfbReader.open(encrypted).use { cfb ->
                val info = cfb.readStream(CfbConst.ENCRYPTION_INFO)
                require(info.size > 8) { "معلومات التشفير داخل الملف غير صالحة" }
                val versionMajor = info.le16(0)
                val versionMinor = info.le16(2)
                if (versionMajor == 4 && versionMinor == 4) {
                    AgileCrypto.decrypt(cfb, info, password, out)
                } else {
                    StandardCrypto.decrypt(cfb, info, password, out)
                }
            }
            check(out.length() > 0) { "تعذر فك تشفير الملف — تحقق من كلمة المرور" }
            return out
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }
}
