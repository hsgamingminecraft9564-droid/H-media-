package com.hmedia.voidengine.features.vault

import com.hmedia.voidengine.BackendNames
import com.hmedia.voidengine.PlaybackSource
import com.hmedia.voidengine.SourceType
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.UUID

data class VaultEntry(val id: String, val displayName: String, val sizeBytes: Long)

/**
 * Encrypted file store. Contents AND display names are encrypted; file names on disk are random ids.
 * Lock screen / biometrics are the UI's job (use VaultKeys.authValiditySeconds + BiometricPrompt).
 */
class PrivateVault(private val directory: File, private val crypto: VaultCrypto) {

    init {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create vault directory")
        }
        // Remove leftovers of writes interrupted by a crash (runs once, before any new write).
        directory.listFiles { file -> file.name.endsWith(TMP_SUFFIX) }?.forEach { it.delete() }
    }

    val chunkSize: Int get() = crypto.chunkSize

    fun addFile(displayName: String, input: InputStream): VaultEntry {
        val id = UUID.randomUUID().toString()
        val data = dataFile(id)
        val meta = metaFile(id)
        // Write to temp files, fsync, then rename: a crash never leaves a half-written entry
        // under its real name. Metadata is committed first; an entry is listed only when its
        // .vlt file exists, so the data file is renamed last.
        val dataTmp = File(directory, data.name + TMP_SUFFIX)
        val metaTmp = File(directory, meta.name + TMP_SUFFIX)
        try {
            FileOutputStream(dataTmp).use { fos ->
                // Do NOT close the buffered stream here: that would close fos before the sync.
                val buffered = BufferedOutputStream(fos)
                crypto.encrypt(input, buffered)
                buffered.flush()
                fos.fd.sync()
            }
            FileOutputStream(metaTmp).use { fos ->
                crypto.encrypt(ByteArrayInputStream(displayName.toByteArray(Charsets.UTF_8)), fos)
                fos.fd.sync()
            }
            if (!metaTmp.renameTo(meta)) throw IOException("Cannot commit vault metadata")
            if (!dataTmp.renameTo(data)) throw IOException("Cannot commit vault data")
        } catch (e: Exception) {
            dataTmp.delete()
            metaTmp.delete()
            data.delete()
            meta.delete()
            throw e
        }
        return VaultEntry(id, displayName, crypto.plainLength(data.length()))
    }

    fun list(): List<VaultEntry> {
        val files = directory.listFiles { file -> file.name.endsWith(DATA_SUFFIX) } ?: return emptyList()
        val entries = ArrayList<VaultEntry>()
        for (file in files.sortedBy { it.name }) {
            val id = file.name.removeSuffix(DATA_SUFFIX)
            try {
                entries.add(VaultEntry(id, readName(id), crypto.plainLength(file.length())))
            } catch (e: IOException) {
                // skip unreadable entries instead of failing the whole list
            }
        }
        return entries
    }

    fun delete(id: String): Boolean {
        val dataDeleted = dataFile(id).delete()
        val metaDeleted = metaFile(id).delete()
        return dataDeleted || metaDeleted
    }

    fun exportTo(id: String, output: OutputStream) {
        val file = dataFile(id)
        if (!file.exists()) throw FileNotFoundException("No such vault entry")
        crypto.decryptTo(file, output)
    }

    /** Source to hand to VoidEngine; requires an engine created with VaultDataSourceFactory(this). */
    fun sourceFor(entry: VaultEntry): PlaybackSource = PlaybackSource(
        uri = "$SCHEME://${entry.id}",
        type = SourceType.LOCAL_FILE,
        title = entry.displayName,
        preferredBackend = BackendNames.MEDIA3
    )

    // ---- used by VaultDataSource ----

    /** Opens the encrypted file for random access and validates its header. Caller closes it. */
    fun openRandomAccess(id: String): RandomAccessFile {
        val file = dataFile(id)
        if (!file.exists()) throw FileNotFoundException("No such vault entry")
        val raf = RandomAccessFile(file, "r")
        try {
            crypto.checkHeader(raf)
        } catch (e: IOException) {
            raf.close()
            throw e
        }
        return raf
    }

    fun plainLength(fileLength: Long): Long = crypto.plainLength(fileLength)

    fun readChunk(file: RandomAccessFile, index: Long): ByteArray = crypto.readChunk(file, index)

    // ---- internals ----

    private fun readName(id: String): String {
        val out = ByteArrayOutputStream()
        crypto.decryptTo(metaFile(id), out)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun dataFile(id: String): File = File(directory, validId(id) + DATA_SUFFIX)

    private fun metaFile(id: String): File = File(directory, validId(id) + META_SUFFIX)

    private fun validId(id: String): String {
        if (!ID_PATTERN.matches(id)) throw IllegalArgumentException("Invalid vault id")
        return id
    }

    companion object {
        const val SCHEME = "vault"
        private const val DATA_SUFFIX = ".vlt"
        private const val META_SUFFIX = ".meta"
        private const val TMP_SUFFIX = ".tmp"
        private val ID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
