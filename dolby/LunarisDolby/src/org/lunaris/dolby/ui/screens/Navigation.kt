/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.lunaris.dolby.ui.viewmodel.AppProfileViewModel
import org.lunaris.dolby.ui.viewmodel.CustomPresetViewModel
import org.lunaris.dolby.ui.viewmodel.DeviceMemoryViewModel
import org.lunaris.dolby.ui.viewmodel.DolbyViewModel
import org.lunaris.dolby.ui.viewmodel.EqualizerViewModel
import org.lunaris.dolby.ui.viewmodel.ScheduledProfileViewModel
import org.lunaris.dolby.ui.viewmodel.dolbyAndroidViewModel

sealed class Screen(val route: String) {
    object Settings : Screen("settings")
    object Equalizer : Screen("equalizer")
    object Advanced : Screen("advanced")
    object AppProfiles : Screen("app_profiles")
    object DeviceMemory : Screen("device_memory")
    object CustomPresets : Screen("custom_presets")
    object ScheduledProfiles : Screen("scheduled_profiles")
    object ImportExport : Screen("import_export")
    object AutomationSettings : Screen("automation_settings")
    object BluetoothRules : Screen("bluetooth_rules")
    object ProfileHistory : Screen("profile_history")
    object PowerUserAudio : Screen("power_user_audio")

    companion object {
        // Must be lazy: eager init of allRoutes during Screen.<clinit> reads nested
        // objects (Settings, …) before they exist → NPE → ExceptionInInitializerError
        // and DolbyActivity can never open (widget/QS still work).
        val allRoutes: Set<String> by lazy {
            setOf(
                Settings.route,
                Equalizer.route,
                Advanced.route,
                AppProfiles.route,
                DeviceMemory.route,
                CustomPresets.route,
                ScheduledProfiles.route,
                ImportExport.route,
                AutomationSettings.route,
                BluetoothRules.route,
                ProfileHistory.route,
                PowerUserAudio.route
            )
        }
    }
}

@Composable
fun DolbyNavHost(
    dolbyViewModel: DolbyViewModel,
    equalizerViewModel: EqualizerViewModel,
    startRoute: String? = null
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Settings.route
    ) {
        composable(Screen.Settings.route) {
            ModernDolbySettingsScreen(
                viewModel = dolbyViewModel,
                navController = navController
            )
        }

        composable(Screen.Equalizer.route) {
            LaunchedEffect(Unit) {
                equalizerViewModel.loadEqualizer()
            }
            
            ModernEqualizerScreen(
                viewModel = equalizerViewModel,
                navController = navController
            )
        }

        composable(Screen.Advanced.route) {
            ModernAdvancedSettingsScreen(
                viewModel = dolbyViewModel,
                navController = navController
            )
        }
        
        composable(Screen.AppProfiles.route) {
            AppProfileScreen(
                viewModel = dolbyAndroidViewModel<AppProfileViewModel>(),
                navController = navController
            )
        }

        composable(Screen.DeviceMemory.route) {
            DeviceMemoryScreen(
                viewModel = dolbyAndroidViewModel<DeviceMemoryViewModel>(),
                navController = navController
            )
        }
        
        composable(Screen.CustomPresets.route) {
            CustomPresetsScreen(
                viewModel = dolbyAndroidViewModel<CustomPresetViewModel>(),
                dolbyViewModel = dolbyViewModel,
                equalizerViewModel = equalizerViewModel,
                navController = navController
            )
        }

        composable(Screen.ScheduledProfiles.route) {
            ScheduledProfilesScreen(
                viewModel = dolbyAndroidViewModel<ScheduledProfileViewModel>(),
                navController = navController
            )
        }

        composable(Screen.ImportExport.route) {
            PresetImportExportScreen(
                viewModel = equalizerViewModel,
                dolbyViewModel = dolbyViewModel,
                navController = navController
            )
        }

        composable(Screen.AutomationSettings.route) {
            AutomationSettingsScreen(navController = navController)
        }

        composable(Screen.BluetoothRules.route) {
            BluetoothRulesScreen(navController = navController)
        }

        composable(Screen.ProfileHistory.route) {
            ProfileHistoryScreen(navController = navController)
        }


        composable(Screen.PowerUserAudio.route) {
            PowerUserAudioScreen(
                navController = navController,
                dolbyViewModel = dolbyViewModel
            )
        }
    }

    LaunchedEffect(startRoute) {
        val route = startRoute?.takeIf { it in Screen.allRoutes } ?: return@LaunchedEffect
        if (route == Screen.Settings.route) return@LaunchedEffect
        navController.navigate(route) {
            launchSingleTop = true
        }
    }
}
