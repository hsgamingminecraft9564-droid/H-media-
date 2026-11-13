package com.hmedia.player

import android.net.Uri

// The search engines the user can choose in Settings. The app only builds the address of each results page;
// the results are the engine's own pages, shown inside the app.
data class SearchProvider(
    val key: String,
    val name: String,
    val home: String,
    val web: String,
    val images: String,
    val videos: String,
    val news: String
)

object SearchProviders {
    val all = listOf(
        SearchProvider(
            key = "google", name = "Google", home = "https://www.google.com/",
            web = "https://www.google.com/search?q=",
            images = "https://www.google.com/search?tbm=isch&q=",
            videos = "https://www.google.com/search?tbm=vid&q=",
            news = "https://www.google.com/search?tbm=nws&q="
        ),
        SearchProvider(
            key = "bing", name = "Bing", home = "https://www.bing.com/",
            web = "https://www.bing.com/search?q=",
            images = "https://www.bing.com/images/search?q=",
            videos = "https://www.bing.com/videos/search?q=",
            news = "https://www.bing.com/news/search?q="
        ),
        SearchProvider(
            key = "duckduckgo", name = "DuckDuckGo", home = "https://duckduckgo.com/",
            web = "https://duckduckgo.com/?q=",
            images = "https://duckduckgo.com/?iax=images&ia=images&q=",
            videos = "https://duckduckgo.com/?iax=videos&ia=videos&q=",
            news = "https://duckduckgo.com/?iar=news&ia=news&q="
        ),
        SearchProvider(
            key = "brave", name = "Brave Search", home = "https://search.brave.com/",
            web = "https://search.brave.com/search?q=",
            images = "https://search.brave.com/images?q=",
            videos = "https://search.brave.com/videos?q=",
            news = "https://search.brave.com/news?q="
        ),
        SearchProvider(
            key = "ecosia", name = "Ecosia", home = "https://www.ecosia.org/",
            web = "https://www.ecosia.org/search?q=",
            images = "https://www.ecosia.org/images?q=",
            videos = "https://www.ecosia.org/videos?q=",
            news = "https://www.ecosia.org/news?q="
        )
    )

    // Falls back to Google if the saved choice is unknown
    fun find(key: String): SearchProvider = all.firstOrNull { it.key == key } ?: all.first()

    fun homeUrl(key: String): String = find(key).home

    // The results address for the chosen category; the query is percent-encoded
    fun resultsUrl(key: String, category: WebCategory, query: String): String {
        val provider = find(key)
        val prefix = when (category) {
            WebCategory.WEB -> provider.web
            WebCategory.IMAGES -> provider.images
            WebCategory.VIDEOS -> provider.videos
            WebCategory.NEWS -> provider.news
        }
        return prefix + Uri.encode(query)
    }
}
