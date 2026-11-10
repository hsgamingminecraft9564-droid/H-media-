package com.hmedia.voidengine

import com.hmedia.voidengine.features.vault.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class VaultCryptoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun crypto(k: SecretKey = key) = VaultCrypto({ k }, 64)
    private fun data(n: Int) = ByteArray(n) { (it * 31 + 7).toByte() }

    private fun encryptToFile(bytes: ByteArray): File {
        val file = tmp.newFile()
        FileOutputStream(file).use { crypto().encrypt(ByteArrayInputStream(bytes), it) }
        return file
    }

    @Test
    fun roundTripForBoundarySizes() {
        for (size in listOf(0, 1, 63, 64, 65, 128, 129, 1000)) {
            val plain = data(size)
            val file = encryptToFile(plain)
            assertEquals("size $size", size.toLong(), crypto().plainLength(file.length()))
            val out = ByteArrayOutputStream()
            crypto().decryptTo(file, out)
            assertArrayEquals("size $size", plain, out.toByteArray())
        }
    }

    @Test
    fun randomAccessChunksMatchPlaintext() {
        val plain = data(200)
        val file = encryptToFile(plain)
        RandomAccessFile(file, "r").use { raf ->
            val c = crypto()
            c.checkHeader(raf)
            assertArrayEquals(plain.copyOfRange(64, 128), c.readChunk(raf, 1))
            assertArrayEquals(plain.copyOfRange(192, 200), c.readChunk(raf, 3))
            assertArrayEquals(plain.copyOfRange(0, 64), c.readChunk(raf, 0))
        }
    }

    @Test
    fun tamperedCiphertextIsDetected() {
        val file = encryptToFile(data(200))
        RandomAccessFile(file, "rw").use { raf ->
            val position = VaultCrypto.HEADER_SIZE + 4 + VaultCrypto.IV_SIZE + 2
            raf.seek(position)
            val b = raf.read()
            raf.seek(position)
            raf.write(b xor 0x01)
        }
        assertThrows(IOException::class.java) { crypto().decryptTo(file, ByteArrayOutputStream()) }
    }

    @Test
    fun swappedChunksAreDetected() {
        val file = encryptToFile(data(192)) // exactly 3 full chunks
        val bytes = file.readBytes()
        val stride = crypto().stride.toInt()
        val start = VaultCrypto.HEADER_SIZE.toInt()
        val swapped = bytes.copyOf()
        System.arraycopy(bytes, start + stride, swapped, start, stride)
        System.arraycopy(bytes, start, swapped, start + stride, stride)
        file.writeBytes(swapped)
        assertThrows(IOException::class.java) { crypto().decryptTo(file, ByteArrayOutputStream()) }
    }

    @Test
    fun wrongKeyIsRejected() {
        val file = encryptToFile(data(100))
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(IOException::class.java) { crypto(other).decryptTo(file, ByteArrayOutputStream()) }
    }

    @Test
    fun malformedLengthsAreRejected() {
        assertThrows(IOException::class.java) { crypto().plainLength(3) }
        assertThrows(IOException::class.java) { crypto().plainLength(VaultCrypto.HEADER_SIZE + crypto().stride + 10) }
    }
}
