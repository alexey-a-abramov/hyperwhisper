package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.SUPPORTED_LANGUAGES

@Composable
fun InputLanguageButton(
    currentLanguage: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val currentLang = SUPPORTED_LANGUAGES.find { it.code == currentLanguage }
    val displayText = if (currentLanguage.isEmpty()) "Auto" else currentLang?.code?.uppercase() ?: currentLanguage.uppercase()

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "In",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Light
            )
            Text(
                text = displayText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun OutputLanguageButton(
    currentLanguage: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val currentLang = SUPPORTED_LANGUAGES.find { it.code == currentLanguage }
    val displayText = if (currentLanguage.isEmpty()) "Auto" else currentLang?.code?.uppercase() ?: currentLanguage.uppercase()

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Out",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Light
            )
            Text(
                text = displayText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
