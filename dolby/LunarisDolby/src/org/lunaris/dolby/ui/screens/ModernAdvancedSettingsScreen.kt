/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.R
import org.lunaris.dolby.domain.models.AudioDeviceCategory
import org.lunaris.dolby.domain.models.DolbyUiState
import org.lunaris.dolby.ui.components.*
import org.lunaris.dolby.ui.viewmodel.DolbyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModernAdvancedSettingsScreen(
    viewModel: DolbyViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentRoute by navController.currentBackStackEntryFlow.collectAsState(null)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.dolby_category_adv_settings),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DolbyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is DolbyUiState.Success -> {
                ModernAdvancedSettingsContent(
                    state = state,
                    viewModel = viewModel,
                    navController = navController,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is DolbyUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { viewModel.loadSettings() },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.app_profiles_retry))
                        }
                    }
                }
            }
        }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = cutoutInsets.calculateStartPadding(layoutDirection),
                        end = cutoutInsets.calculateEndPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                contentAlignment = Alignment.Center
            ) {
                FloatingNavToolbar(
                    currentRoute = currentRoute?.destination?.route ?: "settings",
                    onNavigate = { route ->
                        if (currentRoute?.destination?.route != route) {
                            navController.navigate(route) {
                                popUpTo("settings") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ModernAdvancedSettingsContent(
    state: DolbyUiState.Success,
    viewModel: DolbyViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.settings.enabled) {
            item {
                val context = LocalContext.current
                val headroom = viewModel.getHeadroomInfo()
                val engine = remember { org.lunaris.dolby.data.AudioEnginePreferences(context) }
                val repository = remember { org.lunaris.dolby.data.DolbyRepository(context) }
                var highPassOn by remember { mutableStateOf(engine.isHighPassEnabled()) }
                var highPassHz by remember { mutableIntStateOf(engine.getHighPassHz()) }
                DisposableEffect(Unit) {
                    onDispose { repository.close() }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (engine.isHeadroomWarningEnabled() && headroom != null) {
                        org.lunaris.dolby.ui.components.HeadroomWarningCard(headroom)
                    }
                    ModernSettingsCard(
                        title = stringResource(R.string.processing_section),
                        icon = Icons.Default.Tune
                    ) {
                        ModernSettingSwitch(
                            title = stringResource(R.string.high_pass_enabled),
                            subtitle = stringResource(R.string.high_pass_hz_label, highPassHz),
                            checked = highPassOn,
                            onCheckedChange = {
                                highPassOn = it
                                engine.setBoolean(org.lunaris.dolby.DolbyConstants.PREF_HIGH_PASS_ENABLED, it)
                                repository.refreshSpatialAndGeq(state.settings.currentProfile)
                            },
                            icon = Icons.Default.FilterList
                        )
                        AnimatedVisibility(visible = highPassOn) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.high_pass_hz_label, highPassHz),
                                    value = highPassHz,
                                    onValueChange = {
                                        highPassHz = it.toInt()
                                        context.getSharedPreferences("dolby_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit()
                                            .putInt(org.lunaris.dolby.DolbyConstants.PREF_HIGH_PASS_HZ, it.toInt())
                                            .apply()
                                        repository.refreshSpatialAndGeq(state.settings.currentProfile)
                                    },
                                    valueRange = 20f..120f,
                                    steps = 10,
                                    valueLabel = { "${it} Hz" }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.PowerUserAudio.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.power_user_audio_title))
                    }
                }
            }
            item {
                ModernSettingsCard(
                    title = stringResource(R.string.dolby_category_settings),
                    icon = Icons.Default.Tune
                ) {
                    ModernSettingSwitch(
                        title = stringResource(R.string.graphic_eq_enable_title),
                        subtitle = stringResource(R.string.graphic_eq_enable_summary),
                        checked = state.profileSettings.graphicEqEnabled,
                        onCheckedChange = { viewModel.setGraphicEqEnabled(it) },
                        icon = Icons.Default.Equalizer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_bass_enhancer),
                            subtitle = stringResource(R.string.dolby_bass_enhancer_summary),
                            checked = state.profileSettings.bassLevel > 0,
                            onCheckedChange = { enabled ->
                                if (enabled && state.profileSettings.bassLevel == 0) {
                                    viewModel.setBassLevel(50)
                                } else if (!enabled) {
                                    viewModel.setBassLevel(0)
                                }
                            },
                            icon = Icons.Default.MusicNote
                        )
                        
                        AnimatedVisibility(visible = state.profileSettings.bassLevel > 0) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSelector(
                                    title = stringResource(R.string.dolby_bass_curve),
                                    currentValue = state.profileSettings.bassCurve,
                                    entries = R.array.dolby_bass_curve_entries,
                                    values = R.array.dolby_bass_curve_values,
                                    onValueChange = { viewModel.setBassCurve(it) },
                                    icon = Icons.Default.Equalizer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_bass_level),
                                    value = state.profileSettings.bassLevel,
                                    onValueChange = { viewModel.setBassLevel(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    valueLabel = { "$it%" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_mid_enhancer),
                            subtitle = stringResource(R.string.dolby_mid_enhancer_summary),
                            checked = state.profileSettings.midLevel > 0,
                            onCheckedChange = { enabled ->
                                if (enabled && state.profileSettings.midLevel == 0) {
                                    viewModel.setMidLevel(40)
                                } else if (!enabled) {
                                    viewModel.setMidLevel(0)
                                }
                            },
                            icon = Icons.Default.VolumeUp
                        )

                        AnimatedVisibility(visible = state.profileSettings.midLevel > 0) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_mid_level),
                                    value = state.profileSettings.midLevel,
                                    onValueChange = { viewModel.setMidLevel(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    valueLabel = { "$it%" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_treble_enhancer),
                            subtitle = stringResource(R.string.dolby_treble_enhancer_summary),
                            checked = state.profileSettings.trebleLevel > 0,
                            onCheckedChange = { enabled ->
                                if (enabled && state.profileSettings.trebleLevel == 0) {
                                    viewModel.setTrebleLevel(30)
                                } else if (!enabled) {
                                    viewModel.setTrebleLevel(0)
                                }
                            },
                            icon = Icons.Default.GraphicEq
                        )

                        AnimatedVisibility(visible = state.profileSettings.trebleLevel > 0) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_treble_level),
                                    value = state.profileSettings.trebleLevel,
                                    onValueChange = { viewModel.setTrebleLevel(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    valueLabel = { "$it%" }
                                )
                            }
                        }
                    }
                }
            }

            item {
                AudioTuningSettingsCard(
                    profileSettings = state.profileSettings,
                    isOnBluetooth = state.activeAudioDevice.category == AudioDeviceCategory.BLUETOOTH,
                    viewModel = viewModel
                )
            }
            if (state.settings.currentProfile != 0) {
                item {
                    ModernSettingsCard(
                        title = stringResource(R.string.dolby_spk_virtualizer),
                        icon = Icons.Default.Headphones
                    ) {
                        if (state.isOnSpeaker) {
                            ModernSettingSwitch(
                                title = stringResource(R.string.dolby_spk_virtualizer),
                                subtitle = stringResource(R.string.dolby_spk_virtualizer_summary),
                                checked = state.profileSettings.speakerVirtualizerEnabled,
                                onCheckedChange = { viewModel.setSpeakerVirtualizer(it) },
                                icon = Icons.Default.Speaker
                            )
                        } else {
                            ModernSettingSwitch(
                                title = stringResource(R.string.dolby_hp_virtualizer),
                                subtitle = stringResource(R.string.dolby_hp_virtualizer_summary),
                                checked = state.profileSettings.headphoneVirtualizerEnabled,
                                onCheckedChange = { viewModel.setHeadphoneVirtualizer(it) },
                                icon = Icons.Default.Headphones
                            )
                            
                            AnimatedVisibility(visible = state.profileSettings.headphoneVirtualizerEnabled) {
                                Column {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    ModernSettingSlider(
                                        title = stringResource(R.string.dolby_hp_virtualizer_dolby_strength),
                                        value = state.profileSettings.stereoWideningAmount,
                                        onValueChange = { viewModel.setStereoWidening(it.toInt()) },
                                        valueRange = 4f..64f,
                                        steps = 59
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ModernSettingSlider(
                                        title = stringResource(R.string.hp_virt_mode_title),
                                        value = state.profileSettings.hpVirtMode,
                                        onValueChange = {
                                            viewModel.setHeadphoneVirtualizerTuning(
                                                it.toInt(),
                                                state.profileSettings.hpVirtLrAngle,
                                                state.profileSettings.hpVirtStartBand
                                            )
                                        },
                                        valueRange = 0f..1f,
                                        steps = 0,
                                        valueLabel = { if (it == 0) "0" else "1" }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ModernSettingSlider(
                                        title = stringResource(R.string.hp_virt_angle_title),
                                        value = state.profileSettings.hpVirtLrAngle,
                                        onValueChange = {
                                            viewModel.setHeadphoneVirtualizerTuning(
                                                state.profileSettings.hpVirtMode,
                                                it.toInt(),
                                                state.profileSettings.hpVirtStartBand
                                            )
                                        },
                                        valueRange = 0f..90f,
                                        steps = 17,
                                        valueLabel = { "$it°" }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ModernSettingSlider(
                                        title = stringResource(R.string.hp_virt_start_band_title),
                                        value = state.profileSettings.hpVirtStartBand,
                                        onValueChange = {
                                            viewModel.setHeadphoneVirtualizerTuning(
                                                state.profileSettings.hpVirtMode,
                                                state.profileSettings.hpVirtLrAngle,
                                                it.toInt()
                                            )
                                        },
                                        valueRange = 0f..19f,
                                        steps = 18,
                                        valueLabel = { "$it" }
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    ModernSettingsCard(
                        title = stringResource(R.string.dolby_dialogue_enhancer),
                        icon = Icons.Default.RecordVoiceOver
                    ) {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_dialogue_enhancer),
                            subtitle = stringResource(R.string.dolby_dialogue_enhancer_summary),
                            checked = state.profileSettings.dialogueEnhancerEnabled,
                            onCheckedChange = { viewModel.setDialogueEnhancer(it) },
                            icon = Icons.Default.RecordVoiceOver
                        )
                        
                        AnimatedVisibility(visible = state.profileSettings.dialogueEnhancerEnabled) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_dialogue_enhancer_dolby_strength),
                                    value = state.profileSettings.dialogueEnhancerAmount,
                                    onValueChange = { viewModel.setDialogueEnhancerAmount(it.toInt()) },
                                    valueRange = 1f..12f,
                                    steps = 10
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        ModernSettingSwitch(
                            title = stringResource(R.string.dialogue_ducking_title),
                            subtitle = stringResource(R.string.dialogue_ducking_summary),
                            checked = state.profileSettings.dialogueDuckingEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setDialogueDucking(
                                    enabled,
                                    state.profileSettings.dialogueDuckingAmount
                                )
                            },
                            icon = Icons.Default.VoiceOverOff
                        )
                        AnimatedVisibility(visible = state.profileSettings.dialogueDuckingEnabled) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dialogue_ducking_amount_title),
                                    value = state.profileSettings.dialogueDuckingAmount,
                                    onValueChange = { viewModel.setDialogueDucking(true, it.toInt()) },
                                    valueRange = 0f..16f,
                                    steps = 15,
                                    valueLabel = { "$it" }
                                )
                            }
                        }
                    }
                }
            }
        } else if (state.settings.currentProfile == 0 && state.settings.enabled) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.dolby_adv_settings_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        } else if (!state.settings.enabled) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.dolby_adv_settings_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
