package com.hyperwhisper.ui.settings.components.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.UIScaleOption
import com.hyperwhisper.ui.util.localizedDisplayName

@Composable
fun UIScaleSelector(
    selectedScale: UIScaleOption,
    onScaleSelected: (UIScaleOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UIScaleOption.values().forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selectedScale,
                        onClick = { onScaleSelected(option) }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = option.localizedDisplayName(),
                    fontSize = (16.sp.value * option.scale).sp,
                    fontWeight = if (option == selectedScale) FontWeight.Bold else FontWeight.Normal
                )
                RadioButton(
                    selected = option == selectedScale,
                    onClick = { onScaleSelected(option) }
                )
            }
        }
    }
}
