package com.hmedia.voidengine.features.vault

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chunked AES-256-GCM container with random access (so video can be played without writing plaintext to disk).
 *
 * Layout:  "VLT1" | chunkSize (int32 BE) | record*   (all numbers big-endian)
 * Record:  ctLen (int32) | iv (12 bytes) | ciphertext+tag (ctLen bytes)
 * Every chunk except the last holds exactly [chunkSize] plaintext bytes, so every record except the
 * last has the same size ([stride]) and chunk N starts at HEADER_SIZE + N * stride.
 * The chunk index is bound into the GCM tag as AAD, so chunks cannot be reordered or swapped.
 * Limitation: removing whole chunks from the END of a file is not detected.
 *
 * Pure JVM code (the key comes from [keyProvider]), so it is unit-tested with a software key;
 * on a device the key is an Android Keystore key (see VaultKeys).
 */
class VaultCrypto(
    private val keyProvider: () -> SecretKey,
    val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    init {
        require(chunkSize in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) { "chunkSize out of range" }
    }

    /** Byte distance between the starts of two consecutive full records. */
    val stride: Long get() = chunkSize.toLong() + RECORD_OVERHEAD

    fun encrypt(input: InputStream, output: OutputStream) {
        try {
            output.write(MAGIC)
            output.write(intToBytes(chunkSize))
            val buffer = ByteArray(chunkSize)
            var index = 0L
            while (true) {
                val read = readFully(input, buffer)
                if (read <= 0) break
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
                cipher.updateAAD(aad(index))
                val ciphertext = cipher.doFinal(buffer, 0, read)
                val iv = cipher.iv
                if (iv == null || iv.size != IV_SIZE) throw IOException("Unexpected IV size")
                output.write(intToBytes(ciphertext.size))
                output.write(iv)
                output.write(ciphertext)
                index++
                if (read < chunkSize) break
            }
            output.flush()
        } catch (e: GeneralSecurityException) {
            throw IOException("Encryption failed", e)
        }
    }

    /** Size of the original data, computed from the encrypted file length alone. */
    fun plainLength(fileLength: Long): Long {
        val body = fileLength - HEADER_SIZE
        if (body < 0) throw IOException("Vault file too short")
        val full = body / stride
        val rest = body % stride
        if (rest == 0L) return full * chunkSize
        if (rest <= RECORD_OVERHEAD) throw IOException("Vault file truncated")
        return full * chunkSize + (rest - RECORD_OVERHEAD)
    }

    fun checkHeader(file: RandomAccessFile) {
        file.seek(0)
        val magic = ByteArray(MAGIC.size)
        file.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw IOException("Not a vault file")
        if (file.readInt() != chunkSize) throw IOException("Unsupported vault chunk size")
    }

    /** Decrypts chunk [index] (0-based). Throws IOException on corruption or tampering. */
    fun readChunk(file: RandomAccessFile, index: Long): ByteArray {
        val offset = HEADER_SIZE + index * stride
        if (index < 0 || offset >= file.length()) throw IOException("Chunk out of range")
        file.seek(offset)
        val ctLen = file.readInt()
        if (ctLen <= TAG_SIZE || ctLen > chunkSize + TAG_SIZE) throw IOException("Corrupt chunk header")
        val iv = ByteArray(IV_SIZE)
        file.readFully(iv)
        val ciphertext = ByteArray(ctLen)
        file.readFully(ciphertext)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_SIZE * 8, iv))
            cipher.updateAAD(aad(index))
            return cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw IOException("Vault data is corrupted or has been tampered with", e)
        }
    }

    /** Decrypts a whole vault file into [output]. */
    fun decryptTo(file: File, output: OutputStream) {
        RandomAccessFile(file, "r").use { raf ->
            checkHeader(raf)
            val plain = plainLength(raf.length())
            val chunks = (plain + chunkSize - 1) / chunkSize
            for (i in 0 until chunks) {
                output.write(readChunk(raf, i))
            }
            output.flush()
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun intToBytes(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun aad(index: Long): ByteArray = ByteBuffer.allocate(8).putLong(index).array()

    companion object {
        const val DEFAULT_CHUNK_SIZE = 1 shl 20
        const val MIN_CHUNK_SIZE = 16
        const val MAX_CHUNK_SIZE = 1 shl 24
        const val IV_SIZE = 12
        const val TAG_SIZE = 16
        const val HEADER_SIZE = 8L
        /** ctLen field + IV + GCM tag. */
        const val RECORD_OVERHEAD = 4L + IV_SIZE + TAG_SIZE
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
    }
}
