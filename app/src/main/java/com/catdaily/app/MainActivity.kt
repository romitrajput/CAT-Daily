package com.catdaily.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.catdaily.app.ui.CatTheme
import com.catdaily.app.ui.PracticeScreen
import com.catdaily.app.ui.QuestionScreen
import com.catdaily.app.ui.TodayScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { CatTheme { AppRoot(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@androidx.compose.runtime.Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    val tabs = listOf(
        Tab("today", "Today", Icons.Filled.LocalFireDepartment),
        Tab("practice", "Sections", Icons.Filled.MenuBook),
    )
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (tabs.any { it.route == route }) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo("today"); launchSingleTop = true } },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(pad)) {
            composable("today") { TodayScreen(vm) { id -> nav.navigate("q/$id?daily=true") } }
            composable("practice") { PracticeScreen(vm) { id -> nav.navigate("q/$id?daily=false") } }
            composable(
                "q/{id}?daily={daily}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("daily") { type = NavType.BoolType; defaultValue = false })
            ) { e ->
                val id = e.arguments?.getString("id") ?: return@composable
                val daily = e.arguments?.getBoolean("daily") ?: false
                QuestionScreen(vm, id, daily,
                    onBack = { nav.popBackStack() },
                    onNext = { next -> nav.navigate("q/$next?daily=$daily") { popUpTo("q/{id}?daily={daily}") { inclusive = true } } })
            }
        }
    }
}
