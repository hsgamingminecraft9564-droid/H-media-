package com.hmedia.voidengine

import com.hmedia.voidengine.features.equalizer.EqualizerPresets
import com.hmedia.voidengine.features.epg.XmltvParser
import com.hmedia.voidengine.features.iptv.M3uParser
import com.hmedia.voidengine.features.lyrics.LrcParser
import com.hmedia.voidengine.features.subtitles.SubtitleFormats
import com.hmedia.voidengine.features.url.SourceClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FeaturesTest {

    // ---- direct URL ----
    @Test
    fun classifiesUrls() {
        assertEquals(SourceType.RTSP, SourceClassifier.classify("rtsp://h/x"))
        assertEquals(SourceType.HLS, SourceClassifier.classify("https://h/a.m3u8?token=1"))
        assertEquals(SourceType.DASH, SourceClassifier.classify("https://h/a.mpd"))
        assertEquals(SourceType.IPTV_STREAM, SourceClassifier.classify("https://h/live.ts"))
        assertEquals(SourceType.HTTP, SourceClassifier.classify("https://h/video.mp4"))
        assertEquals(SourceType.LOCAL_FILE, SourceClassifier.classify("file:///a.mp4"))
        assertEquals(SourceType.LOCAL_FILE, SourceClassifier.classify("/sdcard/a.mp4"))
        assertEquals(SourceType.LOCAL_FILE, SourceClassifier.classify("vault://abc"))
        assertEquals(SourceType.RTMP, SourceClassifier.classify("rtmp://h/live"))
        assertEquals(SourceType.RTP_UDP, SourceClassifier.classify("udp://@239.0.0.1:1234"))
        assertEquals(SourceType.RTP_UDP, SourceClassifier.classify("rtp://h:5004"))
        assertEquals(SourceType.MMS, SourceClassifier.classify("mmsh://h/stream"))
        assertEquals(SourceType.FTP, SourceClassifier.classify("ftp://h/a.mp4"))
        assertEquals(SourceType.UNKNOWN, SourceClassifier.classify("gopher://h/x"))
        assertEquals(SourceType.UNKNOWN, SourceClassifier.classify("movie.mp4"))
        assertTrue(SourceType.RTMP.isRemote)
        assertFalse(SourceType.UNKNOWN.isRemote)
    }

    @Test
    fun userInputSchemesAgreeWithClassifier() {
        for (scheme in listOf("http", "https", "rtsp", "rtsps", "rtmp", "rtmps", "rtmpt", "rtp", "udp", "mms", "mmsh", "ftp", "sftp", "smb")) {
            val source = SourceClassifier.fromUserInput("$scheme://host/path")
            assertNotNull(scheme, source)
            assertTrue(scheme, source!!.type != SourceType.UNKNOWN)
        }
        assertNull(SourceClassifier.fromUserInput("https://user:pw@host/a.mp4"))
    }

    @Test
    fun xmltvRejectsLongPrologue() {
        val padding = " ".repeat(70 * 1024)
        try {
            XmltvParser.parse("<?xml version=\"1.0\"?>$padding<!ENTITY x \"y\"><tv/>".byteInputStream())
            org.junit.Assert.fail("expected XmltvSecurityException")
        } catch (e: XmltvParser.XmltvSecurityException) {
            // expected
        }
    }

    @Test
    fun validatesSourceUrls() {
        assertNull(SourceClassifier.validationError("https://h/a.mp4"))
        assertNull(SourceClassifier.validationError("file:///a.mp4"))
        assertNull(SourceClassifier.validationError("udp://@239.0.0.1:1234"))
        assertNotNull(SourceClassifier.validationError("https://user:pw@h/a.mp4"))
        assertNotNull(SourceClassifier.validationError("https:///a.mp4"))
        assertNotNull(SourceClassifier.validationError("gopher://h/x"))
        assertNotNull(SourceClassifier.validationError("https://h/a\u0000.mp4"))
    }

    @Test
    fun xmltvRejectsEntityDeclarationsAndOversizedInput() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE tv [<!ENTITY x SYSTEM "file:///etc/passwd">]><tv>&x;</tv>"""
        try {
            XmltvParser.parse(xxe.byteInputStream())
            org.junit.Assert.fail("expected XmltvSecurityException")
        } catch (e: XmltvParser.XmltvSecurityException) {
            // expected
        }
        try {
            XmltvParser.parse("<tv>${"a".repeat(2000)}</tv>".byteInputStream(), maxBytes = 100)
            org.junit.Assert.fail("expected XmltvSecurityException")
        } catch (e: XmltvParser.XmltvSecurityException) {
            // expected
        }
        val plainDoctype = """<?xml version="1.0"?><!DOCTYPE tv SYSTEM "xmltv.dtd"><tv/>"""
        assertNotNull(XmltvParser.parse(plainDoctype.byteInputStream()))
    }

    @Test
    fun extractsExtensions() {
        assertEquals("mp4", SourceClassifier.extensionOf("https://h/a.MP4?x=1.2"))
        assertNull(SourceClassifier.extensionOf("https://host.com"))
        assertNull(SourceClassifier.extensionOf("https://h/stream"))
        assertEquals("srt", SourceClassifier.extensionOf("movie.en.srt"))
    }

    @Test
    fun parsesUserInput() {
        assertEquals(SourceType.HTTP, SourceClassifier.fromUserInput("  https://h/a.mp4 ")!!.type)
        assertEquals("file:///sdcard/a.mp4", SourceClassifier.fromUserInput("/sdcard/a.mp4")!!.uri)
        assertNull(SourceClassifier.fromUserInput("hello world"))
        assertNull(SourceClassifier.fromUserInput("javascript:alert(1)"))
        assertNull(SourceClassifier.fromUserInput(""))
    }

    // ---- subtitles ----
    @Test
    fun subtitleMimeTypes() {
        assertEquals(SubtitleFormats.SUBRIP, SubtitleFormats.mimeFor("movie.en.SRT"))
        assertEquals(SubtitleFormats.WEBVTT, SubtitleFormats.mimeFor("x.vtt"))
        assertEquals(SubtitleFormats.SSA, SubtitleFormats.mimeFor("x.ass"))
        assertNull(SubtitleFormats.mimeFor("x.txt"))
    }

    // ---- equalizer ----
    @Test
    fun equalizerPresets() {
        val flat = EqualizerPresets.forName(EqualizerPresets.FLAT, 5)
        assertEquals(5, flat.bandGainsDb.size)
        assertTrue(flat.bandGainsDb.all { it == 0f })
        assertFalse(flat.enabled)
        val bass = EqualizerPresets.forName(EqualizerPresets.BASS_BOOST, 5)
        assertTrue(bass.bandGainsDb.first() > bass.bandGainsDb.last())
        val treble = EqualizerPresets.forName(EqualizerPresets.TREBLE_BOOST, 5)
        assertTrue(treble.bandGainsDb.last() > treble.bandGainsDb.first())
        assertTrue(EqualizerPresets.forName(EqualizerPresets.VOCAL, 0).bandGainsDb.isEmpty())
    }

    // ---- lyrics ----
    @Test
    fun parsesLrcWithOffsetAndMultipleTimestamps() {
        val text = "[ar:Someone]\n[offset:500]\n[00:12.00]Hello\n[00:15.50][01:00.5]Chorus\n"
        val timeline = LrcParser.parse(text)
        assertEquals(3, timeline.lines.size)
        assertEquals(11_500L, timeline.lines[0].timeMs)
        assertEquals("Hello", timeline.lines[0].text)
        assertEquals(-1, timeline.indexAt(0L))
        assertEquals(0, timeline.indexAt(11_500L))
        assertEquals(1, timeline.indexAt(20_000L))
        assertEquals(2, timeline.indexAt(1_000_000L))
    }

    // ---- IPTV ----
    @Test
    fun parsesM3uPlaylist() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1" tvg-logo="http://l/x.png" group-title="News",BBC One, HD
            #EXTVLCOPT:http-user-agent=Test/1.0
            http://s/1.m3u8
            #EXTINF:-1,Plain
            http://s/2.ts
            http://s/3
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(3, channels.size)

        val first = channels[0]
        assertEquals("bbc1", first.id)
        assertEquals("BBC One, HD", first.name)
        assertEquals("News", first.group)
        assertEquals("http://l/x.png", first.logoUrl)
        assertEquals("Test/1.0", first.userAgent)
        val firstSource = first.toSource()
        assertEquals(SourceType.HLS, firstSource.type)
        assertEquals("Test/1.0", firstSource.headers["User-Agent"])

        assertEquals("Plain", channels[1].name)
        assertEquals(SourceType.IPTV_STREAM, channels[1].toSource().type)

        assertEquals("3", channels[2].name)
        assertEquals(SourceType.IPTV_STREAM, channels[2].toSource().type)
    }

    // ---- EPG ----
    @Test
    fun parsesXmltvGuide() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="bbc1"><display-name>BBC One</display-name></channel>
              <programme start="20261109120000 +0000" stop="20261109130000 +0000" channel="bbc1">
                <title>News</title><desc>Headlines</desc>
              </programme>
              <programme start="20261109150000 +0100" stop="20261109160000 +0100" channel="bbc1">
                <title>Show</title>
              </programme>
            </tv>
        """.trimIndent()
        val guide = XmltvParser.parse(xml.byteInputStream())
        assertEquals("BBC One", guide.channelNames["bbc1"])
        val list = guide.programmesByChannel["bbc1"]!!
        assertEquals(2, list.size)
        assertEquals(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(), list[0].startMs)
        assertEquals("Headlines", list[0].description)
        // 15:00 at +01:00 is 14:00 UTC
        assertEquals(Instant.parse("2026-11-09T14:00:00Z").toEpochMilli(), list[1].startMs)

        val now = Instant.parse("2026-11-09T12:30:00Z").toEpochMilli()
        val (current, next) = guide.nowAndNext("bbc1", now)
        assertEquals("News", current!!.title)
        assertEquals("Show", next!!.title)

        val early = Instant.parse("2026-11-09T11:00:00Z").toEpochMilli()
        val (none, upcoming) = guide.nowAndNext("bbc1", early)
        assertNull(none)
        assertNotNull(upcoming)
        assertEquals("News", upcoming!!.title)
    }
}
