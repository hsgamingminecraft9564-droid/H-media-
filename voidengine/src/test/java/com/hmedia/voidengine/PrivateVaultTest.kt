package com.hmedia.voidengine

import com.hmedia.voidengine.features.vault.PrivateVault
import com.hmedia.voidengine.features.vault.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.crypto.KeyGenerator

class PrivateVaultTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun vault(dir: File) = PrivateVault(dir, VaultCrypto({ key }, 64))
    private fun data(n: Int) = ByteArray(n) { (it * 17 + 3).toByte() }

    @Test
    fun addListExportRoundTrip() {
        val dir = tmp.newFolder("v1")
        val vault = vault(dir)
        val original = data(500)
        val entry = vault.addFile("movie.mp4", ByteArrayInputStream(original))
        assertEquals(500L, entry.sizeBytes)
        val listed = vault.list()
        assertEquals(1, listed.size)
        assertEquals("movie.mp4", listed[0].displayName)
        val out = ByteArrayOutputStream()
        vault.exportTo(entry.id, out)
        assertArrayEquals(original, out.toByteArray())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun failedWriteLeavesNoFiles() {
        val dir = tmp.newFolder("v2")
        val vault = vault(dir)
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("boom")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("boom")
        }
        try {
            vault.addFile("x", broken)
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
        assertEquals(0, dir.listFiles()!!.size)
        assertTrue(vault.list().isEmpty())
    }

    @Test
    fun staleTempFilesAreRemovedOnOpen() {
        val dir = tmp.newFolder("v3")
        val stale = File(dir, "abc.vlt.tmp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        vault(dir)
        assertFalse(stale.exists())
    }
}
