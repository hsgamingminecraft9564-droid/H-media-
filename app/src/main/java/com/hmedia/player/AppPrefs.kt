package com.hmedia.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hmedia.player.engine.EngineLearner
import com.hmedia.player.engine.VoidPresets

// Language tag to display name. Strings fall back to English until values-xx folders are added.
val LANGUAGES = listOf(
    "en" to "English", "ja" to "日本語", "ko" to "한국어", "hi" to "हिन्दी",
    "ru" to "Русский", "ne" to "नेपाली", "ur" to "اردو", "ar" to "العربية",
    "zh" to "中文", "es" to "Español", "la" to "Latina", "fr" to "Français",
    "de" to "Deutsch", "pt" to "Português", "it" to "Italiano", "tr" to "Türkçe",
    "id" to "Bahasa Indonesia", "vi" to "Tiếng Việt", "th" to "ไทย", "bn" to "বাংলা"
)

class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("hmedia", Context.MODE_PRIVATE)

    var theme by mutableStateOf(enumOr(sp.getString("theme", null), HTheme.BLOOD_IRON))
        private set
    var font by mutableStateOf(enumOr(sp.getString("font", null), HFont.MEDIEVAL))
        private set
    var language by mutableStateOf(sp.getString("lang", "en") ?: "en")
        private set
    var privateMode by mutableStateOf(sp.getBoolean("private", false))
        private set
    var consented by mutableStateOf(sp.getBoolean("consent", false))
        private set
    var recent by mutableStateOf(
        (sp.getString("recent", "") ?: "").split("\n").filter { it.isNotBlank() }
    )
        private set

    fun setTheme(t: HTheme) { theme = t; sp.edit().putString("theme", t.name).apply() }
    fun setFont(f: HFont) { font = f; sp.edit().putString("font", f.name).apply() }
    fun setLanguage(tag: String) { language = tag; sp.edit().putString("lang", tag).apply() }
    // Scripts on web result pages. On by default, because most pages need them; can be turned off in Settings.
    var webScripts by mutableStateOf(sp.getBoolean("web_scripts", true))
        private set
    fun setWebScripts(on: Boolean) { webScripts = on; sp.edit().putBoolean("web_scripts", on).apply() }
    fun setPrivateMode(on: Boolean) { privateMode = on; sp.edit().putBoolean("private", on).apply() }
    fun setConsent(on: Boolean) { consented = on; sp.edit().putBoolean("consent", on).apply() }

    // Private Mode keeps nothing: no recent searches are saved.
    fun remember(query: String) {
        if (privateMode || query.isBlank()) return
        recent = (listOf(query) + recent.filter { it != query }).take(20)
        sp.edit().putString("recent", recent.joinToString("\n")).apply()
    }

    var engineId by mutableStateOf(sp.getString("engine", VoidPresets.DEFAULT_ID) ?: VoidPresets.DEFAULT_ID)
        private set
    fun setEngine(id: String) { engineId = id; sp.edit().putString("engine", id).apply() }

    val learner = EngineLearner(context)
    var smartEngine by mutableStateOf(sp.getBoolean("smart", true))
        private set
    fun setSmartEngine(on: Boolean) { smartEngine = on; sp.edit().putBoolean("smart", on).apply() }

    fun clearRecent() { recent = emptyList(); sp.edit().remove("recent").apply() }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
