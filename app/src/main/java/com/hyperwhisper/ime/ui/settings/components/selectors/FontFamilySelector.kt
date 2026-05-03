package com.hyperwhisper.ui.settings.components.selectors

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hyperwhisper.data.FontFamilyOption
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.util.localizedDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontFamilySelector(
    selectedFont: FontFamilyOption,
    onFontSelected: (FontFamilyOption) -> Unit
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedFont.localizedDisplayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text(strings.selectorFontLabel) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FontFamilyOption.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.localizedDisplayName(),
                            fontFamily = option.fontFamily
                        )
                    },
                    onClick = {
                        onFontSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
