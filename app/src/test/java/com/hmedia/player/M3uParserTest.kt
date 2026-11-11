package com.hmedia.player

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {
    @Test
    fun readsNameGroupAndAddress() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news" group-title="News",Example News
            https://example.com/news.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("Example News", channels[0].name)
        assertEquals("News", channels[0].group)
        assertEquals("https://example.com/news.m3u8", channels[0].url)
    }

    @Test
    fun ignoresUnsupportedAddresses() {
        val text = "#EXTINF:-1,Bad\nfile:///sdcard/x.mp4\n#EXTINF:-1,Good\nrtmp://example.com/live\n"
        val channels = M3uParser.parse(text)
        assertEquals(listOf("Good"), channels.map { it.name })
    }

    @Test
    fun usesAddressWhenNameIsMissing() {
        val channels = M3uParser.parse("http://example.com/a.ts")
        assertEquals("http://example.com/a.ts", channels[0].name)
    }

    @Test
    fun keepsCommasInTheChannelName() {
        val channels = M3uParser.parse("#EXTINF:-1,Sports, Live HD\nhttp://example.com/s.m3u8")
        assertEquals("Sports, Live HD", channels[0].name)
    }

    @Test
    fun keepsCommasInsideQuotedAttributes() {
        val text = "#EXTINF:-1 group-title=\"News, World\",Daily\nhttp://example.com/d.m3u8"
        val channels = M3uParser.parse(text)
        assertEquals("Daily", channels[0].name)
        assertEquals("News, World", channels[0].group)
    }

    @Test
    fun usesDefaultNameWhenTheNameIsEmpty() {
        val channels = M3uParser.parse("#EXTINF:-1,\nhttp://example.com/x.m3u8")
        assertEquals("Channel", channels[0].name)
    }

    @Test
    fun ignoresAByteOrderMarkAtTheStart() {
        val channels = M3uParser.parse("\uFEFFhttp://example.com/a.ts")
        assertEquals(1, channels.size)
    }

    @Test
    fun doesNotCarryOneChannelsNameToTheNext() {
        val text = "#EXTINF:-1 group-title=\"A\",First\nhttp://example.com/1\nhttp://example.com/2"
        val channels = M3uParser.parse(text)
        assertEquals(2, channels.size)
        assertEquals("http://example.com/2", channels[1].name)
        assertEquals("", channels[1].group)
    }

    @Test
    fun stopsAtTheChannelLimit() {
        val text = (1..M3uParser.MAX_CHANNELS + 50).joinToString("\n") { "http://example.com/$it" }
        assertEquals(M3uParser.MAX_CHANNELS, M3uParser.parse(text).size)
    }
}
