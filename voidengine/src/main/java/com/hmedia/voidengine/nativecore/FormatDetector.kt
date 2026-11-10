package com.hmedia.voidengine.nativecore

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.hmedia.voidengine.BackendNames
import com.hmedia.voidengine.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 7, component 1 ("source and format detection"): looks inside a file with the platform's
 * MediaExtractor and checks that the device has a decoder for every track. Run it before playback and
 * put the suggestion into PlaybackSource.preferredBackend. Blocking IO, so it runs on Dispatchers.IO.
 */
object FormatDetector {

    data class TrackFormat(val mime: String, val decoderAvailable: Boolean)

    data class Result(val tracks: List<TrackFormat>) {
        val hasVideo: Boolean get() = tracks.any { it.mime.startsWith("video/") }
        val hasAudio: Boolean get() = tracks.any { it.mime.startsWith("audio/") }
        val allDecodable: Boolean get() = tracks.isNotEmpty() && tracks.all { it.decoderAvailable }
    }

    /** null = the platform could not read the container (VLC is the better bet). */
    suspend fun detect(context: Context, source: PlaybackSource): Result? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            val uri = Uri.parse(source.uri)
            if (uri.scheme == "http" || uri.scheme == "https") {
                extractor.setDataSource(source.uri, source.headers)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val tracks = ArrayList<TrackFormat>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                tracks.add(TrackFormat(mime, codecs.findDecoderForFormat(format) != null))
            }
            Result(tracks)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /** Media3 when the device can decode everything, otherwise VLC (software decoders for almost anything). */
    fun suggestBackend(result: Result?): String =
        if (result != null && result.allDecodable) BackendNames.MEDIA3 else BackendNames.VLC
}
