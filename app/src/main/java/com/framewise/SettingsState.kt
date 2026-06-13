package com.framewise

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide composition settings backed by SharedPreferences.
 *
 * Survives navigation, process death, and app restart.
 * Call [init] once from MainActivity.onCreate with the application context.
 */
object SettingsState {
    private var prefs: SharedPreferences? = null
    private const val NAME = "framewise_settings"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        // Restore persisted values
        _ruleOfThirdsEnabled = prefs!!.getBoolean("rule_of_thirds", true)
        _horizonLevelEnabled = prefs!!.getBoolean("horizon_level", true)
        _goldenRatioEnabled = prefs!!.getBoolean("golden_ratio", false)
        _diagonalEnabled = prefs!!.getBoolean("diagonal", false)
        _keepScreenOn = prefs!!.getBoolean("keep_screen_on", true)
        _capturedCount = prefs!!.getInt("captured_count", 0)
        _onboardingCompleted = prefs!!.getBoolean("onboarding_completed", false)
    }

    private fun save(key: String, value: Any) {
        prefs?.edit()?.apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
            apply()
        }
    }

    private var _ruleOfThirdsEnabled by mutableStateOf(true)
    var ruleOfThirdsEnabled: Boolean
        get() = _ruleOfThirdsEnabled
        set(v) { _ruleOfThirdsEnabled = v; save("rule_of_thirds", v) }

    private var _horizonLevelEnabled by mutableStateOf(true)
    var horizonLevelEnabled: Boolean
        get() = _horizonLevelEnabled
        set(v) { _horizonLevelEnabled = v; save("horizon_level", v) }

    private var _goldenRatioEnabled by mutableStateOf(false)
    var goldenRatioEnabled: Boolean
        get() = _goldenRatioEnabled
        set(v) { _goldenRatioEnabled = v; save("golden_ratio", v) }

    private var _diagonalEnabled by mutableStateOf(false)
    var diagonalEnabled: Boolean
        get() = _diagonalEnabled
        set(v) { _diagonalEnabled = v; save("diagonal", v) }

    private var _keepScreenOn by mutableStateOf(true)
    var keepScreenOn: Boolean
        get() = _keepScreenOn
        set(v) { _keepScreenOn = v; save("keep_screen_on", v) }

    private var _capturedCount by mutableStateOf(0)
    var capturedCount: Int
        get() = _capturedCount
        set(v) { _capturedCount = v; save("captured_count", v) }

    /** 是否已完成首次使用引导。首次启动为 false，看完引导后置 true 并持久化。 */
    private var _onboardingCompleted by mutableStateOf(false)
    var onboardingCompleted: Boolean
        get() = _onboardingCompleted
        set(v) { _onboardingCompleted = v; save("onboarding_completed", v) }

    // ── 主题设置 ───────────────────────────────────────────────────────

    /** 主题模式: "system"(跟随系统) / "dark"(深色) / "light"(浅色) */
    private var _themeMode by mutableStateOf("system")
    var themeMode: String
        get() = _themeMode
        set(v) { _themeMode = v; save("theme_mode", v) }

    /** 是否为深色模式（供主题入口判断） */
    fun isDarkTheme(systemDark: Boolean): Boolean = when (_themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
}
