package dk.familieapp.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.unit.dp

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "I dag", Icons.Filled.Home),
    Tab("mealplan", "Madplan", Icons.Filled.Restaurant),
    Tab("shopping", "Indkøb", Icons.Filled.ShoppingCart),
    Tab("calendar", "Kalender", Icons.Filled.CalendarMonth),
    Tab("chores", "Pligter", Icons.Filled.TaskAlt),
)

/** Skifter fane og husker hver fanes tilstand. */
fun NavController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(vm: AppViewModel, sharedUrl: String?, onSharedUrlHandled: () -> Unit) {
    val store by vm.store.collectAsStateWithLifecycle()
    if (store == null) {
        HouseholdSetupScreen(vm)
        return
    }

    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val loadedProfiles = profiles
    if (loadedProfiles == null || settings == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (me == null) {
        ProfileGate(vm, loadedProfiles)
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            nav.navigate("import?url=" + Uri.encode(sharedUrl))
            onSharedUrlHandled()
        }
    }

    Scaffold(
        containerColor = Ground,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = "today",
            modifier = Modifier
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding(),
        ) {
            composable("today") { TodayScreen(vm, nav) }
            composable("mealplan") { MealPlanScreen(vm, nav) }
            composable("shopping") { ShoppingScreen(vm) }
            composable("calendar") { CalendarScreen(vm) }
            composable("chores") { ChoresScreen(vm, nav) }
            composable("family") { FamilyScreen(vm, nav) }
            composable("recipe/{id}") { entry ->
                RecipeDetailScreen(vm, entry.arguments?.getString("id").orEmpty(), nav)
            }
            composable(
                "edit?id={id}",
                arguments = listOf(navArgument("id") { defaultValue = "" }),
            ) { entry ->
                RecipeEditScreen(vm, entry.arguments?.getString("id").orEmpty(), nav)
            }
            composable(
                "import?url={url}",
                arguments = listOf(navArgument("url") { defaultValue = "" }),
            ) { entry ->
                ImportScreen(vm, entry.arguments?.getString("url").orEmpty(), nav)
            }
        }
    }
}

/** Profilens avatar øverst til højre. Fører til familie og indstillinger. */
@Composable
fun ProfileButton(vm: AppViewModel, nav: NavController) {
    val me by vm.me.collectAsStateWithLifecycle()
    IconButton(onClick = { nav.navigate("family") }) {
        Avatar(me, 36.dp)
    }
}
