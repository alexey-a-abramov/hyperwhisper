package com.hyperwhisper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.sections.KeyboardBottomBar
import com.hyperwhisper.ui.util.localizedDisplayName

@Composable
fun EmojiKeyboard(
    recentEmojis: List<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onReturnToDictation: () -> Unit,
    onModeChange: (com.hyperwhisper.data.KeyboardInputMode) -> Unit,
    currentMode: com.hyperwhisper.data.KeyboardInputMode,
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var selectedCategory by remember { mutableStateOf(EmojiData.EmojiCategory.SMILEYS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(KeyboardMetrics.OuterPadding)
    ) {
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClear = { onSearchQueryChange("") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(KeyboardMetrics.OuterPadding))

        // Category tabs
        CategoryTabs(
            categories = EmojiData.EmojiCategory.values().toList(),
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(KeyboardMetrics.OuterPadding))

        // Emoji grid. Single LazyVerticalGrid for both search and category
        // browsing; nesting a LazyVerticalGrid inside a LazyColumn item gives
        // it unbounded height and crashes Compose with
        // "Vertically scrollable component was measured with an infinity
        // maximum height constraints" (seen in crash-20260502-212123).
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val emojisPerRow = 15
            val visibleEmojis = if (searchQuery.isNotEmpty()) {
                EmojiData.searchEmojis(searchQuery)
            } else {
                EmojiData.emojisByCategory[selectedCategory] ?: emptyList()
            }

            if (visibleEmojis.isEmpty() && searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.emojiNoEmojisFound,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(emojisPerRow),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.KeySpacing),
                    verticalArrangement = Arrangement.spacedBy(KeyboardMetrics.KeySpacing)
                ) {
                    if (searchQuery.isEmpty() && recentEmojis.isNotEmpty()) {
                        item(
                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
                        ) {
                            Text(
                                text = strings.emojiRecentlyUsed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                            )
                        }
                        items(recentEmojis) { emoji ->
                            EmojiButton(emoji = emoji, onClick = { onEmojiSelected(emoji) })
                        }
                        item(
                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    items(visibleEmojis) { emoji ->
                        EmojiButton(emoji = emoji, onClick = { onEmojiSelected(emoji) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Universal bottom bar — paste-last / space / enter, identical to
        // every other layout. Backspace lives in the top strip.
        KeyboardBottomBar(
            lastTranscribedText = lastTranscribedText,
            transcriptionHistory = transcriptionHistory,
            enableHistoryPanel = enableHistoryPanel,
            onPasteText = onPasteText,
            onShowHistory = onShowHistory,
            onSpace = onSpace,
            onEnter = onEnter
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2A2A2A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = strings.keyboardSearchDesc,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White,
                    fontSize = 14.sp
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = strings.emojiSearchPlaceholder,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.clear,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    categories: List<EmojiData.EmojiCategory>,
    selectedCategory: EmojiData.EmojiCategory,
    onCategorySelected: (EmojiData.EmojiCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.TopStripKeyGap)
    ) {
        items(categories) { category ->
            CategoryTab(
                category = category,
                isSelected = category == selectedCategory,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun CategoryTab(
    category: EmojiData.EmojiCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(50.dp)
            .height(45.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color(0xFF2A2A2A)
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = category.icon,
                fontSize = 20.sp
            )
            Text(
                text = category.localizedDisplayName().take(3),
                fontSize = 8.sp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmojiButton(
    emoji: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp)),
        color = Color(0xFF1A1A1A)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
