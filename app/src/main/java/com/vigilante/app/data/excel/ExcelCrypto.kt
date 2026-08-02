package com.vigilante.app.data.excel

import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.crypt.EncryptionMode
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Office-native password protection for exported .xlsx files.
 *
 * Uses ECMA-376 Standard Encryption (AES-128 inside an OLE2/CFB container) —
 * the same scheme Excel itself uses, so opening the exported file on a PC
 * makes Excel/LibreOffice prompt for the password. The app decrypts
 * transparently on re-import using the password stored in Settings, so
 * supervisors never type it when uploading a file back.
 */
object ExcelCrypto {

    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )

    /** True when [file] is an encrypted Office container rather than a plain xlsx (zip). */
    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        val head = ByteArray(8)
        FileInputStream(file).use { if (it.read(head) != 8) return false }
        return head.contentEquals(OLE2_MAGIC)
    }

    /** Encrypts [plainXlsx] into [target] protected by [password]. */
    fun encrypt(plainXlsx: File, target: File, password: String) {
        require(password.isNotBlank()) { "كلمة مرور التشفير فارغة" }
        POIFSFileSystem().use { fs ->
            val info = EncryptionInfo(EncryptionMode.standard)
            val encryptor = info.encryptor
            encryptor.confirmPassword(password)
            encryptor.getDataStream(fs).use { out ->
                FileInputStream(plainXlsx).use { it.copyTo(out) }
            }
            val tmp = File(target.parentFile, target.name + ".tmp")
            FileOutputStream(tmp).use { fs.writeFilesystem(it) }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true); tmp.delete()
            }
        }
    }

    /**
     * Decrypts [encrypted] into a plain temporary xlsx inside [tempDir].
     * Throws with a clear Arabic message when the password is wrong.
     */
    fun decryptToTemp(encrypted: File, password: String, tempDir: File): File {
        POIFSFileSystem(FileInputStream(encrypted)).use { fs ->
            val info = EncryptionInfo(fs)
            val decryptor = Decryptor.getInstance(info)
            check(decryptor.verifyPassword(password)) { "كلمة مرور ملف Excel غير صحيحة" }
            val out = File(tempDir, "decrypted_${System.currentTimeMillis()}.xlsx")
            decryptor.getDataStream(fs).use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            return out
        }
    }
}
