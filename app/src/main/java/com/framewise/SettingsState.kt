package com.framewise

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Process-wide composition settings.
 *
 * Unlike [androidx.compose.runtime.saveable.rememberSaveable], which is scoped to
 * a single screen's composition, this object survives navigation between
 * Settings and Camera so toggles persist for the whole app session.
 */
object SettingsState {
    var ruleOfThirdsEnabled by mutableStateOf(true)
    var horizonLevelEnabled by mutableStateOf(true)
    var goldenRatioEnabled by mutableStateOf(false)
    var diagonalEnabled by mutableStateOf(false)
    var keepScreenOn by mutableStateOf(true)
}
