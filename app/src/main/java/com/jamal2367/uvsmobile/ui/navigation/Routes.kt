package com.jamal2367.uvsmobile.ui.navigation

import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import com.jamal2367.uvsmobile.R

object Routes {
    const val LIBRARY = "library"
    const val STATS = "stats"
    const val FILES = "files"
    const val SCAN = "scan"
    const val SETTINGS = "settings"

    const val ARG_PATH = "path"
    const val DETAIL_PATTERN = "detail/{$ARG_PATH}"

    fun detail(filePath: String): String = "detail/${encodePath(filePath)}"

    /**
     * A file path is not a route segment: it is full of slashes, and percent
     * encoding is decoded again on the way in and out at different points. Base64
     * with the URL-safe alphabet sidesteps the question entirely.
     */
    fun encodePath(filePath: String): String = Base64.encodeToString(
        filePath.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    fun decodePath(encoded: String): String = runCatching {
        String(
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8,
        )
    }.getOrDefault("")
}

/** The five places the bar switches between. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    LIBRARY(Routes.LIBRARY, R.string.nav_library, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    STATS(Routes.STATS, R.string.nav_stats, Icons.Filled.Insights, Icons.Outlined.Insights),
    FILES(Routes.FILES, R.string.nav_files, Icons.Filled.Folder, Icons.Outlined.Folder),
    SCAN(Routes.SCAN, R.string.nav_scan, Icons.Filled.Radar, Icons.Outlined.Radar),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
