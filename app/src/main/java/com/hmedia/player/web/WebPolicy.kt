package com.hmedia.player.web

import java.net.URI

// Which pages the search result viewer may open. Pure rules, so they can be tested on a computer.
object WebPolicy {
    private const val MAX_LENGTH = 8192
    private const val HEX = "0123456789ABCDEF"

    // Characters that may stay as they are in a web address. Other characters are percent-encoded
    // first, the way a browser does, so a real link with a space, |, [, ] or an accented letter still opens.
    private const val KEEP = "-._~:/?#&=+@!$'()*,;"

    // A page may open only if it is a plain web address: http or https, with a host name, no username
    // or password (those can hide where a link really goes), and not absurdly long.
    fun isAllowedPage(raw: String?): Boolean {
        if (raw.isNullOrBlank() || raw.length > MAX_LENGTH) return false
        val uri = try {
            URI(encodeForUri(raw.trim()))
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (uri.host.isNullOrEmpty()) return false
        if (uri.rawUserInfo != null) return false
        return true
    }

    // Percent-encodes the characters that java.net.URI refuses. An existing %XX sequence is kept.
    internal fun encodeForUri(raw: String): String {
        val out = StringBuilder(raw.length + 16)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            val ch = raw[i]
            if (ch == '%' && i + 2 < raw.length && isHex(raw[i + 1]) && isHex(raw[i + 2])) {
                out.append('%')
            } else if (cp < 128 && (Character.isLetterOrDigit(cp) || KEEP.indexOf(ch) >= 0)) {
                out.append(ch)
            } else {
                val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
                for (b in bytes) {
                    out.append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
                }
            }
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
