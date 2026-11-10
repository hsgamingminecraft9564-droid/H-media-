package com.hmedia.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Language tag to display name. Strings fall back to English until values-xx folders are added.
val LANGUAGES = listOf(
    "en" to "English", "ja" to "日本語", "ko" to "한국어", "hi" to "हिन्दी",
    "ru" to "Русский", "ne" to "नेपाली", "ur" to "اردو", "ar" to "العربية",
    "zh" to "中文", "es" to "Español", "la" to "Latina", "fr" to "Français",
    "de" to "Deutsch", "pt" to "Português", "it" to "Italiano", "tr" to "Türkçe",
    "id" to "Bahasa Indonesia", "vi" to "Tiếng Việt", "th" to "ไทย", "bn" to "বাংলা"
)

// Each setting has a private state holder, a read-only property, and one setX function.
// Because the state is private, no generated JVM setter clashes with the setX function.
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("hmedia", Context.MODE_PRIVATE)

    private var themeState by mutableStateOf(enumOr(sp.getString("theme", null), HTheme.BLOOD_IRON))
    val theme: HTheme get() = themeState
    fun setTheme(t: HTheme) {
        themeState = t
        sp.edit().putString("theme", t.name).apply()
    }

    private var fontState by mutableStateOf(enumOr(sp.getString("font", null), HFont.MEDIEVAL))
    val font: HFont get() = fontState
    fun setFont(f: HFont) {
        fontState = f
        sp.edit().putString("font", f.name).apply()
    }

    private var languageState by mutableStateOf(sp.getString("lang", "en") ?: "en")
    val language: String get() = languageState
    fun setLanguage(tag: String) {
        languageState = tag
        sp.edit().putString("lang", tag).apply()
    }

    private var privateState by mutableStateOf(sp.getBoolean("private", false))
    val privateMode: Boolean get() = privateState
    fun setPrivateMode(on: Boolean) {
        privateState = on
        sp.edit().putBoolean("private", on).apply()
    }

    private var consentState by mutableStateOf(sp.getBoolean("consent", false))
    val consented: Boolean get() = consentState
    fun setConsent(on: Boolean) {
        consentState = on
        sp.edit().putBoolean("consent", on).apply()
    }

    // Scripts on web result pages. On by default, because most pages need them; can be turned off in Settings.
    private var webScriptsState by mutableStateOf(sp.getBoolean("web_scripts", true))
    val webScripts: Boolean get() = webScriptsState
    fun setWebScripts(on: Boolean) {
        webScriptsState = on
        sp.edit().putBoolean("web_scripts", on).apply()
    }

    private var recentState by mutableStateOf(
        (sp.getString("recent", "") ?: "").split("\n").filter { it.isNotBlank() }
    )
    val recent: List<String> get() = recentState

    // Private Mode keeps nothing: no recent searches are saved.
    fun remember(query: String) {
        if (privateMode || query.isBlank()) return
        recentState = (listOf(query) + recentState.filter { it != query }).take(20)
        sp.edit().putString("recent", recentState.joinToString("\n")).apply()
    }

    fun clearRecent() {
        recentState = emptyList()
        sp.edit().remove("recent").apply()
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
