package com.hyperwhisper.ui.settings.components.selectors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ColorSchemeOption

@Composable
fun ColorSchemeSelector(
    selectedScheme: ColorSchemeOption,
    onSchemeSelected: (ColorSchemeOption) -> Unit
) {
    // Scrollable vertical list of theme cards
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ColorSchemeOption.values().size) { index ->
            val option = ColorSchemeOption.values()[index]
            val isSelected = option == selectedScheme

            // Theme card with mini preview
            Surface(
                onClick = { onSchemeSelected(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                },
                border = if (isSelected) {
                    BorderStroke(2.dp, option.primaryColor)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini theme preview card
                    Surface(
                        modifier = Modifier.size(width = 80.dp, height = 56.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = option.primaryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, option.primaryColor.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mini header bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(
                                        color = option.primaryColor,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            // Mini content area with accent colors
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini button
                                Box(
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 12.dp)
                                        .background(
                                            color = option.secondaryColor,
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                )
                                // Mini FAB
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            color = option.tertiaryColor,
                                            shape = CircleShape
                                        )
                                )
                            }
                            // Mini text lines
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(4.dp)
                                        .background(
                                            color = option.primaryColor.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(1.dp)
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(4.dp)
                                        .background(
                                            color = option.secondaryColor.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }
                    }

                    // Theme name and color dots
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = option.displayName,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                option.primaryColor
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        // Color dots row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(option.primaryColor, CircleShape)
                                    .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                            )
                            // Secondary
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(option.secondaryColor, CircleShape)
                                    .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                            )
                            // Tertiary
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(option.tertiaryColor, CircleShape)
                                    .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                            )
                        }
                    }

                    // Selection indicator
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = option.primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Not selected",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
