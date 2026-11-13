package com.hmedia.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// Search screen: the search box, the categories, and the search history kept on this phone (not saved in Private Mode).
@Composable
fun SearchHubScreen(prefs: AppPrefs, onSearch: (String, WebCategory) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.search_title), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.home_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query.trim(), WebCategory.WEB) })
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(WebCategory.entries) { category ->
                AssistChip(
                    onClick = { onSearch(query.trim(), category) },
                    label = { Text(stringResource(category.label)) }
                )
            }
        }
        if (prefs.privateMode) {
            Text(
                stringResource(R.string.private_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.search_history), style = MaterialTheme.typography.titleMedium)
            if (!prefs.privateMode && prefs.recent.isNotEmpty()) {
                TextButton(onClick = { prefs.clearRecent() }) { Text(stringResource(R.string.search_clear)) }
            }
        }
        if (!prefs.privateMode && prefs.recent.isEmpty()) {
            Text(
                stringResource(R.string.search_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (!prefs.privateMode) {
            LazyColumn(Modifier.weight(1f)) {
                items(prefs.recent) { term ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSearch(term, WebCategory.WEB) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(term, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { prefs.forgetRecent(term) }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.search_remove),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
