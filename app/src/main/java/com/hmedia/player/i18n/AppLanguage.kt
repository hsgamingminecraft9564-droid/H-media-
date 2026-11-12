package com.hmedia.player.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Per-app language controller. Pass null (or "system") for device default. */
object AppLanguage {
    data class Language(val tag: String, val nativeName: String, val englishName: String)

    val supported: List<Language> = listOf(
        Language("en", "English", "English"),
        Language("ne", "नेपाली", "Nepali"),
        Language("hi", "हिन्दी", "Hindi"),
        Language("ru", "Русский", "Russian"),
        Language("es", "Español", "Spanish"),
        Language("zh-Hans", "简体中文", "Chinese (Simplified)"),
        Language("zh-Hant", "繁體中文", "Chinese (Traditional)"),
        Language("ja", "日本語", "Japanese"),
        Language("ko", "한국어", "Korean"),
        Language("fr", "Français", "French"),
        Language("de", "Deutsch", "German"),
        Language("pt", "Português", "Portuguese"),
        Language("pt-BR", "Português (Brasil)", "Portuguese (Brazil)"),
        Language("ar", "العربية", "Arabic"),
        Language("it", "Italiano", "Italian"),
        Language("id", "Bahasa Indonesia", "Indonesian"),
        Language("tr", "Türkçe", "Turkish"),
        Language("bn", "বাংলা", "Bengali"),
        Language("ur", "اردو", "Urdu"),
        Language("pl", "Polski", "Polish"),
        Language("th", "ไทย", "Thai"),
        Language("vi", "Tiếng Việt", "Vietnamese"),
        Language("uk", "Українська", "Ukrainian"),
        Language("nl", "Nederlands", "Dutch"),
        Language("el", "Ελληνικά", "Greek"),
        Language("fa", "فارسی", "Persian"),
        Language("ms", "Bahasa Melayu", "Malay"),
        Language("sw", "Kiswahili", "Swahili"),
        Language("gu", "ગુજરાતી", "Gujarati"),
        Language("ta", "தமிழ்", "Tamil"),
        Language("te", "తెలుగు", "Telugu"),
        Language("mr", "मराठी", "Marathi"),
        Language("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        Language("as", "অসমীয়া", "Assamese"),
        Language("or", "ଓଡ଼ିଆ", "Odia"),
        Language("ml", "മലയാളം", "Malayalam"),
        Language("kn", "ಕನ್ನಡ", "Kannada"),
        Language("si", "සිංහල", "Sinhala"),
        Language("my", "မြန်မာ", "Burmese"),
        Language("km", "ខ្មែរ", "Khmer"),
        Language("lo", "ລາວ", "Lao"),
        Language("mn", "Монгол", "Mongolian"),
        Language("uz", "O‘zbek", "Uzbek"),
        Language("kk", "Қазақша", "Kazakh"),
        Language("az", "Azərbaycanca", "Azerbaijani"),
        Language("hy", "Հայերեն", "Armenian"),
        Language("ka", "ქართული", "Georgian"),
        Language("he", "עברית", "Hebrew"),
        Language("sq", "Shqip", "Albanian"),
        Language("sr", "Српски", "Serbian"),
        Language("hr", "Hrvatski", "Croatian"),
        Language("bs", "Bosanski", "Bosnian"),
        Language("sl", "Slovenščina", "Slovenian"),
        Language("sk", "Slovenčina", "Slovak"),
        Language("cs", "Čeština", "Czech"),
        Language("hu", "Magyar", "Hungarian"),
        Language("ro", "Română", "Romanian"),
        Language("bg", "Български", "Bulgarian"),
        Language("mk", "Македонски", "Macedonian"),
        Language("da", "Dansk", "Danish"),
        Language("sv", "Svenska", "Swedish"),
        Language("no", "Norsk", "Norwegian"),
        Language("fi", "Suomi", "Finnish"),
        Language("is", "Íslenska", "Icelandic"),
        Language("et", "Eesti", "Estonian"),
        Language("lv", "Latviešu", "Latvian"),
        Language("lt", "Lietuvių", "Lithuanian"),
        Language("ga", "Gaeilge", "Irish"),
        Language("cy", "Cymraeg", "Welsh"),
        Language("ca", "Català", "Catalan"),
        Language("eu", "Euskara", "Basque"),
        Language("gl", "Galego", "Galician"),
        Language("af", "Afrikaans", "Afrikaans"),
        Language("zu", "isiZulu", "Zulu"),
        Language("xh", "isiXhosa", "Xhosa"),
        Language("am", "አማርኛ", "Amharic"),
        Language("ha", "Hausa", "Hausa"),
        Language("yo", "Yorùbá", "Yoruba"),
        Language("ig", "Igbo", "Igbo"),
        Language("fil", "Filipino", "Filipino"),
        Language("jv", "Basa Jawa", "Javanese"),
        Language("su", "Basa Sunda", "Sundanese"),
        Language("ceb", "Cebuano", "Cebuano"),
        Language("la", "Latina", "Latin"),
        Language("ku", "Kurdî", "Kurdish"),
        Language("ps", "پښتو", "Pashto"),
        Language("sd", "سنڌي", "Sindhi"),
        Language("dv", "ދިވެހި", "Dhivehi"),
        Language("bo", "བོད་སྐད་", "Tibetan"),
        Language("ug", "ئۇيغۇرچە", "Uyghur"),
        Language("tk", "Türkmençe", "Turkmen"),
        Language("ky", "Кыргызча", "Kyrgyz"),
        Language("tg", "Тоҷикӣ", "Tajik"),
        Language("tt", "Татарча", "Tatar"),
        Language("br", "Brezhoneg", "Breton"),
        Language("lb", "Lëtzebuergesch", "Luxembourgish"),
        Language("mt", "Malti", "Maltese"),
        Language("fo", "Føroyskt", "Faroese"),
        Language("yi", "ייִדיש", "Yiddish"),
        Language("haw", "ʻŌlelo Hawaiʻi", "Hawaiian")

    )

    fun setLanguage(languageTag: String?) {
        val locales = if (languageTag.isNullOrBlank() || languageTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun currentLanguageTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.toLanguageTag()
    }
}
