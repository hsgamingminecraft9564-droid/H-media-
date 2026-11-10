package com.hmedia.voidengine.features.subtitles

import com.hmedia.voidengine.ExternalSubtitle
import com.hmedia.voidengine.features.url.SourceClassifier

/** External subtitle helpers. Embedded subtitles are handled by the backends through track selection. */
object SubtitleFormats {
    const val SUBRIP = "application/x-subrip"
    const val WEBVTT = "text/vtt"
    const val SSA = "text/x-ssa"
    const val TTML = "application/ttml+xml"

    /** MIME type from a file name or URI, or null when unknown. */
    fun mimeFor(nameOrUri: String): String? = when (SourceClassifier.extensionOf(nameOrUri)) {
        "srt" -> SUBRIP
        "vtt" -> WEBVTT
        "ass", "ssa" -> SSA
        "ttml", "dfxp", "xml" -> TTML
        else -> null
    }

    fun external(uri: String, language: String? = null, label: String? = null): ExternalSubtitle =
        ExternalSubtitle(uri = uri, mimeType = mimeFor(uri), language = language, label = label)
}
