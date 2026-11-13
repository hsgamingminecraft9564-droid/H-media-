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

    // Removes one search from the history
    fun forgetRecent(term: String) {
        recentState = recentState.filter { it != term }
        sp.edit().putString("recent", recentState.joinToString("\n")).apply()
    }

    // Guest profile, saved only on this phone
    private var profileNameState by mutableStateOf(sp.getString("profile_name", "") ?: "")
    val profileName: String get() = profileNameState
    private var profileAgeState by mutableStateOf(sp.getString("profile_age", "") ?: "")
    val profileAge: String get() = profileAgeState
    private var profileGenderState by mutableStateOf(sp.getString("profile_gender", "") ?: "")
    val profileGender: String get() = profileGenderState
    private var profilePhotoState by mutableStateOf(sp.getString("profile_photo", "") ?: "")
    val profilePhoto: String get() = profilePhotoState

    fun saveProfile(name: String, age: String, gender: String) {
        profileNameState = name.trim().take(40)
        profileAgeState = age.trim().take(3)
        profileGenderState = gender
        sp.edit()
            .putString("profile_name", profileNameState)
            .putString("profile_age", profileAgeState)
            .putString("profile_gender", profileGenderState)
            .apply()
    }

    fun setProfilePhoto(uri: String) {
        profilePhotoState = uri
        sp.edit().putString("profile_photo", uri).apply()
    }

    // Favourite IPTV channels, kept by their address
    private var favoritesState by mutableStateOf(sp.getStringSet("iptv_favorites", emptySet()) ?: emptySet<String>())
    val favorites: Set<String> get() = favoritesState

    fun toggleFavorite(url: String) {
        val next = favoritesState.toMutableSet()
        if (!next.add(url)) next.remove(url)
        favoritesState = next
        sp.edit().putStringSet("iptv_favorites", next).apply()
    }

    // Picture chosen from the gallery for the background; empty means the default background
    private var backgroundPathState by mutableStateOf(sp.getString("background_path", "") ?: "")
    val backgroundPath: String get() = backgroundPathState

    fun setBackgroundPath(path: String) {
        backgroundPathState = path
        sp.edit().putString("background_path", path).apply()
    }

    // The search engine chosen in Settings (Google by default)
    private var searchProviderState by mutableStateOf(sp.getString("search_provider", "google") ?: "google")
    val searchProvider: String get() = searchProviderState

    fun setSearchProvider(key: String) {
        searchProviderState = key
        sp.edit().putString("search_provider", key).apply()
    }

    fun clearRecent() {
        recentState = emptyList()
        sp.edit().remove("recent").apply()
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
