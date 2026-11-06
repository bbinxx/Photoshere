package com.bbinxx.photoshere.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbinxx.photoshere.ui.about.AboutScreen
import com.bbinxx.photoshere.ui.camera.CameraScreen
import com.bbinxx.photoshere.ui.gallery.GalleryScreen
import com.bbinxx.photoshere.ui.home.HomeScreen
import com.bbinxx.photoshere.ui.processing.ProcessingScreen
import com.bbinxx.photoshere.ui.settings.SettingsScreen
import com.bbinxx.photoshere.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val CAPTURE = "capture?sessionId={sessionId}"
    const val PROCESSING = "processing/{sessionId}"
    const val VIEWER = "viewer/{recordId}"

    fun capture(sessionId: String? = null): String =
        if (sessionId.isNullOrBlank()) "capture" else "capture?sessionId=$sessionId"

    fun processing(sessionId: String): String = "processing/$sessionId"

    fun viewer(recordId: String): String = "viewer/$recordId"
}

@Composable
fun PhotoSphereNavHost() {
    val navController = rememberNavController()

    val goHome: () -> Unit = {
        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
            navController.navigate(Routes.HOME) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewCapture = { navController.navigate(Routes.capture()) },
                onResumeSession = { sessionId ->
                    navController.navigate(Routes.capture(sessionId))
                },
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSphere = { recordId ->
                    navController.navigate(Routes.viewer(recordId))
                },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(
            route = Routes.CAPTURE,
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            CameraScreen(
                sessionId = entry.arguments?.getString("sessionId"),
                onClose = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onProcessing = { sessionId ->
                    navController.navigate(Routes.processing(sessionId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }

        composable(
            route = Routes.PROCESSING,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            ProcessingScreen(
                sessionId = sessionId,
                onBackHome = goHome,
                onOpenViewer = { recordId ->
                    navController.navigate(Routes.viewer(recordId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
        ) { entry ->
            val recordId = entry.arguments?.getString("recordId").orEmpty()
            ViewerScreen(
                recordId = recordId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                onBack = { navController.popBackStack() },
                onOpenSphere = { recordId ->
                    navController.navigate(Routes.viewer(recordId))
                },
                onNewCapture = { navController.navigate(Routes.capture()) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
