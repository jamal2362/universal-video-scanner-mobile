package com.jamal2367.uvsmobile.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import com.jamal2367.uvsmobile.di.AppContainer
import com.jamal2367.uvsmobile.ui.navigation.TopLevelDestination
import com.jamal2367.uvsmobile.ui.navigation.UvsNavHost

/**
 * Which instance a poster should be fetched from.
 *
 * Handed down rather than passed through every state: a poster URL is only
 * half an address - the other half is whichever of the two servers the app is
 * currently talking to, and that can change between two frames.
 */
val LocalPosterServer = compositionLocalOf<ServerConfig?> { null }

@Composable
fun UvsApp(container: AppContainer, settings: AppSettings) {
    val navController = rememberNavController()
    val activeServer by container.router.activeServer.collectAsState()

    // Before the first call comes back nothing has answered yet, so the first
    // address that could is the best guess for a poster.
    val posterServer = activeServer?.config ?: settings.servers().firstOrNull()

    // Wide enough for a rail: a tablet or an unfolded phone should not waste a
    // whole edge on a bar the height of a thumb.
    //
    // Measured on the shorter edge, so it is the device that decides and not
    // the way it is being held: a phone turned sideways is over 720dp wide and
    // would have swung its bar to the side, which is the one place a thumb
    // holding the phone cannot comfortably get to.
    val useRail = LocalConfiguration.current.smallestScreenWidthDp >= 600

    CompositionLocalProvider(LocalPosterServer provides posterServer) {
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                UvsNavigationRail(navController)
                UvsNavHost(
                    navController = navController,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                // This shell holds no top bar, so it must not claim the status
                // bar either: each screen has a top bar of its own that pads
                // for it and draws underneath it. Left at the default, the
                // inset would be counted twice and every screen would start a
                // status bar's height too low.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { UvsNavigationBar(navController) },
            ) { padding ->
                UvsNavHost(
                    navController = navController,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                )
            }
        }
    }
}

@Composable
private fun UvsNavigationBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destinations = remember { TopLevelDestination.entries }

    // Material's short bar rather than the tall one: 64dp with the label under
    // the icon, instead of 80dp with a strip of nothing above it. The system's
    // own gesture bar is added underneath rather than eaten into.
    ShortNavigationBar {
        destinations.forEach { destination ->
            val selected = backStackEntry?.destination?.hierarchy
                ?.any { it.route == destination.route } == true
            ShortNavigationBarItem(
                selected = selected,
                onClick = { navController.switchTo(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.tabLabelRes),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun UvsNavigationRail(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destinations = remember { TopLevelDestination.entries }

    WideNavigationRail {
        destinations.forEach { destination ->
            val selected = backStackEntry?.destination?.hierarchy
                ?.any { it.route == destination.route } == true
            WideNavigationRailItem(
                selected = selected,
                onClick = { navController.switchTo(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.tabLabelRes),
                        maxLines = 1,
                    )
                },
                railExpanded = false,
            )
        }
    }
}

/**
 * Switch tabs the way a bar is expected to behave: one entry per tab on the
 * back stack, each remembering where it was left.
 */
private fun NavHostController.switchTo(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
