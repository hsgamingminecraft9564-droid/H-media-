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
        ),
        SearchProvider(
            key = "yahoo", name = "Yahoo", home = "https://search.yahoo.com/",
            web = "https://search.yahoo.com/search?p=",
            images = "https://images.search.yahoo.com/search/images?p=",
            videos = "https://video.search.yahoo.com/search/video?p=",
            news = "https://news.search.yahoo.com/search?p="
        ),
        SearchProvider(
            key = "startpage", name = "Startpage", home = "https://www.startpage.com/",
            web = "https://www.startpage.com/do/search?query=",
            images = "https://www.startpage.com/do/search?cat=pics&query=",
            videos = "https://www.startpage.com/do/search?cat=video&query=",
            news = "https://www.startpage.com/do/search?cat=news&query="
        ),
        SearchProvider(
            key = "yandex", name = "Yandex", home = "https://yandex.com/",
            web = "https://yandex.com/search/?text=",
            images = "https://yandex.com/images/search?text=",
            videos = "https://yandex.com/video/search?text=",
            news = "https://yandex.com/news/search?text="
        ),
        SearchProvider(
            key = "qwant", name = "Qwant", home = "https://www.qwant.com/",
            web = "https://www.qwant.com/?q=",
            images = "https://www.qwant.com/?t=images&q=",
            videos = "https://www.qwant.com/?t=videos&q=",
            news = "https://www.qwant.com/?t=news&q="
        ),
        SearchProvider(
            key = "baidu", name = "Baidu", home = "https://www.baidu.com/",
            web = "https://www.baidu.com/s?wd=",
            images = "https://image.baidu.com/search/index?tn=baiduimage&word=",
            videos = "https://www.baidu.com/sf/vsearch?pd=video&wd=",
            news = "https://www.baidu.com/s?rtt=1&tn=news&word="
        ),
        SearchProvider(
            key = "naver", name = "Naver", home = "https://www.naver.com/",
            web = "https://search.naver.com/search.naver?query=",
            images = "https://search.naver.com/search.naver?where=image&query=",
            videos = "https://search.naver.com/search.naver?where=video&query=",
            news = "https://search.naver.com/search.naver?where=news&query="
        )
    )

    // Google is first and the default; it is also used if the saved choice is unknown
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
