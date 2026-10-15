package com.guanyji.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guanyji.app.feature.shell.PageShell
import com.guanyji.app.feature.tank.TankDetailScreen
import com.guanyji.app.feature.tank.TankFormScreen
import com.guanyji.app.feature.tank.TankListScreen
import com.guanyji.app.feature.tank.TankQuickFormScreen
import com.guanyji.app.feature.fish.FishListScreen
import com.guanyji.app.feature.fish.FishFormScreen
import com.guanyji.app.feature.fish.FishDetailScreen
import com.guanyji.app.feature.fish.FishRecordsScreen
import com.guanyji.app.feature.feeding.FeedingScreen
import com.guanyji.app.feature.care.CareScreen
import com.guanyji.app.feature.overview.OverviewScreen
import com.guanyji.app.feature.profile.ProfileScreen
import com.guanyji.app.feature.profile.SettingsScreen
import com.guanyji.app.feature.profile.AboutHelpScreen
import com.guanyji.app.feature.statistics.StatisticsScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@Composable
fun FishApp(openCareTaskId: String? = null, onCareTaskOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    var overviewMessage by remember { mutableStateOf<String?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(openCareTaskId) {
        if (openCareTaskId != null) navController.navigate(AppDestination.Care.route) { launchSingleTop = true }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("destination-${destination.route}"),
                        selected = currentRoute == destination.route || (destination == AppDestination.Tanks && currentRoute?.startsWith("tank/") == true) || (destination == AppDestination.Profile && currentRoute in setOf("statistics", "settings", "about")),
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Overview.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            AppDestination.entries.forEach { destination ->
                composable(destination.route) {
                    if (destination == AppDestination.Fish) {
                        FishListScreen(
                            onCreate = { navController.navigate("fish/create") },
                            onOpen = { navController.navigate("fish/detail/$it") },
                            onEdit = { navController.navigate("fish/edit/$it") },
                        )
                    } else if (destination == AppDestination.Tanks) {
                        TankListScreen(
                            onCreate = { navController.navigate("tank/create") },
                            onOpen = { navController.navigate("tank/detail/$it") },
                        )
                    } else if (destination == AppDestination.Care) CareScreen(openCareTaskId, onCareTaskOpened)
                    else if (destination == AppDestination.Profile) ProfileScreen(
                        onStatistics = { navController.navigate("statistics") },
                        onSettings = { navController.navigate("settings") },
                        onAbout = { navController.navigate("about") },
                    )
                    else if (destination == AppDestination.Overview) OverviewScreen(
                        onAddTank = { navController.navigate("tank/create") },
                        onAddFish = { navController.navigate("fish/create") },
                        onFeeding = { navController.navigate("feeding?tankId=null&fishId=null") },
                        onWater = { tankId -> if (tankId == null) navController.navigate(AppDestination.Tanks.route) else navController.navigate("tank/action/$tankId/water") },
                        onTank = { navController.navigate("tank/detail/$it") },
                        onFish = { navController.navigate("fish/detail/$it") },
                        onCare = { taskId -> navController.navigate(AppDestination.Care.route) },
                        onMessage = { overviewMessage = it },
                    ) else PageShell(destination)
                }
            }
            composable("tank/create") {
                TankFormScreen(null, onBack = { navController.popBackStack() }, onSaved = { navController.navigate("tank/detail/$it") { popUpTo("tank/create") { inclusive = true } } })
            }
            composable("fish/create") {
                FishFormScreen(null, onBack = { navController.popBackStack() }, onSaved = { id -> navController.navigate("fish/detail/$id") { popUpTo("fish/create") { inclusive = true } } })
            }
            composable("fish/edit/{fishId}") { entry ->
                val fishId = entry.arguments?.getString("fishId") ?: return@composable
                FishFormScreen(fishId, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable("fish/detail/{fishId}") { entry ->
                val fishId = entry.arguments?.getString("fishId") ?: return@composable
                FishDetailScreen(fishId, onBack = { navController.popBackStack() }, onEdit = { navController.navigate("fish/edit/$fishId") }, onOpenRecords = { section -> navController.navigate("fish/records/$fishId/$section") }, onQuickFeeding = { navController.navigate("feeding?tankId=null&fishId=$fishId") })
            }
            composable("fish/records/{fishId}/{section}") { entry ->
                val fishId = entry.arguments?.getString("fishId") ?: return@composable
                val section = entry.arguments?.getString("section") ?: return@composable
                FishRecordsScreen(fishId, section, onBack = { navController.popBackStack() })
            }
            composable("tank/edit/{tankId}") { entry ->
                val tankId = entry.arguments?.getString("tankId") ?: return@composable
                TankFormScreen(tankId, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable("tank/detail/{tankId}") { entry ->
                val tankId = entry.arguments?.getString("tankId") ?: return@composable
                TankDetailScreen(
                    tankId = tankId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("tank/edit/$tankId") },
                    onQuickAction = { action, id ->
                        if (action.startsWith("change/")) navController.navigate("tank/action/$id/change/${action.substringAfter('/')}")
                        else navController.navigate("tank/action/$id/$action")
                    },
                )
            }
            composable("tank/action/{tankId}/{action}") { entry ->
                val tankId = entry.arguments?.getString("tankId").orEmpty()
                val action = entry.arguments?.getString("action").orEmpty()
                if (action == "feeding") FeedingScreen(tankId, null, onBack = { navController.popBackStack() }) else TankQuickFormScreen(tankId, action, onBack = { navController.popBackStack() })
            }
            composable("tank/action/{tankId}/change/{changeId}") { entry ->
                val tankId = entry.arguments?.getString("tankId").orEmpty()
                val changeId = entry.arguments?.getString("changeId").orEmpty()
                TankQuickFormScreen(tankId, "change", editId = changeId, onBack = { navController.popBackStack() })
            }
            composable("feeding?tankId={tankId}&fishId={fishId}") { entry ->
                val selectedTank = entry.arguments?.getString("tankId")?.takeUnless { it == "null" }
                val selectedFish = entry.arguments?.getString("fishId")?.takeUnless { it == "null" }
                FeedingScreen(selectedTank, selectedFish, onBack = { navController.popBackStack() })
            }
            composable("statistics") {
                StatisticsScreen(onBack = { navController.popBackStack() }, onAddRecord = { action, tankId ->
                    if (action == "growth") navController.navigate(AppDestination.Fish.route)
                    else if (tankId == null) navController.navigate(AppDestination.Tanks.route)
                    else if (action == "feeding") navController.navigate("tank/action/$tankId/feeding")
                    else navController.navigate("tank/action/$tankId/$action")
                })
            }
            composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }, onAbout = { navController.navigate("about") }) }
            composable("about") { AboutHelpScreen(onBack = { navController.popBackStack() }) }
        }
    }
    overviewMessage?.let { message -> AlertDialog(onDismissRequest = { overviewMessage = null }, text = { Text(message) }, confirmButton = { TextButton(onClick = { overviewMessage = null }) { Text("知道了") } }) }
}
