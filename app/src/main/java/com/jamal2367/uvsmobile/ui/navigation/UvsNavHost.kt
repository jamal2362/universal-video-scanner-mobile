package com.jamal2367.uvsmobile.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jamal2367.uvsmobile.ui.detail.DetailScreen
import com.jamal2367.uvsmobile.ui.files.FilesScreen
import com.jamal2367.uvsmobile.ui.library.LibraryScreen
import com.jamal2367.uvsmobile.ui.library.LibraryViewModel
import com.jamal2367.uvsmobile.ui.scan.ScanScreen
import com.jamal2367.uvsmobile.ui.settings.SettingsScreen
import com.jamal2367.uvsmobile.ui.stats.StatsScreen

@Composable
fun UvsNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // The library's state outlives its tab: a filter tapped in the statistics
    // has to land in the same view model the library screen is reading, and a
    // per-destination one would be a different object.
    val activity = LocalContext.current as ComponentActivity
    val libraryViewModel: LibraryViewModel = viewModel(viewModelStoreOwner = activity)

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier,
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onOpenEntry = { navController.navigate(Routes.detail(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                onShowInLibrary = { field, value ->
                    libraryViewModel.clearNarrowing()
                    libraryViewModel.setFilter(field, value)
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.LIBRARY) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.FILES) {
            FilesScreen(
                onOpenEntry = { navController.navigate(Routes.detail(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        composable(
            route = Routes.DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_PATH) { type = NavType.StringType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
