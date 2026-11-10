package com.hmedia.voidengine.backend

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.SurfaceView
import com.hmedia.voidengine.BackendAdapter
import com.hmedia.voidengine.EqualizerSettings
import com.hmedia.voidengine.PlaybackError
import com.hmedia.voidengine.PlaybackSource
import com.hmedia.voidengine.PlaybackState
import com.hmedia.voidengine.PlayerSnapshot
import com.hmedia.voidengine.TrackInfo
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.IOException

/**
 * libVLC 3.x backend. This is the ONLY file (besides the Gradle dependency) that may import org.videolan.*.
 * Audio focus and "becoming noisy" are handled by the engine, not here.
 * Note: libVLC cannot send arbitrary HTTP headers; only User-Agent and Referer are forwarded.
 */
class VlcBackendAdapter(context: Context) : BackendAdapter {

    private val appContext: Context = context.applicationContext

    override var listener: BackendAdapter.Listener? = null

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null

    private var currentSource: PlaybackSource? = null
    private var pfd: ParcelFileDescriptor? = null
    private var attachedView: SurfaceView? = null
    private var userStarted = false
    private var lengthMs = PlayerSnapshot.UNKNOWN_DURATION
    private var lastTracksKey = ""

    init {
        val vlc = LibVLC(appContext, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
        libVlc = vlc
        val p = MediaPlayer(vlc)
        p.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
        p.setEventListener { event -> handleEvent(event) }
        player = p
    }

    override fun setSource(source: PlaybackSource) {
        val p = player ?: return
        val vlc = libVlc ?: return
        closeDescriptor()
        currentSource = source
        userStarted = false
        lengthMs = PlayerSnapshot.UNKNOWN_DURATION
        lastTracksKey = ""

        val uri = Uri.parse(source.uri)
        val media: Media = try {
            if (uri.scheme == "content") {
                val descriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("Cannot open $uri")
                pfd = descriptor
                Media(vlc, descriptor.fileDescriptor)
            } else {
                Media(vlc, uri)
            }
        } catch (e: Exception) {
            listener?.onError(PlaybackError.SourceNotFound("Cannot open source", e))
            return
        }

        media.setHWDecoderEnabled(true, false)
        if (source.startPositionMs > 0L) {
            media.addOption(":start-time=${source.startPositionMs / 1000.0}")
        }
        if (source.type.isRemote) {
            media.addOption(":network-caching=1500")
        }
        source.headers["User-Agent"]?.let { media.addOption(":http-user-agent=$it") }
        source.headers["Referer"]?.let { media.addOption(":http-referrer=$it") }
        for (subtitle in source.externalSubtitles) {
            media.addSlave(IMedia.Slave(IMedia.Slave.Type.Subtitle, 4, subtitle.uri))
        }
        p.setMedia(media)
        media.release()
    }

    // libVLC has no separate "ready" event, so prepare() reports READY once the media is set.
    override fun prepare() {
        if (currentSource != null) listener?.onState(PlaybackState.READY)
    }

    override fun play() {
        userStarted = true
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        userStarted = false
        player?.stop()
    }

    override fun seekTo(positionMs: Long) {
        player?.setTime(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        player?.setRate(speed)
    }

    override fun setVolume(volume: Float) {
        player?.setVolume((volume.coerceIn(0f, 1f) * 100f).toInt())
    }

    override fun selectAudioTrack(id: String?) {
        val trackId = id?.toIntOrNull() ?: return
        player?.setAudioTrack(trackId)
    }

    override fun selectSubtitleTrack(id: String?) {
        player?.setSpuTrack(id?.toIntOrNull() ?: -1)
    }

    override fun setEqualizer(settings: EqualizerSettings?) {
        val p = player ?: return
        if (settings == null || !settings.enabled) {
            p.setEqualizer(null)
            return
        }
        val eq = MediaPlayer.Equalizer.create()
        eq.setPreAmp(settings.preAmpDb)
        val bands = MediaPlayer.Equalizer.getBandCount()
        for (i in 0 until minOf(bands, settings.bandGainsDb.size)) {
            eq.setAmp(i, settings.bandGainsDb[i])
        }
        p.setEqualizer(eq)
    }

    override fun equalizerBandFrequenciesHz(): List<Int> {
        val count = MediaPlayer.Equalizer.getBandCount()
        return (0 until count).map { MediaPlayer.Equalizer.getBandFrequency(it).toInt() }
    }

    override fun attachVideoView(view: SurfaceView) {
        val p = player ?: return
        if (attachedView === view) return
        detachVideoView()
        val vout = p.vlcVout
        vout.setVideoView(view)
        vout.attachViews()
        attachedView = view
    }

    override fun detachVideoView() {
        if (attachedView == null) return
        player?.vlcVout?.detachViews()
        attachedView = null
    }

    override fun release() {
        listener = null
        val p = player
        player = null
        if (p != null) {
            p.setEventListener(null)
            p.stop()
            if (attachedView != null) {
                p.vlcVout.detachViews()
                attachedView = null
            }
            p.release()
        }
        libVlc?.release()
        libVlc = null
        closeDescriptor()
    }

    private fun closeDescriptor() {
        try {
            pfd?.close()
        } catch (e: IOException) {
            // ignore
        }
        pfd = null
    }

    private fun handleEvent(event: MediaPlayer.Event) {
        val l = listener ?: return
        when (event.type) {
            MediaPlayer.Event.Playing -> l.onState(PlaybackState.PLAYING)
            MediaPlayer.Event.Paused -> l.onState(PlaybackState.PAUSED)
            MediaPlayer.Event.EndReached -> l.onState(PlaybackState.ENDED)
            MediaPlayer.Event.Buffering -> {
                val percent = event.buffering.toInt()
                l.onBuffering(percent)
                if (userStarted) {
                    if (percent < 100) {
                        l.onState(PlaybackState.BUFFERING)
                    } else {
                        l.onState(if (player?.isPlaying == true) PlaybackState.PLAYING else PlaybackState.PAUSED)
                    }
                }
            }
            MediaPlayer.Event.LengthChanged -> {
                lengthMs = event.lengthChanged
                l.onPosition(player?.getTime() ?: 0L, lengthMs)
            }
            MediaPlayer.Event.TimeChanged -> l.onPosition(event.timeChanged, lengthMs)
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> publishTracks()
            MediaPlayer.Event.Vout -> publishVideoSize()
            MediaPlayer.Event.EncounteredError -> l.onError(errorForSource())
        }
    }

    private fun errorForSource(): PlaybackError {
        val remote = currentSource?.type?.isRemote == true
        return if (remote) PlaybackError.Network("VLC could not open or keep the stream")
        else PlaybackError.Backend("VLC could not play this file")
    }

    private fun publishVideoSize() {
        val track = player?.getCurrentVideoTrack() ?: return
        if (track.width > 0 && track.height > 0) {
            listener?.onVideoSize(track.width, track.height)
        }
    }

    private fun publishTracks() {
        val p = player ?: return
        val audio = (p.getAudioTracks() ?: emptyArray())
            .filter { it.id >= 0 }
            .map { TrackInfo(it.id.toString(), it.name ?: "Track ${it.id}") }
        val subtitles = (p.getSpuTracks() ?: emptyArray())
            .filter { it.id >= 0 }
            .map { TrackInfo(it.id.toString(), it.name ?: "Subtitle ${it.id}") }
        val selectedAudio = p.getAudioTrack().takeIf { it >= 0 }?.toString()
        val selectedSubtitle = p.getSpuTrack().takeIf { it >= 0 }?.toString()
        val key = "$audio|$subtitles|$selectedAudio|$selectedSubtitle"
        if (key == lastTracksKey) return
        lastTracksKey = key
        listener?.onTracks(audio, subtitles, selectedAudio, selectedSubtitle)
    }
}
