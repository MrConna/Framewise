package com.framewise.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.framewise.ui.CameraScreen
import com.framewise.ui.GalleryScreen
import com.framewise.ui.SettingsScreen
import com.framewise.ui.theme.FramewiseTheme

sealed class Screen(val route: String) {
    object Camera : Screen("camera")
    object Settings : Screen("settings")
    object Gallery : Screen("gallery")
}

/**
 * 页面过渡动画时长（毫秒）。
 * 参考 awesome-android-ui: transitions-everywhere / CircularReveal
 */
private const val TRANSITION_DURATION = 300

/** 页面淡入 + 上滑 */
private val pageEnter = slideInVertically(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetY = { it / 8 }
) + fadeIn(tween(TRANSITION_DURATION))

/** 页面淡出 + 下滑 */
private val pageExit = slideOutVertically(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetY = { it / 8 }
) + fadeOut(tween(TRANSITION_DURATION))

/** 后退时页面淡入 + 下滑（返回栈效果） */
private val popEnter = slideInVertically(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetY = { -it / 8 }
) + fadeIn(tween(TRANSITION_DURATION))

/** 后退时页面淡出 + 上滑 */
private val popExit = slideOutVertically(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetY = { -it / 8 }
) + fadeOut(tween(TRANSITION_DURATION))

@Composable
fun NavGraph(navController: NavHostController) {
    FramewiseTheme {
        NavHost(
            navController = navController,
            startDestination = Screen.Camera.route,
            enterTransition = { pageEnter },
            exitTransition = { pageExit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) {
            composable(Screen.Camera.route) {
                CameraScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToGallery = { navController.navigate(Screen.Gallery.route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Gallery.route) {
                GalleryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
