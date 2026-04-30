package com.hyperwhisper.ui.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperwhisper.ui.settings.SettingsTab

@Composable
fun SettingsHamburgerMenu(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))
        Text(
            "HyperWhisper Settings",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Divider()
        Spacer(Modifier.height(8.dp))
        
        SettingsTab.values().forEach { tab ->
            NavigationDrawerItem(
                label = { Text(tab.title) },
                icon = { Icon(tab.icon, contentDescription = null) },
                selected = tab == selectedTab,
                onClick = {
                    onTabSelected(tab)
                    onClose()
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
