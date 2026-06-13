package com.framewise.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.framewise.R
import com.framewise.SettingsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framewise.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 构图规则 ────────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.GridOn, title = "构图规则")
            Spacer(Modifier.height(4.dp))

            AnimatedSettingRow(
                icon = Icons.Outlined.ViewColumn,
                title = "三分法",
                desc = "将主体放在画面的三分之一分割线上",
                checked = SettingsState.ruleOfThirdsEnabled,
                onCheck = { SettingsState.ruleOfThirdsEnabled = it }
            )
            AnimatedSettingRow(
                icon = Icons.Outlined.HorizontalRule,
                title = "水平校准",
                desc = "检测并校正歪斜的地平线",
                checked = SettingsState.horizonLevelEnabled,
                onCheck = { SettingsState.horizonLevelEnabled = it }
            )
            AnimatedSettingRow(
                icon = Icons.Outlined.AutoFixHigh,
                title = "黄金比例",
                desc = "使用黄金螺旋线优化构图",
                checked = SettingsState.goldenRatioEnabled,
                onCheck = { SettingsState.goldenRatioEnabled = it }
            )
            AnimatedSettingRow(
                icon = Icons.Outlined.ShowChart,
                title = "对角线",
                desc = "利用对角线元素增强画面动感",
                checked = SettingsState.diagonalEnabled,
                onCheck = { SettingsState.diagonalEnabled = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            // ── 相机设置 ────────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.CameraAlt, title = "相机设置")
            Spacer(Modifier.height(4.dp))

            AnimatedSettingRow(
                icon = Icons.Outlined.BrightnessHigh,
                title = "保持屏幕常亮",
                desc = "取景时保持屏幕不灭",
                checked = SettingsState.keepScreenOn,
                onCheck = { SettingsState.keepScreenOn = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            // ── 主题设置 ────────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Palette, title = "主题设置")
            Spacer(Modifier.height(4.dp))

            val currentTheme = SettingsState.themeMode
            val themeLabel = when (currentTheme) {
                "dark" -> "深色模式"
                "light" -> "浅色模式"
                else -> { val sysDark = isSystemInDarkTheme(); if (sysDark) "跟随系统（深色）" else "跟随系统（浅色）" }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("主题", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(themeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    // 三态切换按钮
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to "自动", "dark" to "深色", "light" to "浅色").forEach { (mode, label) ->
                            FilterChip(
                                selected = currentTheme == mode,
                                onClick = { SettingsState.themeMode = mode },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                                    selectedLabelColor = AccentBlue,
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            // ── 存储信息 ────────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Folder, title = "存储信息")
            Spacer(Modifier.height(4.dp))

            InfoCard(
                icon = Icons.Outlined.PhotoLibrary,
                title = "保存位置",
                value = "Pictures/Framewise"
            )
            InfoCard(
                icon = Icons.Outlined.Photo,
                title = "已拍摄照片",
                value = "${SettingsState.capturedCount} 张"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            // ── 关于 ────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Info, title = "关于")
            Spacer(Modifier.height(4.dp))

            InfoCard(icon = Icons.Outlined.Info, title = "应用名称", value = "构图指南")
            InfoCard(icon = Icons.Outlined.Tag, title = "版本号", value = "0.1.9")
            InfoCard(icon = Icons.Outlined.Build, title = "技术栈", value = "CameraX + ML Kit + Compose")

            Spacer(Modifier.height(24.dp))

            // 统计摘要
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📸 ${SettingsState.capturedCount}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = AccentBlue
                    )
                    Text(
                        text = "共拍摄照片数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = AccentBlue, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AnimatedSettingRow(icon: ImageVector, title: String, desc: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) AccentBlue.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "trackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "thumbColor"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (checked) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheck,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = thumbColor,
                    checkedTrackColor = trackColor,
                    uncheckedThumbColor = thumbColor,
                    uncheckedTrackColor = trackColor,
                )
            )
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        }
    }
}
