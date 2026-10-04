/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.BluetoothProfileManager
import org.lunaris.dolby.data.DeviceStateManager
import org.lunaris.dolby.data.MediaContentRulesManager
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.ui.viewmodel.AutomationSettingsViewModel
import org.lunaris.dolby.ui.viewmodel.dolbyAndroidViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(navController: NavController) {
    val viewModel: AutomationSettingsViewModel = dolbyAndroidViewModel()
    val state by viewModel.uiState.collectAsState()
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values)
    var newPackageName by remember { mutableStateOf("") }
    var newContentType by remember { mutableStateOf(MediaContentRulesManager.CONTENT_TYPES.first()) }
    var contentTypeExpanded by remember { mutableStateOf(false) }
    val contentTypeLabels = mapOf(
        "music" to stringResource(R.string.media_type_music),
        "video" to stringResource(R.string.media_type_video),
        "game" to stringResource(R.string.media_type_game),
        "speech" to stringResource(R.string.media_type_speech)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionTitle(stringResource(R.string.sleep_timer_title)) }
            item {
                Text(stringResource(R.string.sleep_timer_desc))
                Slider(
                    value = state.sleepMinutes.toFloat(),
                    onValueChange = { viewModel.setSleepMinutes(it.toInt()) },
                    valueRange = 5f..120f,
                    steps = 22
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.startSleepTimer() }) {
                        Text(stringResource(R.string.sleep_timer_start))
                    }
                    if (state.activeTimer != null) {
                        OutlinedButton(onClick = { viewModel.cancelSleepTimer() }) {
                            Text(stringResource(R.string.sleep_timer_cancel))
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.widget_sleep_duration_setting)) }
            item {
                Text(
                    stringResource(R.string.widget_sleep_duration_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DolbyConstants.WIDGET_SLEEP_MINUTE_OPTIONS.forEach { mins ->
                        FilterChip(
                            selected = state.widgetSleepMinutes == mins,
                            onClick = { viewModel.setWidgetSleepMinutes(mins) },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.automation_features_title)) }
            item {
                PrefSwitch(stringResource(R.string.battery_saver_mode), state.batterySaver) {
                    viewModel.setBatterySaver(it)
                }
            }
            item {
                PrefSwitch(stringResource(R.string.auto_disable_on_call), state.autoCall) {
                    viewModel.setAutoCall(it)
                }
            }
            item {
                PrefSwitch(stringResource(R.string.media_content_detection), state.mediaDetect) {
                    viewModel.setMediaDetect(it)
                }
            }
            if (state.mediaDetect) {
                item {
                    Text(
                        stringResource(R.string.media_content_mapping_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    MediaProfilePicker(
                        label = stringResource(R.string.media_type_music),
                        selectedProfile = state.musicProfile,
                        profiles = profiles,
                        profileValues = profileValues,
                        onSelected = { viewModel.setMediaProfile("music", it) }
                    )
                }
                item {
                    MediaProfilePicker(
                        label = stringResource(R.string.media_type_video),
                        selectedProfile = state.videoProfile,
                        profiles = profiles,
                        profileValues = profileValues,
                        onSelected = { viewModel.setMediaProfile("video", it) }
                    )
                }
                item {
                    MediaProfilePicker(
                        label = stringResource(R.string.media_type_game),
                        selectedProfile = state.gameProfile,
                        profiles = profiles,
                        profileValues = profileValues,
                        onSelected = { viewModel.setMediaProfile("game", it) }
                    )
                }
                item {
                    MediaProfilePicker(
                        label = stringResource(R.string.media_type_speech),
                        selectedProfile = state.speechProfile,
                        profiles = profiles,
                        profileValues = profileValues,
                        onSelected = { viewModel.setMediaProfile("speech", it) }
                    )
                }
                item { SectionTitle(stringResource(R.string.media_package_overrides_title)) }
                items(
                    state.packageOverrides.entries.toList(),
                    key = { it.key }
                ) { (pkg, type) ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pkg, fontWeight = FontWeight.SemiBold)
                                Text(
                                    contentTypeLabels[type] ?: type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.removePackageOverride(pkg) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.a11y_delete))
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newPackageName,
                            onValueChange = { newPackageName = it },
                            label = { Text(stringResource(R.string.media_package_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ExposedDropdownMenuBox(
                            expanded = contentTypeExpanded,
                            onExpandedChange = { contentTypeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = contentTypeLabels[newContentType] ?: newContentType,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(contentTypeExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = contentTypeExpanded,
                                onDismissRequest = { contentTypeExpanded = false }
                            ) {
                                MediaContentRulesManager.CONTENT_TYPES.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(contentTypeLabels[type] ?: type) },
                                        onClick = {
                                            newContentType = type
                                            contentTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.addPackageOverride(newPackageName, newContentType)
                                newPackageName = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.media_package_add))
                        }
                    }
                }
            }
            item {
                PrefSwitch(stringResource(R.string.game_latency_mode), state.gameLatency) {
                    viewModel.setGameLatency(it)
                }
            }
            item {
                PrefSwitch(stringResource(R.string.focus_mode_integration), state.focusMode) {
                    viewModel.setFocusMode(it)
                }
            }
            item { SectionTitle(stringResource(R.string.ui_settings_title)) }
            item {
                PrefSwitch(stringResource(R.string.simple_ui_mode), state.simpleUi) {
                    viewModel.setSimpleUi(it)
                }
            }
            item {
                PrefSwitch(stringResource(R.string.amoled_theme), state.amoled) {
                    viewModel.setAmoled(it)
                }
            }
            item {
                Text(stringResource(R.string.safe_listening_limit))
                Slider(
                    value = state.safeLimit,
                    onValueChange = { viewModel.setSafeLimit(it) },
                    valueRange = 0f..100f,
                    steps = 20
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun PrefSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val onLabel = stringResource(R.string.a11y_switch_on)
    val offLabel = stringResource(R.string.a11y_switch_off)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = if (checked) onLabel else offLabel
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaProfilePicker(
    label: String,
    selectedProfile: Int,
    profiles: Array<String>,
    profileValues: Array<String>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIndex = profileValues.indexOf(selectedProfile.toString()).coerceAtLeast(0)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = profiles.getOrElse(selectedIndex) { "?" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        profileValues.getOrNull(index)?.toIntOrNull()?.let(onSelected)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothRulesScreen(navController: NavController) {
    val context = LocalContext.current
    val btManager = remember { BluetoothProfileManager(context) }
    var rules by remember { mutableStateOf(btManager.getRules()) }
    val bondedDevices = remember { btManager.getBondedDevices() }
    val audioManager = context.getSystemService(AudioManager::class.java)
    val connectedBtDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values)
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDeviceKey by remember { mutableStateOf<String?>(null) }
    var selectedDeviceName by remember { mutableStateOf("") }
    var selectedProfileIndex by remember { mutableIntStateOf(0) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    val deviceChoices = remember(bondedDevices, connectedBtDevice) {
        val deviceStateManager = DeviceStateManager(context)
        buildList {
            bondedDevices.forEach { add(it.deviceKey to it.displayName) }
            connectedBtDevice?.let { device ->
                val key = deviceStateManager.deviceKey(device)
                val name = deviceStateManager.deviceDisplayName(device)
                if (none { it.first == key }) {
                    add(0, key to name)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bt_rules_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (deviceChoices.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val first = deviceChoices.first()
                        selectedDeviceKey = first.first
                        selectedDeviceName = first.second
                        selectedProfileIndex = 0
                        showAddDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.a11y_add)) },
                    text = { Text(stringResource(R.string.bt_rules_add_paired)) }
                )
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    stringResource(R.string.bt_rules_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if (rules.isEmpty()) {
                item { Text(stringResource(R.string.bt_rules_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(rules, key = { it.deviceKey }) { rule ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(rule.displayName, fontWeight = FontWeight.SemiBold)
                                val idx = profileValues.indexOf(rule.profileId.toString())
                                Text(
                                    profiles.getOrElse(idx.coerceAtLeast(0)) { "Profile ${rule.profileId}" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = {
                                btManager.deleteRule(rule.deviceKey)
                                rules = btManager.getRules()
                            }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.a11y_delete)) }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && deviceChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.bt_rules_pick_device)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = deviceMenuExpanded,
                        onExpandedChange = { deviceMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedDeviceName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.bt_rules_device_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(deviceMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = deviceMenuExpanded,
                            onDismissRequest = { deviceMenuExpanded = false }
                        ) {
                            deviceChoices.forEach { (key, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedDeviceKey = key
                                        selectedDeviceName = name
                                        deviceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = profileMenuExpanded,
                        onExpandedChange = { profileMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = profiles.getOrElse(selectedProfileIndex) { "?" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.bt_rules_pick_profile)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = profileMenuExpanded,
                            onDismissRequest = { profileMenuExpanded = false }
                        ) {
                            profiles.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedProfileIndex = index
                                        profileMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val key = selectedDeviceKey ?: return@Button
                    val profileId = profileValues.getOrNull(selectedProfileIndex)?.toIntOrNull() ?: 2
                    btManager.addRule(key, selectedDeviceName, profileId)
                    rules = btManager.getRules()
                    showAddDialog = false
                }) {
                    Text(stringResource(R.string.bt_rules_add_paired))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val historyManager = remember { ProfileChangeHistoryManager(context) }
    var history by remember { mutableStateOf(historyManager.getHistory()) }
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val values = stringArrayResource(R.array.dolby_profile_values)
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        historyManager.clearHistory()
                        history = emptyList()
                    }) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.a11y_clear_history)) }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (history.isEmpty()) {
                item { Text(stringResource(R.string.profile_history_empty)) }
            } else {
                items(history, key = { it.timestamp }) { entry ->
                    val idx = values.indexOf(entry.profileId.toString())
                    val profileName = profiles.getOrElse(idx.coerceAtLeast(0)) { "?" }
                    ListItem(
                        headlineContent = { Text(profileName) },
                        supportingContent = { Text("${entry.source.key}: ${entry.detail}") },
                        trailingContent = { Text(fmt.format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}
