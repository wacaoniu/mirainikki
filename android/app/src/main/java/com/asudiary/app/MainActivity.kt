package com.asudiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asudiary.app.data.Repo
import com.asudiary.app.data.SyncWorker
import com.asudiary.app.ui.HabitsScreen
import com.asudiary.app.ui.HomeScreen
import com.asudiary.app.ui.SettingsScreen
import com.asudiary.app.ui.ShadoujiTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncWorker.scheduleIfConfigured(this)
        setContent {
            ShadoujiTheme { App() }
        }
    }
}

data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun App() {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("home", "记录", Icons.Outlined.Book),
        Tab("habits", "习惯", Icons.Filled.TaskAlt),
        Tab("settings", "设置", Icons.Filled.Settings),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad),
        ) {
            composable("home") { HomeScreen() }
            composable("habits") { HabitsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
