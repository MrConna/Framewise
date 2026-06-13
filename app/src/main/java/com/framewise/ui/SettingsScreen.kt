package com.framewise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.framewise.SettingsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    // Backed by the process-wide [SettingsState] so toggles survive navigation
    // between Settings and Camera (rememberSaveable was scoped to this screen).

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Composition Rules",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                title = "Rule of Thirds",
                description = "Show grid lines dividing the frame into nine equal parts",
                checked = SettingsState.ruleOfThirdsEnabled,
                onCheckedChange = { SettingsState.ruleOfThirdsEnabled = it }
            )

            SettingRow(
                title = "Horizon Level",
                description = "Show indicator to prevent image skewing",
                checked = SettingsState.horizonLevelEnabled,
                onCheckedChange = { SettingsState.horizonLevelEnabled = it }
            )

            SettingRow(
                title = "Golden Ratio",
                description = "Show golden ratio grid overlays",
                checked = SettingsState.goldenRatioEnabled,
                onCheckedChange = { SettingsState.goldenRatioEnabled = it }
            )

            SettingRow(
                title = "Diagonal Lines",
                description = "Show dynamic diagonal guidelines",
                checked = SettingsState.diagonalEnabled,
                onCheckedChange = { SettingsState.diagonalEnabled = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Text(
                text = "System Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                title = "Keep Screen On",
                description = "Prevent device screen from turning off while using camera",
                checked = SettingsState.keepScreenOn,
                onCheckedChange = { SettingsState.keepScreenOn = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Save Location",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Pictures/Framewise",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Captured photos are saved to your external storage directory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
