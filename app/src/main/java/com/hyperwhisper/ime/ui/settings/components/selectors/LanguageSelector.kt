package com.hyperwhisper.ui.settings.components.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.SUPPORTED_LANGUAGES
import com.hyperwhisper.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    label: String,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedLang = SUPPORTED_LANGUAGES.find { it.code == selectedLanguage }

    // Fuzzy search filter
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SUPPORTED_LANGUAGES
        } else {
            val query = searchQuery.lowercase()
            SUPPORTED_LANGUAGES.filter { language ->
                language.name.lowercase().contains(query) ||
                language.code.lowercase().contains(query) ||
                // Fuzzy match: check if query letters appear in order
                language.name.lowercase().let { name ->
                    var queryIndex = 0
                    name.forEach { char ->
                        if (queryIndex < query.length && char == query[queryIndex]) {
                            queryIndex++
                        }
                    }
                    queryIndex == query.length
                }
            }.sortedBy { language ->
                // Prioritize exact matches and starts-with matches
                when {
                    language.name.lowercase() == query -> 0
                    language.code.lowercase() == query -> 1
                    language.name.lowercase().startsWith(query) -> 2
                    language.code.lowercase().startsWith(query) -> 3
                    language.name.lowercase().contains(query) -> 4
                    else -> 5
                }
            }
        }
    }

    // Reset search when menu closes
    LaunchedEffect(expanded) {
        if (!expanded) {
            searchQuery = ""
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLang?.name ?: strings.autoDetect,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = { Text(supportingText, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.languageSelectorSearchPlaceholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Divider()

            // Filtered language list
            filteredLanguages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(language.name)
                            if (language.code.isNotEmpty()) {
                                Text(
                                    text = language.code,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    onClick = {
                        onLanguageSelected(language.code)
                        expanded = false
                        searchQuery = ""
                    }
                )
            }

            if (filteredLanguages.isEmpty()) {
                Text(
                    text = strings.noLanguagesFound,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
