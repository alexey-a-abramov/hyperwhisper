package com.hyperwhisper.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.SUPPORTED_LANGUAGES
import com.hyperwhisper.localization.LocalStrings

/**
 * Language selector dialog
 * Shows full list of supported languages with recently used at top
 * Displays language names and codes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorDialog(
    title: String,
    currentLanguage: String,
    recentlyUsedLanguages: List<String>,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    // Reorder languages with recently used at top (after Auto-detect and English)
    val reorderedLanguages = remember(recentlyUsedLanguages) {
        val autoDetect = SUPPORTED_LANGUAGES.firstOrNull { it.code.isEmpty() }
        val english = SUPPORTED_LANGUAGES.firstOrNull { it.code == "en" }
        val recentLanguages = recentlyUsedLanguages.mapNotNull { code ->
            SUPPORTED_LANGUAGES.firstOrNull { it.code == code }
        }
        val remainingLanguages = SUPPORTED_LANGUAGES.filter {
            it.code.isNotEmpty() && it.code != "en" && !recentlyUsedLanguages.contains(it.code)
        }

        listOfNotNull(autoDetect, english) + recentLanguages + remainingLanguages
    }

    // Full-screen overlay within keyboard
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 16.dp
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider()

                // Language list (scrollable, compact)
                // Use Voice Commands mode to change languages hands-free
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(reorderedLanguages.size) { index ->
                        val language = reorderedLanguages[index]
                        Surface(
                            onClick = { onLanguageSelected(language.code) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (language.code == currentLanguage) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = language.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (language.code == currentLanguage) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (language.code.isNotEmpty()) {
                                    Text(
                                        text = language.code,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Cancel button (compact)
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text(strings.cancel.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
