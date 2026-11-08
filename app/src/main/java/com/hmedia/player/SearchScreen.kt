package com.hmedia.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmedia.player.search.SearchApi
import com.hmedia.player.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Shows results as a native list. Pages open in the app's own viewer, not in another browser.
@Composable
fun SearchResultsScreen(query: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var results by remember(query) { mutableStateOf<List<SearchResult>>(emptyList()) }
    var error by remember(query) { mutableStateOf("") }
    var loading by remember(query) { mutableStateOf(true) }

    LaunchedEffect(query) {
        loading = true
        // The address is checked by SearchApi, which also reports a placeholder address as "not set up yet"
        val outcome = withContext(Dispatchers.IO) { SearchApi.search(BuildConfig.SEARCH_API_BASE, query) }
        outcome
            .onSuccess { results = it; error = "" }
            .onFailure { results = emptyList(); error = it.message ?: "Search failed. Try again." }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(query, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (!loading && error.isEmpty() && results.isEmpty()) {
            Text(stringResource(R.string.no_results, query), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(results) { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(r.url) }
                        .padding(vertical = 10.dp)
                ) {
                    Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        SearchApi.domainOf(r.url),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (r.snippet.isNotEmpty()) {
                        Text(r.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
            }
        }
    }
}
