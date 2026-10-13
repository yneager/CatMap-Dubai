package com.catmap.dubai.ui.navigation

import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.catmap.dubai.R
import com.catmap.dubai.data.AppErrors
import com.catmap.dubai.ui.admin.AdminDashboardScreen
import com.catmap.dubai.ui.admin.AdminPostsScreen
import com.catmap.dubai.ui.admin.AdminSittersScreen
import com.catmap.dubai.ui.admin.AdminUsersScreen
import com.catmap.dubai.ui.admin.MaintenanceQueueScreen
import com.catmap.dubai.ui.admin.MergeQueueScreen
import com.catmap.dubai.ui.admin.ModLogScreen
import com.catmap.dubai.ui.admin.ReportsQueueScreen
import com.catmap.dubai.ui.admin.SubmissionsScreen
import com.catmap.dubai.ui.admin.VolunteersScreen
import com.catmap.dubai.ui.adoption.AdoptionRequestsScreen
import com.catmap.dubai.ui.adoption.AdoptionScreen
import com.catmap.dubai.ui.auth.ForgotPasswordScreen
import com.catmap.dubai.ui.auth.LoginScreen
import com.catmap.dubai.ui.auth.RegisterScreen
import com.catmap.dubai.ui.auth.VerifyEmailScreen
import com.catmap.dubai.ui.cats.CatEditScreen
import com.catmap.dubai.ui.cats.CatListScreen
import com.catmap.dubai.ui.cats.CatProfileScreen
import com.catmap.dubai.ui.cats.MergeSuggestScreen
import com.catmap.dubai.ui.cats.PhotoViewerScreen
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalSnackbar
import com.catmap.dubai.ui.components.OfflineBanner
import com.catmap.dubai.ui.explore.ExploreScreen
import com.catmap.dubai.ui.explore.SearchScreen
import com.catmap.dubai.ui.map.LocationPickerScreen
import com.catmap.dubai.ui.map.MapScreen
import com.catmap.dubai.ui.messages.ChatScreen
import com.catmap.dubai.ui.messages.ChatWithScreen
import com.catmap.dubai.ui.messages.MessagesScreen
import com.catmap.dubai.ui.posts.AreaAlertsScreen
import com.catmap.dubai.ui.posts.FavoritesScreen
import com.catmap.dubai.ui.posts.MyPostsScreen
import com.catmap.dubai.ui.posts.PostChooserSheet
import com.catmap.dubai.ui.posts.PostDetailScreen
import com.catmap.dubai.ui.posts.PostEditorScreen
import com.catmap.dubai.ui.posts.PosterScreen
import com.catmap.dubai.ui.profile.AchievementsScreen
import com.catmap.dubai.ui.profile.EditProfileScreen
import com.catmap.dubai.ui.profile.ProfileScreen
import com.catmap.dubai.ui.rescue.NewRescueScreen
import com.catmap.dubai.ui.rescue.RescueDetailScreen
import com.catmap.dubai.ui.rescue.RescueListScreen
import com.catmap.dubai.ui.settings.NotificationsScreen
import com.catmap.dubai.ui.settings.SettingsScreen
import com.catmap.dubai.ui.sighting.NewSightingScreen
import com.catmap.dubai.ui.sighting.SightingDetailScreen
import com.catmap.dubai.ui.sighting.SightingEditScreen
import com.catmap.dubai.ui.sitting.BookingRequestScreen
import com.catmap.dubai.ui.sitting.BookingsScreen
import com.catmap.dubai.ui.sitting.SitterDetailScreen
import com.catmap.dubai.ui.sitting.SitterEditScreen
import com.catmap.dubai.ui.sitting.SittersScreen
import com.catmap.dubai.ui.stations.StationDetailScreen
import com.catmap.dubai.ui.stations.StationEditScreen
import com.catmap.dubai.ui.stations.StationsScreen
import com.catmap.dubai.ui.theme.BrandGradient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private data class TabItem(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabItems = listOf(
    TabItem(Routes.MAP, R.string.tab_map, Icons.Outlined.Map, Icons.Filled.Map),
    TabItem(Routes.EXPLORE, R.string.tab_explore, Icons.Outlined.Explore, Icons.Filled.Explore),
    TabItem(Routes.MESSAGES, R.string.tab_messages, Icons.Outlined.Forum, Icons.Filled.Forum),
    TabItem(Routes.PROFILE, R.string.tab_profile, Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun AppNavHost(pendingRoute: MutableState<String?>) {
    val container = LocalContainer.current
    val snackbar = LocalSnackbar.current
    val nav = rememberNavController()
    val user by container.session.user.collectAsStateWithLifecycle()
    val online by container.connectivity.online.collectAsStateWithLifecycle()
    val listenerError by AppErrors.listenerError.collectAsStateWithLifecycle()
    var choosingPost by rememberSaveable { mutableStateOf(false) }
    val start = remember {
        val u = container.session.user.value
        when {
            u == null -> Routes.LOGIN
            !u.isEmailVerified -> Routes.VERIFY
            else -> Routes.MAP
        }
    }

    // Background errors (e.g. a listener that lost permission) show up as a snackbar.
    LaunchedEffect(Unit) { AppErrors.messages.collect { snackbar.showSnackbar(it) } }

    // A quiet location fix (only if already allowed) lets lists show distances straight away.
    LaunchedEffect(user) { if (user != null) container.location.refreshQuietly() }
    LaunchedEffect(user?.uid) {
        user?.takeIf { it.isEmailVerified }?.uid?.let { uid ->
            runCatching { container.posts.remindStalePosts(uid) }.onFailure { AppErrors.report(it) }
        }
    }

    // Signing out or deleting the account anywhere returns to the login screen.
    LaunchedEffect(user) {
        if (user == null && nav.currentDestination?.route !in Routes.authRoutes) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    // A tapped system notification opens its screen once the user is signed in.
    LaunchedEffect(pendingRoute.value, user) {
        val route = pendingRoute.value
        if (route != null && user?.isEmailVerified == true) {
            // The NavHost sits inside the Scaffold, which composes it later, during layout. Wait until it has set
            // its graph (and restored any saved back stack); navigating earlier fails and the link was lost.
            nav.currentBackStackEntryFlow.first()
            pendingRoute.value = null
            runCatching { nav.navigate(route) }.onFailure { Log.w("CatMap", "Couldn't open $route", it) }
        }
    }

    val unreadMessages by remember(user?.uid) {
        val uid = user?.uid
        if (uid == null) flowOf(0L) else container.chats.chatsFor(uid).map { list -> list.sumOf { it.unread[uid] ?: 0L } }
    }.collectAsStateWithLifecycle(0L)

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                current in Routes.tabs,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                BottomBar(nav, current, unreadMessages, onPost = { choosingPost = true })
            }
        },
    ) { padding ->
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        Column(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom)) {
            val showBanner = current !in Routes.authRoutes
            if (showBanner) OfflineBanner(online, Modifier.statusBarsPadding())
            listenerError?.let { message ->
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().then(if (online) Modifier.statusBarsPadding() else Modifier).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.TextButton(onClick = { AppErrors.retryListeners() }) { Text(stringResource(R.string.try_again)) }
                    }
                }
            }
            NavHost(
                nav, startDestination = start,
                // While the banner is up it already sits below the status bar, so screens shouldn't pad for it again.
                modifier = Modifier.weight(1f).then(if ((showBanner && !online) || listenerError != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
                enterTransition = { forwardIn(rtl) },
                exitTransition = { forwardOut(rtl) },
                popEnterTransition = { backIn(rtl) },
                popExitTransition = { backOut(rtl) },
            ) {
                composable(Routes.LOGIN) { LoginScreen(nav) }
                composable(Routes.REGISTER) { RegisterScreen(nav) }
                composable(Routes.FORGOT) { ForgotPasswordScreen(nav) }
                composable(Routes.VERIFY) { VerifyEmailScreen(nav) }

                tab(Routes.MAP) { MapScreen(nav) }
                tab(Routes.EXPLORE) { ExploreScreen(nav) }
                tab(Routes.MESSAGES) { MessagesScreen(nav) }
                tab(Routes.PROFILE) { ProfileScreen(nav) }

                composable(Routes.SEARCH) { SearchScreen(nav) }
                composable(Routes.CATS) { CatListScreen(nav) }
                composable(Routes.RESCUE) { RescueListScreen(nav) }
                composable(Routes.STATIONS) { StationsScreen(nav) }
                composable(Routes.HELP_DIRECTORY) { com.catmap.dubai.ui.help.HelpDirectoryScreen(nav) }

                composable(Routes.CAT) { CatProfileScreen(nav, it.arg("id")) }
                composable(Routes.CAT_EDIT) { CatEditScreen(nav, it.arg("id")) }
                composable(Routes.CAT_MERGE) { MergeSuggestScreen(nav, it.arg("id")) }
                composable(Routes.PHOTO, arguments = listOf(optionalString("catId"))) {
                    PhotoViewerScreen(nav, it.arg("id"), it.arguments?.getString("catId"))
                }

                composable(Routes.NEW_SIGHTING, arguments = listOf(optionalString("catId"))) {
                    NewSightingScreen(nav, it.arguments?.getString("catId"))
                }
                composable(Routes.SIGHTING) { SightingDetailScreen(nav, it.arg("id")) }
                composable(Routes.SIGHTING_EDIT) { SightingEditScreen(nav, it.arg("id")) }

                composable(Routes.NEW_RESCUE, arguments = listOf(optionalString("catId"))) {
                    NewRescueScreen(nav, it.arguments?.getString("catId"))
                }
                composable(Routes.RESCUE_DETAIL) { RescueDetailScreen(nav, it.arg("id")) }

                composable(Routes.STATION) { StationDetailScreen(nav, it.arg("id")) }
                composable(Routes.STATION_EDIT, arguments = listOf(optionalString("id"))) {
                    StationEditScreen(nav, it.arguments?.getString("id"))
                }

                composable(Routes.POST) { PostDetailScreen(nav, it.arg("id")) }
                composable(
                    Routes.NEW_POST,
                    arguments = listOf(optionalString("category"), optionalString("type")),
                    enterTransition = { slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(300)) },
                    popExitTransition = { slideOutVertically(tween(300)) { it / 3 } + fadeOut(tween(250)) },
                ) {
                    PostEditorScreen(nav, null, it.arguments?.getString("category"), it.arguments?.getString("type"))
                }
                composable(Routes.EDIT_POST) { PostEditorScreen(nav, it.arg("id"), null, null) }
                composable(Routes.POSTER) { PosterScreen(nav, it.arg("id")) }
                composable(Routes.MY_POSTS) { MyPostsScreen(nav) }
                composable(Routes.FAVORITES) { FavoritesScreen(nav) }
                composable(Routes.AREA_ALERTS) { AreaAlertsScreen(nav) }

                composable(Routes.ADOPTION) { AdoptionScreen(nav) }
                composable(Routes.ADOPTION_REQUESTS) { AdoptionRequestsScreen(nav) }

                composable(Routes.SITTERS) { SittersScreen(nav) }
                composable(Routes.SITTER) { SitterDetailScreen(nav, it.arg("uid")) }
                composable(Routes.SITTER_EDIT) { SitterEditScreen(nav) }
                composable(Routes.BOOK) { BookingRequestScreen(nav, it.arg("uid")) }
                composable(Routes.BOOKINGS, arguments = listOf(optionalString("tab"))) { BookingsScreen(nav, it.arguments?.getString("tab") == "sitter") }

                composable(Routes.CHAT, arguments = listOf(optionalString("postId"))) { ChatScreen(nav, it.arg("id"), it.arguments?.getString("postId")) }
                composable(Routes.CHAT_WITH, arguments = listOf(optionalString("postId"))) {
                    ChatWithScreen(nav, it.arg("uid"), it.arguments?.getString("postId"))
                }

                composable(Routes.PROFILE_EDIT) { EditProfileScreen(nav) }
                composable(Routes.USER) { ProfileScreen(nav, it.arg("uid")) }
                composable(Routes.ACHIEVEMENTS) { AchievementsScreen(nav) }
                composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }

                composable(Routes.ADMIN) { AdminDashboardScreen(nav) }
                composable(Routes.ADMIN_REPORTS) { ReportsQueueScreen(nav) }
                composable(Routes.ADMIN_RESCUES) { RescueListScreen(nav, moderationView = true) }
                composable(Routes.ADMIN_MERGES) { MergeQueueScreen(nav) }
                composable(Routes.ADMIN_VOLUNTEERS) { VolunteersScreen(nav) }
                composable(Routes.ADMIN_MAINTENANCE) { MaintenanceQueueScreen(nav) }
                composable(Routes.ADMIN_SUBMISSIONS) { SubmissionsScreen(nav) }
                composable(Routes.ADMIN_LOG) { ModLogScreen(nav) }
                composable(Routes.ADMIN_POSTS) { AdminPostsScreen(nav) }
                composable(Routes.ADMIN_USERS) { AdminUsersScreen(nav) }
                composable(Routes.ADMIN_SITTERS) { AdminSittersScreen(nav) }

                composable(
                    Routes.PICK_LOCATION,
                    arguments = listOf(optionalString("lat"), optionalString("lng"), optionalString("radius"), optionalString("area")),
                ) {
                    LocationPickerScreen(
                        nav,
                        it.arguments?.getString("lat")?.toDoubleOrNull(),
                        it.arguments?.getString("lng")?.toDoubleOrNull(),
                        it.arguments?.getString("radius")?.toDoubleOrNull(),
                        it.arguments?.getString("area") == "true",
                    )
                }
            }
        }
    }

    if (choosingPost) PostChooserSheet(nav, onDismiss = { choosingPost = false })
}

