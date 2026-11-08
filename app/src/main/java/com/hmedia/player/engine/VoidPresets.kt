package com.hmedia.player.engine

data class VoidPreset(
    val id: String,
    val name: String,
    val note: String,
    val libOptions: List<String>,
    val mediaOptions: List<String> = emptyList()
)

// Each Void preset is a set of libVLC options for one kind of use.
// Tune the numbers on a real phone; they are starting points.
object VoidPresets {
    const val DEFAULT_ID = "balanced"

    private const val AUDIO = "--aout=opensles"
    private const val HW = "--avcodec-hw=any"
    private const val SW = "--avcodec-hw=none"
    private const val RECONNECT = "--http-reconnect"

    val ALL: List<VoidPreset> = listOf(
        VoidPreset("balanced", "Void Balanced", "Default for files and streams",
            listOf(AUDIO, HW, "--network-caching=1500", RECONNECT)),
        VoidPreset("smooth_iptv", "Void Smooth IPTV", "Big buffer so live TV rarely stutters",
            listOf(AUDIO, HW, "--network-caching=8000", "--live-caching=8000", RECONNECT)),
        VoidPreset("weak_network", "Void Weak Network", "Longest buffer for slow or unstable links",
            listOf(AUDIO, HW, "--network-caching=15000", "--live-caching=15000", "--rtsp-tcp", RECONNECT)),
        VoidPreset("low_latency", "Void Low Latency", "Shortest delay for live sports",
            listOf(AUDIO, HW, "--network-caching=300", "--live-caching=300")),
        VoidPreset("hardware", "Void Hardware Decode", "Uses the phone's video chip, saves battery",
            listOf(AUDIO, HW, "--network-caching=1500")),
        VoidPreset("software", "Void Software Decode", "Most compatible, use if video glitches",
            listOf(AUDIO, SW, "--network-caching=1500")),
        VoidPreset("battery", "Void Battery Saver", "Fewer decoder threads, lighter buffering",
            listOf(AUDIO, HW, "--avcodec-threads=2", "--network-caching=2000")),
        VoidPreset("audio_focus", "Void Audio Focus", "Music and podcasts, video off",
            listOf(AUDIO, HW, "--network-caching=3000"), listOf(":no-video")),
        VoidPreset("radio", "Void Radio", "Internet radio, audio only, long buffer",
            listOf(AUDIO, "--network-caching=4000", RECONNECT), listOf(":no-video")),
        VoidPreset("cinema", "Void Cinema", "Automatic deinterlacing for sharper movies",
            listOf(AUDIO, HW, "--network-caching=2000"), listOf(":deinterlace=-1")),
        VoidPreset("subtitles", "Void Subtitles Plus", "Bigger subtitles for easy reading",
            listOf(AUDIO, HW, "--sub-text-scale=150", "--network-caching=1500")),
        VoidPreset("high_bitrate", "Void High Bitrate", "4K and high-bitrate files",
            listOf(AUDIO, HW, "--network-caching=4000", "--file-caching=3000")),
        VoidPreset("low_memory", "Void Low Memory", "Small buffers for older phones",
            listOf(AUDIO, HW, "--network-caching=1000", "--file-caching=1000", "--avcodec-threads=1")),
        VoidPreset("old_device", "Void Old Device", "Software decode with a safe buffer",
            listOf(AUDIO, SW, "--network-caching=3000", RECONNECT))
    )

    fun byId(id: String): VoidPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
