package com.framewise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.framewise.R
import com.framewise.SettingsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
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
                text = "构图规则",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                title = "三分法",
                description = "显示九宫格辅助线",
                checked = SettingsState.ruleOfThirdsEnabled,
                onCheckedChange = { SettingsState.ruleOfThirdsEnabled = it }
            )

            SettingRow(
                title = "水平校准",
                description = "防止画面倾斜",
                checked = SettingsState.horizonLevelEnabled,
                onCheckedChange = { SettingsState.horizonLevelEnabled = it }
            )

            SettingRow(
                title = "黄金比例",
                description = "显示黄金螺旋辅助线",
                checked = SettingsState.goldenRatioEnabled,
                onCheckedChange = { SettingsState.goldenRatioEnabled = it }
            )

            SettingRow(
                title = "对角线",
                description = "显示对角线构图指引",
                checked = SettingsState.diagonalEnabled,
                onCheckedChange = { SettingsState.diagonalEnabled = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Text(
                text = "系统设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                title = "保持屏幕常亮",
                description = "取景时保持屏幕不灭",
                checked = SettingsState.keepScreenOn,
                onCheckedChange = { SettingsState.keepScreenOn = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "保存位置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Pictures/构图指南",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "照片保存到设备外部存储",
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