private fun NavBackStackEntry.arg(name: String) = arguments?.getString(name).orEmpty()

/** Tabs cross-fade instead of sliding, so switching between them feels instant. */
private fun NavGraphBuilder.tab(route: String, content: @Composable () -> Unit) = composable(
    route,
    enterTransition = { if (initialState.destination.route in Routes.tabs) fadeIn(tween(220)) else backIn(false) },
    exitTransition = { if (targetState.destination.route in Routes.tabs) fadeOut(tween(160)) else fadeOut(tween(200)) },
    popEnterTransition = { fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f) },
) { content() }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardIn(rtl: Boolean): EnterTransition =
    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (rtl) -it / 4 else it / 4 } + fadeIn(tween(260))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardOut(rtl: Boolean): ExitTransition =
    slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (rtl) it / 8 else -it / 8 } + fadeOut(tween(200))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backIn(rtl: Boolean): EnterTransition =
    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (rtl) it / 8 else -it / 8 } + fadeIn(tween(260))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backOut(rtl: Boolean): ExitTransition =
    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (rtl) -it / 4 else it / 4 } + fadeOut(tween(220)) + scaleOut(tween(300), targetScale = 0.98f)

private fun optionalString(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

/** Map · Explore · (+ Post) · Messages · Profile, with a centered gradient Post button. */
@Composable
private fun BottomBar(nav: NavHostController, current: String?, unreadMessages: Long, onPost: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabItems.take(2).forEach { TabButton(nav, it, current == it.route, 0, Modifier.weight(1f)) }
            PostButton(onPost, Modifier.weight(1f))
            tabItems.drop(2).forEach { TabButton(nav, it, current == it.route, if (it.route == Routes.MESSAGES) unreadMessages else 0, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabButton(nav: NavHostController, item: TabItem, selected: Boolean, badge: Long, modifier: Modifier) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tab")
    val indicator by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "indicator")
    val label = stringResource(item.label)
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple()) {
                nav.navigate(item.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = indicator))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            BadgedBox(badge = { if (badge > 0) Badge { Text(if (badge > 99) "99+" else badge.toString()) } }) {
                Icon(if (selected) item.selectedIcon else item.icon, null, Modifier.scale(scale))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostButton(onClick: () -> Unit, modifier: Modifier) {
    val description = stringResource(R.string.post_new)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(52.dp)
                .shadow(2.dp, CircleShape, spotColor = Color(0xFFE5486B))
                .clip(CircleShape)
                .background(BrandGradient)
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = description
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(32.dp), tint = Color.White)
        }
    }
}
