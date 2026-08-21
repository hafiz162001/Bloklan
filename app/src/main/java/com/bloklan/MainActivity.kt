package com.bloklan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bloklan.core.vpn.BloklanVpnService
import com.bloklan.data.repository.AppRepository
import com.bloklan.ui.screens.HomeScreen
import com.bloklan.ui.screens.LogScreen
import com.bloklan.ui.screens.RulesScreen
import com.bloklan.ui.screens.SettingsScreen
import com.bloklan.ui.screens.WebPlayerScreen
import com.bloklan.ui.screens.NativePlayerScreen
import com.bloklan.ui.theme.BgDark
import com.bloklan.ui.theme.BloklanTheme
import com.bloklan.ui.theme.CardBgDark
import com.bloklan.ui.theme.PrimaryNeon
import com.bloklan.ui.theme.TextMuted
import com.bloklan.ui.theme.TextPrimary

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Beranda", Icons.Default.Shield)
    object Player : Screen("player", "Player", Icons.Default.PlayCircleOutline)
    object Logs : Screen("logs", "Monitor", Icons.Default.Dns)
    object Rules : Screen("rules", "Aturan", Icons.Default.Security)
    object Settings : Screen("settings", "Pengaturan", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val repository = AppRepository.instance

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()

        setContent {
            BloklanTheme {
                val navController = rememberNavController()
                var currentRoute by remember { mutableStateOf(Screen.Home.route) }

                val items = listOf(
                    Screen.Home,
                    Screen.Player,
                    Screen.Logs,
                    Screen.Rules,
                    Screen.Settings
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BgDark,
                    bottomBar = {
                        NavigationBar(
                            containerColor = CardBgDark,
                            contentColor = TextPrimary
                        ) {
                            items.forEach { screen ->
                                val selected = currentRoute == screen.route
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title, fontSize = 10.sp, maxLines = 1) },
                                    selected = selected,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            currentRoute = screen.route
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BgDark,
                                        selectedTextColor = PrimaryNeon,
                                        indicatorColor = PrimaryNeon,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onToggleVpn = { toggleVpn() },
                                onNavigateToLogs = {
                                    currentRoute = Screen.Logs.route
                                    navController.navigate(Screen.Logs.route)
                                }
                            )
                        }
                        composable(Screen.Player.route) {
                            NativePlayerScreen()
                        }
                        composable(Screen.Logs.route) {
                            LogScreen()
                        }
                        composable(Screen.Rules.route) {
                            RulesScreen()
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun toggleVpn() {
        if (repository.isVpnActive.value) {
            stopVpnService()
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnLauncher.launch(prepareIntent)
            } else {
                startVpnService()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, BloklanVpnService::class.java).apply {
            action = BloklanVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, BloklanVpnService::class.java).apply {
            action = BloklanVpnService.ACTION_STOP
        }
        startService(intent)
    }
}
