package com.framewise.ui.navigation

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

@Composable
fun NavGraph(navController: NavHostController) {
    FramewiseTheme {
        NavHost(
            navController = navController,
            startDestination = Screen.Camera.route
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
