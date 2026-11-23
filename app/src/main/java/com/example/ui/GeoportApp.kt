package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.BatteryPowerMonitorSheet
import com.example.ui.components.BatteryWarningBanner
import com.example.ui.components.EmfVisualAlertBanner
import com.example.ui.components.NewSessionDialog
import com.example.ui.components.StatusLed
import com.example.ui.screens.EmfRadarScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.LogsExportScreen
import com.example.ui.screens.OvilusScreen
import com.example.ui.screens.RecorderScreen
import com.example.ui.screens.SpiritBoxScreen
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.nightVisionOverlay
import com.example.viewmodel.InvestigationViewModel
import com.example.viewmodel.UiEvent

sealed class GeoportDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object EmfRadar : GeoportDestination(
        route = "emf_radar",
        labelRes = R.string.nav_emf_radar,
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar
    )
    data object SpiritBox : GeoportDestination(
        route = "spirit_box",
        labelRes = R.string.nav_spirit_box,
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq
    )
    data object Ovilus : GeoportDestination(
        route = "ovilus",
        labelRes = R.string.nav_ovilus,
        selectedIcon = Icons.Filled.RecordVoiceOver,
        unselectedIcon = Icons.Outlined.RecordVoiceOver
    )
    data object Recorder : GeoportDestination(
        route = "recorder",
        labelRes = R.string.nav_recorder,
        selectedIcon = Icons.Filled.Mic,
        unselectedIcon = Icons.Outlined.Mic
    )
    data object Journal : GeoportDestination(
        route = "journal",
        labelRes = R.string.nav_journal,
        selectedIcon = Icons.Filled.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook
    )
    data object LogsExport : GeoportDestination(
        route = "logs_export",
        labelRes = R.string.nav_logs_export,
        selectedIcon = Icons.Filled.Assessment,
        unselectedIcon = Icons.Outlined.Assessment
    )

    companion object {
        val entries = listOf(EmfRadar, SpiritBox, Ovilus, Recorder, Journal, LogsExport)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoportApp(
    viewModel: InvestigationViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val sensorStatus by viewModel.sensorStatus.collectAsStateWithLifecycle()
    val emfReading by viewModel.currentEmfReading.collectAsStateWithLifecycle()
    val emfHistory by viewModel.emfHistory.collectAsStateWithLifecycle()
    val temperatureReading by viewModel.temperatureReading.collectAsStateWithLifecycle()
    val trendSamples by viewModel.trendSamples.collectAsStateWithLifecycle()
    val alertConfig by viewModel.alertConfig.collectAsStateWithLifecycle()
    val alertBannerState by viewModel.alertBannerState.collectAsStateWithLifecycle()
    val locationStatus by viewModel.locationStatus.collectAsStateWithLifecycle()
    val spiritBoxState by viewModel.spiritBoxState.collectAsStateWithLifecycle()
    val spiritSpectrum by viewModel.spiritSpectrum.collectAsStateWithLifecycle()
    val ovilusState by viewModel.ovilusState.collectAsStateWithLifecycle()
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val liveWaveform by viewModel.liveWaveform.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val allClips by viewModel.allAudioClips.collectAsStateWithLifecycle()
    val allFlaggedSegments by viewModel.allFlaggedSegments.collectAsStateWithLifecycle()
    val analyzingClipId by viewModel.analyzingClipId.collectAsStateWithLifecycle()
    val waveformCache by viewModel.waveformCache.collectAsStateWithLifecycle()
    val inspectedClipId by viewModel.inspectedClipId.collectAsStateWithLifecycle()
    val inspectedSegmentId by viewModel.inspectedSegmentId.collectAsStateWithLifecycle()
    val recentAnomalies by viewModel.recentAnomalies.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val storageStatus by viewModel.storageStatus.collectAsStateWithLifecycle()
    val isTransferring by viewModel.isTransferring.collectAsStateWithLifecycle()
    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()
    val nightVisionEnabled by viewModel.nightVisionEnabled.collectAsStateWithLifecycle()
    val nightVisionBrightness by viewModel.nightVisionBrightness.collectAsStateWithLifecycle()

    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showBatterySheet by remember { mutableStateOf(false) }
    var pendingTextExport by remember { mutableStateOf<String?>(null) }
    var pendingAudioSourcePath by remember { mutableStateOf<String?>(null) }
    var startRecordingAfterPermission by remember { mutableStateOf(false) }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.setCustomTransferFolder(treeUri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateMicPermission(granted)
        if (granted && startRecordingAfterPermission) {
            startRecordingAfterPermission = false
            viewModel.startAudioRecording()
        } else if (!granted) {
            startRecordingAfterPermission = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.updateLocationPermission(granted)
    }

    val createTextDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val content = pendingTextExport
        pendingTextExport = null
        if (uri != null && content != null) {
            viewModel.writeTextToSafUri(uri, content)
        }
    }

    val createJsonDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingTextExport
        pendingTextExport = null
        if (uri != null && content != null) {
            viewModel.writeTextToSafUri(uri, content)
        }
    }

    val createAudioDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val src = pendingAudioSourcePath
        pendingAudioSourcePath = null
        if (uri != null && src != null) {
            viewModel.copyAudioToSafUri(uri, src)
        }
    }

    LaunchedEffect(Unit) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updateMicPermission(hasMic)

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updateLocationPermission(hasFine || hasCoarse)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.LaunchShareIntent -> {
                    context.startActivity(event.intent)
                }
                is UiEvent.RequestSafSaveText -> {
                    pendingTextExport = event.content
                    if (event.mimeType == "application/json") {
                        createJsonDocLauncher.launch(event.suggestedName)
                    } else {
                        createTextDocLauncher.launch(event.suggestedName)
                    }
                }
                is UiEvent.RequestSafSaveAudio -> {
                    pendingAudioSourcePath = event.sourcePath
                    createAudioDocLauncher.launch(event.suggestedName)
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nightVisionOverlay(nightVisionEnabled, nightVisionBrightness)
    ) {
        val useRail = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = viewModel::toggleNightVision,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (nightVisionEnabled) AnomalyCrimson.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = if (nightVisionEnabled) AnomalyCrimson else TextSecondary
                                ),
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(48.dp)
                                    .testTag("night_vision_toggle")
                            ) {
                                Icon(
                                    imageVector = if (nightVisionEnabled) Icons.Filled.NightsStay else Icons.Outlined.NightsStay,
                                    contentDescription = stringResource(
                                        if (nightVisionEnabled) R.string.cd_night_vision_off else R.string.cd_night_vision_on
                                    )
                                )
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_geoport_logo_1795117550925),
                                    contentDescription = stringResource(R.string.cd_app_logo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "GEOPORT EMF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (locationStatus.currentCoordinate != null) {
                                    StatusLed(active = true, activeColor = GeoportTeal)
                                }
                                if (spiritBoxState.isRunning) {
                                    StatusLed(active = true, activeColor = FranksAmber, pulse = true)
                                }
                                if (ovilusState.isActive) {
                                    StatusLed(active = true, activeColor = PhosphorCyan, pulse = ovilusState.isSpeaking)
                                }
                                if (recorderState.isRecording) {
                                    StatusLed(active = true, activeColor = AnomalyCrimson, pulse = true)
                                }
                                val batColor = when {
                                    batteryState.isCharging -> GeoportTeal
                                    batteryState.isLowPowerAlertActive || batteryState.isThermalAlertActive -> AnomalyCrimson
                                    batteryState.levelPct <= 25 -> FranksAmber
                                    else -> PhosphorCyan
                                }
                                androidx.compose.material3.AssistChip(
                                    onClick = { showBatterySheet = true },
                                    label = {
                                        Text(
                                            text = "${batteryState.levelPct}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = batColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    modifier = Modifier.testTag("top_battery_chip")
                                )
                            }
                        }
                    )
                    EmfVisualAlertBanner(
                        bannerState = alertBannerState,
                        onDismiss = viewModel::dismissVisualAlert
                    )
                    BatteryWarningBanner(
                        batteryState = batteryState,
                        onOpenMonitor = { showBatterySheet = true },
                        onEnableLowPower = viewModel::enableLowPowerMode
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("scrollable_bottom_tool_bar"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GeoportDestination.entries.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    val label = stringResource(dest.labelRes)
                                    val itemBg = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                                    val contentColor = if (selected) MaterialTheme.colorScheme.primary else TextSecondary
                                    val activeBadgeColor = when (dest) {
                                        GeoportDestination.EmfRadar -> if (activeSession != null) GeoportTeal else null
                                        GeoportDestination.SpiritBox -> if (spiritBoxState.isRunning) FranksAmber else null
                                        GeoportDestination.Ovilus -> if (ovilusState.isActive) PhosphorCyan else null
                                        GeoportDestination.Recorder -> if (recorderState.isRecording) AnomalyCrimson else null
                                        GeoportDestination.Journal -> null
                                        GeoportDestination.LogsExport -> null
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 82.dp, minHeight = 56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(itemBg)
                                            .clickable {
                                                navController.navigate(dest.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .testTag("nav_${dest.route}")
                                    ) {
                                        Box(contentAlignment = Alignment.TopEnd) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = label,
                                                tint = if (selected) contentColor else TextMuted,
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .size(22.dp)
                                            )
                                            if (activeBadgeColor != null) {
                                                StatusLed(
                                                    active = true,
                                                    activeColor = activeBadgeColor,
                                                    pulse = true,
                                                    modifier = Modifier.size(7.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = contentColor,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        GeoportDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = GeoportDestination.EmfRadar.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 820.dp)
                    ) {
                        composable(GeoportDestination.EmfRadar.route) {
                            EmfRadarScreen(
                                sensorStatus = sensorStatus,
                                reading = emfReading,
                                history = emfHistory,
                                activeSession = activeSession,
                                alertConfig = alertConfig,
                                onCalibrateClick = viewModel::calibrateEmfBaseline,
                                onManualMarkClick = { viewModel.logManualAnomaly() },
                                onThresholdChange = viewModel::setAnomalyThreshold,
                                onAudibleAlertToggle = viewModel::setAudibleAlertEnabled,
                                onSelectAlertSound = viewModel::setAlertSound,
                                onPreviewAlertSound = viewModel::previewSelectedAlertSound,
                                onHapticToggle = viewModel::setHapticAlertEnabled,
                                onIgnoreMotionToggle = viewModel::setIgnoreMotionAlerts,
                                onStartSessionClick = { showNewSessionDialog = true },
                                onStopSessionClick = viewModel::endActiveInvestigationSession,
                                temperatureReading = temperatureReading,
                                trendSamples = trendSamples,
                                audioClips = allClips,
                                flaggedSegments = allFlaggedSegments,
                                onInspectAudioClip = { clip ->
                                    viewModel.inspectAudioClip(clip)
                                    navController.navigate(GeoportDestination.Recorder.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(GeoportDestination.SpiritBox.route) {
                            SpiritBoxScreen(
                                spiritState = spiritBoxState,
                                spectrumBars = spiritSpectrum,
                                emfReading = emfReading,
                                recorderState = recorderState,
                                onToggleSpiritBox = viewModel::toggleSpiritBox,
                                onModeChange = viewModel::setSpiritBoxMode,
                                onSweepRateChange = viewModel::setSweepRateMs,
                                onDirectionChange = viewModel::setSweepDirection,
                                onEchoChange = viewModel::setEchoDepth,
                                onSquelchChange = viewModel::setSquelch,
                                onGainChange = viewModel::setGain,
                                onEmfCoupledChange = viewModel::setEmfCoupled,
                                onRecordOrStopClick = {
                                    if (recorderState.isRecording) {
                                        viewModel.stopAndSaveAudioRecording()
                                    } else if (recorderState.hasMicPermission) {
                                        viewModel.setMixSpiritBox(true)
                                        viewModel.startAudioRecording()
                                    } else {
                                        viewModel.setMixSpiritBox(true)
                                        startRecordingAfterPermission = true
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            )
                        }

                        composable(GeoportDestination.Ovilus.route) {
                            OvilusScreen(
                                ovilusState = ovilusState,
                                emfReading = emfReading,
                                onToggleActive = viewModel::toggleOvilus,
                                onManualScan = viewModel::triggerOvilusManualScan,
                                onModeChange = viewModel::setOvilusMode,
                                onVoiceProfileChange = viewModel::setOvilusVoiceProfile,
                                onSelectDeviceVoice = viewModel::selectOvilusDeviceVoice,
                                onPitchChange = viewModel::setOvilusPitch,
                                onSpeechRateChange = viewModel::setOvilusSpeechRate,
                                onSensitivityChange = viewModel::setOvilusSensitivity,
                                onCooldownChange = viewModel::setOvilusCooldown,
                                onAutoLogChange = viewModel::setOvilusAutoLog,
                                onTestVoice = viewModel::testOvilusVoice,
                                onSpeakLexiconWord = viewModel::speakOvilusLexiconWord,
                                onAskQuestion = viewModel::askOvilusQuestion,
                                onReplayEntry = viewModel::replayOvilusEntry,
                                onPinToJournal = viewModel::pinOvilusEntryToJournal,
                                onExportCsv = viewModel::exportOvilusCsv,
                                onExportJson = viewModel::exportOvilusJson,
                                onShareTranscript = viewModel::shareOvilusTranscript,
                                onClearLog = viewModel::clearOvilusLog
                            )
                        }

                        composable(GeoportDestination.Recorder.route) {
                            RecorderScreen(
                                recorderState = recorderState,
                                liveWaveform = liveWaveform,
                                spiritBoxRunning = spiritBoxState.isRunning,
                                clips = allClips,
                                flaggedSegments = allFlaggedSegments,
                                analyzingClipId = analyzingClipId,
                                playbackState = playbackState,
                                waveformCache = waveformCache,
                                inspectedClipId = inspectedClipId,
                                inspectedSegmentId = inspectedSegmentId,
                                storageStatus = storageStatus,
                                isTransferring = isTransferring,
                                onRefreshStorage = { viewModel.refreshStorageVolumes(notifyIfSdFound = true) },
                                onSelectStorageMode = viewModel::setStorageTargetMode,
                                onPickCustomFolder = { openFolderLauncher.launch(null) },
                                onTransferAllFiles = viewModel::transferAllFilesToSaveLocation,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onStartRecording = viewModel::startAudioRecording,
                                onTogglePause = viewModel::togglePauseRecording,
                                onStopRecording = viewModel::stopAndSaveAudioRecording,
                                onMixSpiritBoxChange = viewModel::setMixSpiritBox,
                                onNoiseSuppressionChange = viewModel::setNoiseSuppressionLevel,
                                onHumNotchChange = viewModel::setMainsHumNotchEnabled,
                                onTagMarker = viewModel::tagRecordingMarker,
                                onAnalyzeClip = viewModel::analyzeAudioClip,
                                onPlayPauseClip = viewModel::toggleClipPlayback,
                                onPlayPauseSegment = viewModel::toggleSegmentPlayback,
                                onInspectClip = viewModel::inspectAudioClip,
                                onInspectSegment = viewModel::inspectFlaggedSegment,
                                onCloseInspector = viewModel::closeWaveformInspector,
                                onSeekAndPrepareClip = { clip, posMs ->
                                    viewModel.seekAndPrepareClip(clip, posMs)
                                },
                                onSeekAndPrepareSegment = { seg, posMs ->
                                    viewModel.seekAndPrepareSegment(seg, posMs)
                                },
                                onLoopRangeChange = viewModel::setPlaybackLoopWindow,
                                onLoadWaveform = viewModel::loadWaveformForFile,
                                onSeekPlayback = viewModel::seekPlayback,
                                onPlaybackSpeedChange = viewModel::setPlaybackSpeed,
                                onSaveCopyToDevice = viewModel::requestSaveClipToDevice,
                                onSaveSegmentToDevice = viewModel::requestSaveSegmentToDevice,
                                onShareClip = viewModel::shareAudioClip,
                                onShareSegment = viewModel::shareFlaggedSegment,
                                onDeleteClip = viewModel::deleteAudioClip,
                                onDeleteSegment = viewModel::deleteFlaggedSegment
                            )
                        }

                        composable(GeoportDestination.Journal.route) {
                            JournalScreen(
                                locationStatus = locationStatus,
                                activeSession = activeSession,
                                currentEmfReading = emfReading,
                                recentAnomalies = recentAnomalies,
                                audioClips = allClips,
                                journalEntries = journalEntries,
                                onRequestLocationPermission = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onStartSessionClick = { showNewSessionDialog = true },
                                onStopSessionClick = viewModel::endActiveInvestigationSession,
                                onSaveJournalEntry = viewModel::addJournalEntry,
                                onPlayClip = viewModel::toggleClipPlayback,
                                onDeleteEntry = viewModel::deleteJournalEntry,
                                onExportJournalCsv = viewModel::exportJournalCsv,
                                onExportJournalJson = viewModel::exportJournalJson,
                                onShareJournal = viewModel::shareJournalReport
                            )
                        }

                        composable(GeoportDestination.LogsExport.route) {
                            LogsExportScreen(
                                sessions = sessions,
                                activeSession = activeSession,
                                selectedSessionId = selectedSessionId,
                                storageStatus = storageStatus,
                                isTransferring = isTransferring,
                                onRefreshStorage = { viewModel.refreshStorageVolumes(notifyIfSdFound = true) },
                                onSelectStorageMode = viewModel::setStorageTargetMode,
                                onPickCustomFolder = { openFolderLauncher.launch(null) },
                                onTransferAllFiles = viewModel::transferAllFilesToSaveLocation,
                                observeAnomalies = viewModel::observeAnomaliesForSession,
                                observeClips = viewModel::observeClipsForSession,
                                onSelectSession = viewModel::selectSessionForInspection,
                                onStartSessionClick = { showNewSessionDialog = true },
                                onStopSessionClick = viewModel::endActiveInvestigationSession,
                                onDeleteSession = viewModel::deleteSession,
                                onExportCsv = viewModel::exportSessionCsv,
                                onExportJson = viewModel::exportSessionJson,
                                onShareReport = viewModel::shareSessionReport,
                                observeTrendSamples = viewModel::observeTrendSamplesForSession,
                                allFlaggedSegments = allFlaggedSegments,
                                onInspectAudioClip = { clip ->
                                    viewModel.inspectAudioClip(clip)
                                    navController.navigate(GeoportDestination.Recorder.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewSessionDialog) {
        NewSessionDialog(
            onDismiss = { showNewSessionDialog = false },
            onConfirm = { title, location, notes ->
                showNewSessionDialog = false
                if (!locationStatus.hasPermission) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                viewModel.startInvestigationSession(title, location, notes)
            }
        )
    }

    if (showBatterySheet) {
        BatteryPowerMonitorSheet(
            batteryState = batteryState,
            nightVisionEnabled = nightVisionEnabled,
            nightVisionBrightness = nightVisionBrightness,
            onNightVisionChange = viewModel::setNightVisionEnabled,
            onNightVisionBrightnessChange = viewModel::setNightVisionBrightness,
            onThresholdChange = viewModel::setBatteryWarningThreshold,
            onAutoProtectChange = viewModel::setAutoProtectRecording,
            onEnableLowPower = viewModel::enableLowPowerMode,
            onDismiss = { showBatterySheet = false }
        )
    }
}