private fun AudioTuningSettingsCard(
    profileSettings: org.lunaris.dolby.domain.models.ProfileSettings,
    isOnBluetooth: Boolean,
    viewModel: DolbyViewModel
) {
    val context = LocalContext.current
    ModernSettingsCard(
        title = stringResource(R.string.audio_tuning_title),
        icon = Icons.Default.VolumeUp
    ) {
        ModernSettingSwitch(
            title = stringResource(R.string.output_boost_title),
            subtitle = stringResource(R.string.output_boost_summary),
            checked = profileSettings.outputBoostEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val tenths = if (profileSettings.outputBoostTenthsDb == 0) 30 else profileSettings.outputBoostTenthsDb
                    viewModel.setOutputBoost(true, tenths)
                } else {
                    viewModel.setOutputBoost(false, profileSettings.outputBoostTenthsDb)
                }
            },
            icon = Icons.Default.TrendingUp
        )
        AnimatedVisibility(visible = profileSettings.outputBoostEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.output_boost_title),
                    value = profileSettings.outputBoostTenthsDb,
                    onValueChange = { viewModel.setOutputBoost(true, it.toInt()) },
                    valueRange = viewModel.getOutputBoostMinTenths().toFloat()..
                        viewModel.getOutputBoostMaxTenths().toFloat(),
                    steps = (viewModel.getOutputBoostMaxTenths() - viewModel.getOutputBoostMinTenths() - 1).coerceAtLeast(0),
                    valueLabel = { tenths ->
                        context.getString(R.string.output_boost_value, tenths / 10f)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (isOnBluetooth) {
            ModernSettingSwitch(
                title = stringResource(R.string.virtual_bass_bluetooth_title),
                subtitle = stringResource(R.string.virtual_bass_bluetooth_summary),
                checked = profileSettings.virtualBassBluetoothEnabled,
                onCheckedChange = { viewModel.setVirtualBassBluetooth(it) },
                icon = Icons.Default.Bluetooth
            )
        } else {
            ModernSettingSwitch(
                title = stringResource(R.string.virtual_bass_speaker_title),
                subtitle = stringResource(R.string.virtual_bass_speaker_summary),
                checked = profileSettings.virtualBassSpeakerEnabled,
                onCheckedChange = { viewModel.setVirtualBassSpeaker(it) },
                icon = Icons.Default.GraphicEq
            )
        }
        AnimatedVisibility(
            visible = if (isOnBluetooth) {
                profileSettings.virtualBassBluetoothEnabled
            } else {
                profileSettings.virtualBassSpeakerEnabled
            }
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.vb_mode_title),
                    value = profileSettings.virtualBassMode,
                    onValueChange = {
                        viewModel.setVirtualBassDetails(
                            it.toInt(),
                            profileSettings.virtualBassOverallGain,
                            profileSettings.virtualBassSlopeGain
                        )
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                    valueLabel = { "$it" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.vb_overall_gain_title),
                    value = profileSettings.virtualBassOverallGain,
                    onValueChange = {
                        viewModel.setVirtualBassDetails(
                            profileSettings.virtualBassMode,
                            it.toInt(),
                            profileSettings.virtualBassSlopeGain
                        )
                    },
                    valueRange = -320f..0f,
                    steps = 31,
                    valueLabel = { "$it" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.vb_slope_gain_title),
                    value = profileSettings.virtualBassSlopeGain,
                    onValueChange = {
                        viewModel.setVirtualBassDetails(
                            profileSettings.virtualBassMode,
                            profileSettings.virtualBassOverallGain,
                            it.toInt()
                        )
                    },
                    valueRange = -32f..32f,
                    steps = 31,
                    valueLabel = { "$it" }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.adv_bass_title),
            subtitle = stringResource(R.string.adv_bass_summary),
            checked = profileSettings.advancedBassEnabled,
            onCheckedChange = { enabled ->
                viewModel.setAdvancedBass(
                    enabled,
                    profileSettings.advancedBassBoost,
                    profileSettings.advancedBassCutoff,
                    profileSettings.advancedBassWidth
                )
            },
            icon = Icons.Default.Speaker
        )
        AnimatedVisibility(visible = profileSettings.advancedBassEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.adv_bass_boost_title),
                    value = profileSettings.advancedBassBoost,
                    onValueChange = {
                        viewModel.setAdvancedBass(
                            true,
                            it.toInt(),
                            profileSettings.advancedBassCutoff,
                            profileSettings.advancedBassWidth
                        )
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    valueLabel = { "$it%" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.adv_bass_cutoff_title),
                    value = profileSettings.advancedBassCutoff,
                    onValueChange = {
                        viewModel.setAdvancedBass(
                            true,
                            profileSettings.advancedBassBoost,
                            it.toInt(),
                            profileSettings.advancedBassWidth
                        )
                    },
                    valueRange = 50f..1000f,
                    steps = 18,
                    valueLabel = { "$it Hz" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.adv_bass_width_title),
                    value = profileSettings.advancedBassWidth,
                    onValueChange = {
                        viewModel.setAdvancedBass(
                            true,
                            profileSettings.advancedBassBoost,
                            profileSettings.advancedBassCutoff,
                            it.toInt()
                        )
                    },
                    valueRange = 1f..16f,
                    steps = 14,
                    valueLabel = { "$it" }
                )
            }
        }
    }
}
