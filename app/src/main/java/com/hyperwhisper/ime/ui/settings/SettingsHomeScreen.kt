package com.hyperwhisper.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.ApiSettings

/**
 * Settings home: a clean, scannable list of the areas you can configure,
 * grouped by task. Status ("Active configuration") lives in About and
 * maintenance/diagnostics ("Re-test providers", "Latency Stats") live in
 * Advanced, so this landing page stays pure navigation.
 */
@Composable
fun SettingsHomeScreen(
    apiSettings: ApiSettings,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CATEGORY_GROUPS.forEach { group ->
            item(key = "header-${group.title}") { SectionHeader(group.title) }
            items(group.categories, key = { it.name }) { category ->
                CategoryRow(
                    category = category,
                    trailing = trailingTextFor(category, apiSettings),
                    onClick = { onCategorySelected(category) }
                )
            }
            item(key = "gap-${group.title}") { Spacer(Modifier.size(4.dp)) }
        }
    }
}

/**
 * Task-oriented grouping of the settings categories. Order within each group
 * matches the conceptual pipeline (speech → text → output) then interface, then
 * system-level tooling.
 */
private data class CategoryGroup(val title: String, val categories: List<SettingsCategory>)

private val CATEGORY_GROUPS = listOf(
    CategoryGroup(
        "Speech → text",
        listOf(
            SettingsCategory.TRANSCRIPTION,
            SettingsCategory.POST_PROCESSING,
            SettingsCategory.VOICE_MODES,
            SettingsCategory.LOCAL_MODELS,
        )
    ),
    CategoryGroup(
        "Interface",
        listOf(
            SettingsCategory.KEYBOARD_BEHAVIOR,
            SettingsCategory.APPEARANCE,
        )
    ),
    CategoryGroup(
        "System",
        listOf(
            SettingsCategory.ADVANCED,
            SettingsCategory.ABOUT,
        )
    ),
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    trailing: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            CategoryIcon(category.icon)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.localizedTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    category.localizedSubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!trailing.isNullOrBlank()) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun trailingTextFor(category: SettingsCategory, apiSettings: ApiSettings): String? =
    SettingsStatusLabels.categoryTrailing(category, apiSettings)
