package io.github.droidkaigi.feeder

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.DrawerDefaults
import androidx.compose.material.DrawerValue
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalDrawer
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.navigate
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import io.github.droidkaigi.feeder.feed.FeedScreen
import io.github.droidkaigi.feeder.feed.FeedTabs
import io.github.droidkaigi.feeder.main.R
import io.github.droidkaigi.feeder.other.OtherScreen
import io.github.droidkaigi.feeder.other.OtherTabs
import kotlinx.coroutines.launch

@Composable
fun AppContent(
    modifier: Modifier = Modifier,
    firstDrawerValue: DrawerValue = DrawerValue.Closed,
) {
    val drawerState = rememberDrawerState(firstDrawerValue)
    val drawerContentState = rememberDrawerContentState(DrawerContents.HOME.route)
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val onNavigationIconClick: () -> Unit = {
        coroutineScope.launch {
            drawerState.open()
        }
    }
    val deepLinkUri =
        "https://" + LocalContext.current.getString(R.string.deep_link_host) +
            LocalContext.current.getString(R.string.deep_link_path)
    val actions = remember(navController) {
        AppActions(navController)
    }
    ModalDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerShape = MaterialTheme.shapes.large.copy(all = CornerSize(0.dp)),
        drawerContent = {
            DrawerContent(drawerContentState.currentValue) { contents ->
                if (drawerContentState.selectDrawerContent(contents.route)) {
                    actions.onSelectDrawerItem(contents)
                }
                coroutineScope.launch {
                    drawerState.close()
                }
            }
        },
        scrimColor = Color.Black.copy(alpha = DrawerDefaults.ScrimOpacity),
    ) {
        NavHost(navController, startDestination = "feed/{feedTab}") {
            composable(
                route = "feed/{feedTab}",
                deepLinks = listOf(navDeepLink { uriPattern = "$deepLinkUri/feed/{feedTab}" }),
                arguments = listOf(
                    navArgument("feedTab") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val routePath = rememberRoutePath(
                    backStackEntry.arguments?.getString("feedTab")
                        ?: FeedTabs.Home.routePath
                )
                val selectedTab = FeedTabs.ofRoutePath(routePath.value)
                drawerContentState.onSelectDrawerContent(selectedTab)
                val context = LocalContext.current
                FeedScreen(
                    onNavigationIconClick = onNavigationIconClick,
                    selectedTab = selectedTab,
                    onSelectedTab = { feedTabs ->
                        // We don't use navigation component transitions here for animation.
                        routePath.value = feedTabs.routePath
                        drawerContentState.onSelectDrawerContent(feedTabs)
                    },
                    onDetailClick = { feedItem: FeedItem ->
                        actions.onSelectFeed(context, feedItem)
                    }
                )
            }
            composable(
                route = "other/{otherTab}",
                deepLinks = listOf(navDeepLink { uriPattern = "$deepLinkUri/other/{otherTab}" }),
                arguments = listOf(
                    navArgument("otherTab") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val routePath = rememberRoutePath(
                    backStackEntry.arguments?.getString("otherTab")
                        ?: OtherTabs.AboutThisApp.routePath
                )
                val selectedTab = OtherTabs.ofRoutePath(routePath.value)
                drawerContentState.onSelectDrawerContent(selectedTab)
                OtherScreen(
                    selectedTab = selectedTab,
                    onSelectTab = { otherTabs ->
                        // We don't use navigation component transitions here for animation.
                        routePath.value = otherTabs.routePath
                        drawerContentState.onSelectDrawerContent(otherTabs)
                    },
                    onNavigationIconClick = onNavigationIconClick
                )
            }
        }
    }
}

private class AppActions(navController: NavHostController) {
    val onSelectDrawerItem: (DrawerContents) -> Unit = { contents ->
        navController.navigate(contents.route) {
            // Pop up to the start destination of the graph to
            // avoid building up a large stack of destinations
            // on the back stack as users select items.
            // And clean up all of the stacks if users select one of feed tabs.
            // Refer to https://developer.android.com/jetpack/compose/navigation#bottom-nav
            popUpTo(navController.graph.startDestination) {
                inclusive = when (contents) {
                    DrawerContents.HOME,
                    DrawerContents.BLOG,
                    DrawerContents.VIDEO,
                    DrawerContents.PODCAST,
                    -> true
                    else -> false
                }
            }
        }
    }

    val onSelectFeed: (Context, FeedItem) -> Unit = { context, feedItem ->
        val builder = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)

        val intent = builder.build()
        intent.launchUrl(context, Uri.parse(feedItem.link))
    }
}

@Composable
fun rememberRoutePath(
    initialRoutePath: String,
) = rememberSaveable {
    mutableStateOf(initialRoutePath)
}
