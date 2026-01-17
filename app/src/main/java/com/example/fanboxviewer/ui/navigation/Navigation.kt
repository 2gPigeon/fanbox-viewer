package com.example.fanboxviewer.ui.navigation

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fanboxviewer.AppContainer
import com.example.fanboxviewer.session.SessionManager
import com.example.fanboxviewer.ui.screens.BookmarksScreen
import com.example.fanboxviewer.ui.screens.CreatorListScreen
import com.example.fanboxviewer.ui.screens.HiddenScreen
import com.example.fanboxviewer.ui.screens.LoginScreen
import com.example.fanboxviewer.ui.screens.PostListScreen2
import com.example.fanboxviewer.ui.screens.SettingsScreen
import com.example.fanboxviewer.ui.screens.SplashScreen

sealed class Route(val path: String) {
    data object Splash : Route("splash")
    data object Login : Route("login")
    data object Creators : Route("creators")
    data object Posts : Route("posts/{creatorId}/{creatorName}") {
        fun build(creatorId: String, creatorName: String) = "posts/$creatorId/$creatorName"
    }
    data object Bookmarks : Route("bookmarks")
    data object Hidden : Route("hidden")
    data object Settings : Route("settings")
}

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val sessionManager = SessionManager()
    val context = LocalContext.current

    NavHost(navController = nav, startDestination = Route.Splash.path) {
        composable(Route.Splash.path) {
            SplashScreen(
                isLoggedIn = sessionManager.isLoggedIn(),
                onGoLogin = { nav.navigate(Route.Login.path) { popUpTo(Route.Splash.path) { inclusive = true } } },
                onGoHome = { nav.navigate(Route.Creators.path) { popUpTo(Route.Splash.path) { inclusive = true } } }
            )
        }
        composable(Route.Login.path) {
            LoginScreen(
                onLoggedIn = {
                    nav.navigate(Route.Creators.path) { popUpTo(Route.Login.path) { inclusive = true } }
                }
            )
        }
        composable(Route.Creators.path) {
            CreatorListScreen(
                container = container,
                onOpenCreator = { id, name -> nav.navigate(Route.Posts.build(id, name)) },
                onOpenBookmarks = { nav.navigate(Route.Bookmarks.path) },
                onOpenHidden = { nav.navigate(Route.Hidden.path) },
                onOpenSettings = { nav.navigate(Route.Settings.path) }
            )
        }
        composable(
            Route.Posts.path,
            arguments = listOf(
                navArgument("creatorId") { type = NavType.StringType },
                navArgument("creatorName") { type = NavType.StringType },
            )
        ) { backStack ->
            val creatorId = backStack.arguments?.getString("creatorId") ?: return@composable
            val creatorName = backStack.arguments?.getString("creatorName") ?: ""
            PostListScreen2(
                container = container,
                creatorId = creatorId,
                creatorName = creatorName,
                onOpenPost = { url ->
                    val cti = CustomTabsIntent.Builder().build()
                    cti.launchUrl(context, android.net.Uri.parse(url))
                }
            )
        }
        composable(Route.Bookmarks.path) {
            BookmarksScreen(
                container = container,
                onOpenPost = { url ->
                    val cti = CustomTabsIntent.Builder().build()
                    cti.launchUrl(context, android.net.Uri.parse(url))
                }
            )
        }
        composable(Route.Hidden.path) {
            HiddenScreen(container = container)
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                container = container,
                onLogout = {
                    SessionManager().logout()
                    nav.navigate(Route.Login.path) { popUpTo(Route.Creators.path) { inclusive = true } }
                }
            )
        }
    }
}
