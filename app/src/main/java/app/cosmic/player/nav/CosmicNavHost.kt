package app.cosmic.player.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cosmic.feature.download.DownloadScreen
import app.cosmic.feature.download.YoutubeSearchScreen
import app.cosmic.feature.library.LibraryScreen
import app.cosmic.feature.nowplaying.MiniPlayer
import app.cosmic.feature.nowplaying.NowPlayingScreen
import app.cosmic.feature.playlists.PlaylistDetailScreen
import app.cosmic.feature.playlists.PlaylistsScreen
import app.cosmic.feature.search.SearchScreen
import app.cosmic.feature.settings.SettingsScreen

private sealed class TopDest(val route: String, val label: String, val icon: ImageVector) {
    data object Library : TopDest("library", "Library", Icons.Filled.LibraryMusic)
    data object Playlists : TopDest("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic)
    data object Downloads : TopDest("downloads", "Downloads", Icons.Filled.Download)
    data object Settings : TopDest("settings", "Settings", Icons.Filled.Settings)
}

private val topDests = listOf(TopDest.Library, TopDest.Playlists, TopDest.Downloads, TopDest.Settings)
private const val NowPlayingRoute = "nowplaying"
private const val SearchRoute = "search"
private const val PlaylistDetailRoute = "playlist"
private const val YoutubeSearchRoute = "youtube_search"

@Composable
fun CosmicNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isNowPlaying = currentRoute == NowPlayingRoute
    // Only Now Playing is fully full-screen. Sub-routes (search, YT search,
    // playlist detail) hide the bottom NavigationBar — they have their own
    // top-bar back nav — but still show the mini-player above so the user
    // doesn't lose track of what's playing.
    val showBottomNav = !isNowPlaying &&
        currentRoute != SearchRoute &&
        currentRoute != YoutubeSearchRoute &&
        currentRoute?.startsWith("$PlaylistDetailRoute/") != true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isNowPlaying) {
                BottomBarWithMiniPlayer(
                    currentRoute = currentRoute,
                    showNavigationBar = showBottomNav,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onMiniPlayerClick = { navController.navigate(NowPlayingRoute) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopDest.Library.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(TopDest.Library.route) {
                LibraryScreen(onSearchClick = { navController.navigate(SearchRoute) })
            }
            composable(SearchRoute) { SearchScreen() }
            composable(TopDest.Playlists.route) {
                PlaylistsScreen(onOpenPlaylist = { id ->
                    navController.navigate("$PlaylistDetailRoute/$id")
                })
            }
            composable(
                route = "$PlaylistDetailRoute/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) {
                PlaylistDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(TopDest.Downloads.route) {
                DownloadScreen(onSearchYoutube = { navController.navigate(YoutubeSearchRoute) })
            }
            composable(YoutubeSearchRoute) {
                YoutubeSearchScreen(onBack = { navController.popBackStack() })
            }
            composable(TopDest.Settings.route) { SettingsScreen() }
            composable(
                route = NowPlayingRoute,
                enterTransition = {
                    androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 320),
                    ) + androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    )
                },
                exitTransition = {
                    androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 280),
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    )
                },
                popEnterTransition = {
                    androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 320),
                    ) + androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    )
                },
                popExitTransition = {
                    androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 280),
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    )
                },
            ) {
                NowPlayingScreen(onCollapse = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBarWithMiniPlayer(
    currentRoute: String?,
    showNavigationBar: Boolean,
    onSelect: (String) -> Unit,
    onMiniPlayerClick: () -> Unit,
) {
    // The bottom bar floats over a transparent surface so the screen
    // gradient bleeds through to the system nav-bar area. The mini-player
    // sits on a solid container; the nav pill below it is its own
    // floating surface separated by a gap.
    Column {
        MiniPlayer(onClick = onMiniPlayerClick)
        if (showNavigationBar) {
            CosmicNavPill(
                currentRoute = currentRoute,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun CosmicNavPill(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    // 64 dp tall pill, full bleed horizontally with 16 dp side margin and
    // a 12 dp bottom margin above the system nav-bar inset. The selected
    // item gets a colored "blob" indicator behind it that animates between
    // positions; unselected items show monochrome icon + tiny label.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(28.dp), clip = false),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                topDests.forEach { dest ->
                    NavPillItem(
                        dest = dest,
                        selected = currentRoute == dest.route,
                        onClick = { onSelect(dest.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPillItem(
    dest: TopDest,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pillWidth by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "pillWidth",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 240),
        label = "iconTint",
    )

    // Icon-only pill: the colored blob is now a circle that lights up behind
    // the selected icon. Labels live in the top bar of each screen.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (pillWidth > 0f) {
            Box(
                modifier = Modifier
                    .size((40 * pillWidth).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Icon(
            dest.icon,
            contentDescription = dest.label,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}
