package com.hyperwhisper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun EmojiKeyboard(
    recentEmojis: List<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onReturnToDictation: () -> Unit,
    onModeChange: (com.hyperwhisper.data.KeyboardInputMode) -> Unit,
    currentMode: com.hyperwhisper.data.KeyboardInputMode,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(EmojiData.EmojiCategory.SMILEYS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(4.dp)
    ) {
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClear = { onSearchQueryChange("") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Category tabs
        CategoryTabs(
            categories = EmojiData.EmojiCategory.values().toList(),
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Emoji grid
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (searchQuery.isNotEmpty()) {
                // Show search results
                val searchResults = EmojiData.searchEmojis(searchQuery)
                EmojiGrid(
                    emojis = searchResults,
                    onEmojiClick = { emoji ->
                        onEmojiSelected(emoji)
                    },
                    emojisPerRow = 15
                )
            } else {
                // Show emojis by category with recents at top
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Recent emojis section (if any)
                    if (recentEmojis.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "Recently Used",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(recentEmojis) { emoji ->
                                        EmojiButton(
                                            emoji = emoji,
                                            onClick = { onEmojiSelected(emoji) }
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Category emojis
                    item {
                        val categoryEmojis = EmojiData.emojisByCategory[selectedCategory] ?: emptyList()
                        EmojiGrid(
                            emojis = categoryEmojis,
                            onEmojiClick = { emoji ->
                                onEmojiSelected(emoji)
                            },
                            emojisPerRow = 15
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bottom control row
        Row(
            modifier = Modifier.fillMaxWidth().height(45.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Mode switcher
            UnifiedModeSwitcher(
                currentMode = currentMode,
                onModeChange = onModeChange,
                onReturnToDictation = onReturnToDictation,
                modifier = Modifier.weight(2.5f).fillMaxHeight()
            )

            // Space
            Surface(
                onClick = onSpace,
                modifier = Modifier.weight(2f).fillMaxHeight(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEB3B)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "space",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }

            // Backspace
            Surface(
                onClick = onBackspace,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFD32F2F)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = Color.White
                    )
                }
            }

            // Enter
            Surface(
                onClick = onEnter,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF00C853)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                contentDescription = "Search",
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
                            text = "Search emoji...",
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
                        contentDescription = "Clear",
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                text = category.displayName.take(3),
                fontSize = 8.sp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmojiGrid(
    emojis: List<String>,
    onEmojiClick: (String) -> Unit,
    emojisPerRow: Int
) {
    if (emojis.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No emojis found",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(emojisPerRow),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(emojis) { emoji ->
                EmojiButton(
                    emoji = emoji,
                    onClick = { onEmojiClick(emoji) }
                )
            }
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

// Note: UnifiedModeSwitcher is already defined in KeyboardScreen.kt
// We'll use it here by importing it
