package com.hmedia.voidengine.features.vault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.RandomAccessFile

/** Media3 data source that decrypts vault:// files on the fly with full seek support. */
class VaultDataSource(private val vault: PrivateVault) : BaseDataSource(/* isNetwork = */ false) {

    private var uri: Uri? = null
    private var file: RandomAccessFile? = null
    private var position = 0L
    private var remaining = 0L
    private var cachedIndex = -1L
    private var cachedChunk: ByteArray? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val id = dataSpec.uri.authority ?: throw IOException("Invalid vault uri")
        val raf = vault.openRandomAccess(id)
        file = raf
        val plainLength: Long = try {
            val length = vault.plainLength(raf.length())
            if (dataSpec.position > length) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            length
        } catch (e: IOException) {
            close()
            throw e
        }
        position = dataSpec.position
        val available = plainLength - position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(dataSpec.length, available)
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val raf = file ?: throw IOException("Data source is not open")
        val size = vault.chunkSize.toLong()
        val index = position / size
        val inChunk = (position % size).toInt()
        val chunk = chunkAt(raf, index)
        val count = minOf(length.toLong(), remaining, (chunk.size - inChunk).toLong()).toInt()
        if (count <= 0) return C.RESULT_END_OF_INPUT
        System.arraycopy(chunk, inChunk, buffer, offset, count)
        position += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        cachedChunk = null
        cachedIndex = -1L
        try {
            file?.close()
        } finally {
            file = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun chunkAt(raf: RandomAccessFile, index: Long): ByteArray {
        val cached = cachedChunk
        if (cached != null && cachedIndex == index) return cached
        val chunk = vault.readChunk(raf, index)
        cachedChunk = chunk
        cachedIndex = index
        return chunk
    }
}

class VaultDataSourceFactory(private val vault: PrivateVault) : DataSource.Factory {
    override fun createDataSource(): DataSource = VaultDataSource(vault)
}
