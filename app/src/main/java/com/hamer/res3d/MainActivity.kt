package com.hamer.res3d

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hamer.res3d.ui.theme.Pico3dResolutionTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NavTab {
    HOME,
    RENDERING,
    PERFORMANCE,
    ABOUT
}

@Composable
fun HelpButton(title: String, message: String) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier.size(18.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = "Help",
            tint = Color.LightGray.copy(alpha = 0.5f)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
            text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        stringResource(id = R.string.close),
                        color = colorResource(id = R.color.toggle_button)
                    )
                }
            },
            containerColor = colorResource(id = R.color.card_bg),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}

@Composable
fun rememberLauncherIcon(): ImageBitmap? {
    val context = LocalContext.current
    return remember {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
        drawable?.let {
            val bitmap = Bitmap.createBitmap(
                it.intrinsicWidth.coerceAtLeast(1),
                it.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            bitmap.asImageBitmap()
        }
    }
}

@Composable
fun Modifier.picoScrollbar(
    state: ScrollState,
    rightOffset: Dp = 4.dp
): Modifier {
    val scrollbarColor = colorResource(id = R.color.scrollbar)
    return this.drawWithContent {
        drawContent()
        if (state.maxValue > 0) {
            val verticalInset = 12.dp.toPx()
            val barWidth = 3.dp.toPx()
            val viewHeight = size.height
            val drawableHeight = viewHeight - (verticalInset * 2)

            val minKnobHeight = 16.dp.toPx()
            val maxKnobHeight = 300.dp.toPx()
            val contentRatio = drawableHeight / (drawableHeight + state.maxValue)
            val knobHeight = (drawableHeight * contentRatio)
                .coerceIn(minKnobHeight, maxKnobHeight)

            val scrollProgress = state.value.toFloat() / state.maxValue
            val topOffset = verticalInset + (scrollProgress * (drawableHeight - knobHeight))

            val xPos = size.width - rightOffset.toPx() - barWidth

            drawRoundRect(
                color = scrollbarColor,
                topLeft = Offset(xPos, topOffset),
                size = Size(barWidth, knobHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Pico3dResolutionTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    ResolutionControl(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        cacheDir = cacheDir
                    )
                }
            }
        }
    }
}

@Composable
fun ResolutionControl(modifier: Modifier = Modifier, cacheDir: java.io.File) {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    var resolution by remember { mutableStateOf("") }
    var stencilMesh by remember { mutableStateOf("") }
    var ffr by remember { mutableStateOf("") }
    var textureFov by remember { mutableStateOf("") }
    var maxPwrLevel by remember { mutableStateOf("") }
    var minPwrLevel by remember { mutableStateOf("") }
    var cpuMode by remember { mutableStateOf("") }
    var gpuMode by remember { mutableStateOf("") }
    var latencyMode by remember { mutableStateOf("") }
    var cpuSet by remember { mutableStateOf("2") } // Default is Stock (2)
    var status by remember { mutableStateOf("") }
    var currentValues by remember { mutableStateOf(ConfigValues()) }
    var liveStats by remember { mutableStateOf(LiveStats()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storageContext = context.createDeviceProtectedStorageContext()
    val resources = context.resources
    val appUid = android.os.Process.myUid()

    LaunchedEffect(Unit) {
        checkAndEnableXposedScope(context.packageName)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == NavTab.HOME) {
            while (true) {
                liveStats = fetchLiveStats()
                delay(1500L)
            }
        }
    }

    fun refreshValues() {
        scope.launch {
            val (values, error) = fetchCurrentValues(cacheDir, appUid, context)
            currentValues = values
            if (values.resolution != "Unknown" && values.resolution != "N/A") {
                resolution = values.resolution
            }
            if (values.stencilMesh != "Unknown" && values.stencilMesh != "N/A") {
                stencilMesh = values.stencilMesh
            }
            if (values.ffr != "Unknown" && values.ffr != "N/A") {
                ffr = values.ffr
            }
            if (values.textureFov != "Unknown" && values.textureFov != "N/A") {
                textureFov = values.textureFov
            }
            if (values.maxPwrLevel != "Unknown" && values.maxPwrLevel != "N/A") {
                maxPwrLevel = values.maxPwrLevel
            }
            if (values.minPwrLevel != "Unknown" && values.minPwrLevel != "N/A") {
                minPwrLevel = values.minPwrLevel
            }
            if (values.cpuMode != "Unknown" && values.cpuMode != "N/A") {
                cpuMode = values.cpuMode
            }
            if (values.gpuMode != "Unknown" && values.gpuMode != "N/A") {
                gpuMode = values.gpuMode
            }
            if (values.latencyMode != "Unknown" && values.latencyMode != "N/A") {
                latencyMode = values.latencyMode
            }
            if (values.cpuSet != "Unknown" && values.cpuSet != "N/A") {
                cpuSet = values.cpuSet
            }
            if (error.isNotBlank() && status.isBlank()) {
                status = resources.getString(R.string.status_read_error_prefix, error)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshValues()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val applyingText = stringResource(id = R.string.status_applying)
    LaunchedEffect(status) {
        if (status.isNotBlank() && status != applyingText) {
            val isError = status.contains("Error") || status.contains("Failed")
            delay(if (isError) 10000L else 3000L)
            status = ""
        }
    }

    ResolutionControlContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        currentValues = currentValues,
        liveStats = liveStats,
        resolution = resolution,
        stencilMesh = stencilMesh,
        ffr = ffr,
        textureFov = textureFov,
        maxPwrLevel = maxPwrLevel,
        minPwrLevel = minPwrLevel,
        cpuMode = cpuMode,
        gpuMode = gpuMode,
        latencyMode = latencyMode,
        cpuSet = cpuSet,
        status = status,
        onResolutionChange = { resolution = it },
        onStencilMeshChange = { stencilMesh = it },
        onFfrChange = { ffr = it },
        onTextureFovChange = { textureFov = it },
        onMaxPwrLevelChange = { maxPwrLevel = it },
        onMinPwrLevelChange = { minPwrLevel = it },
        onCpuModeChange = { cpuMode = it },
        onGpuModeChange = { gpuMode = it },
        onLatencyModeChange = { latencyMode = it },
        onCpuSetChange = { cpuSet = it },
        onApplyRenderingClick = {
            if (resolution.isNotBlank() || stencilMesh.isNotBlank() || ffr.isNotBlank() || textureFov.isNotBlank()) {
                scope.launch {
                    status = resources.getString(R.string.status_applying)
                    val (success, error) = applySettings(
                        res = resolution,
                        sm = stencilMesh,
                        ffr = ffr,
                        tf = textureFov,
                        maxPwr = maxPwrLevel,
                        minPwr = minPwrLevel,
                        cpuMode = cpuMode,
                        gpuMode = gpuMode,
                        latencyMode = latencyMode,
                        cpuSet = cpuSet,
                        cacheDir = cacheDir,
                        uid = appUid,
                        context = storageContext,
                        databaseChanged = true
                    )
                    if (success) {
                        refreshValues()
                        status = if (error == "Success. Rebooting...") {
                            resources.getString(R.string.status_rebooting)
                        } else {
                            resources.getString(R.string.status_applied_success)
                        }
                    } else {
                        status = if (error == "Verification failed. DB values did not change.") {
                            resources.getString(R.string.status_verify_failed)
                        } else {
                            resources.getString(R.string.status_error_prefix, error)
                        }
                    }
                }
            }
        },
        onApplyPerformanceClick = {
            if (maxPwrLevel.isNotBlank() || minPwrLevel.isNotBlank() || cpuMode.isNotBlank() || gpuMode.isNotBlank() || latencyMode.isNotBlank() || cpuSet.isNotBlank()) {
                scope.launch {
                    status = resources.getString(R.string.status_applying)
                    val (success, error) = applySettings(
                        res = resolution,
                        sm = stencilMesh,
                        ffr = ffr,
                        tf = textureFov,
                        maxPwr = maxPwrLevel,
                        minPwr = minPwrLevel,
                        cpuMode = cpuMode,
                        gpuMode = gpuMode,
                        latencyMode = latencyMode,
                        cpuSet = cpuSet,
                        cacheDir = cacheDir,
                        uid = appUid,
                        context = storageContext,
                        databaseChanged = false
                    )
                    if (success) {
                        refreshValues()
                        status = resources.getString(R.string.status_applied_success)
                    } else {
                        status = resources.getString(R.string.status_error_prefix, error)
                    }
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun ResolutionControlContent(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    currentValues: ConfigValues,
    liveStats: LiveStats,
    resolution: String,
    stencilMesh: String,
    ffr: String,
    textureFov: String,
    maxPwrLevel: String,
    minPwrLevel: String,
    cpuMode: String,
    gpuMode: String,
    latencyMode: String,
    cpuSet: String,
    status: String,
    onResolutionChange: (String) -> Unit,
    onStencilMeshChange: (String) -> Unit,
    onFfrChange: (String) -> Unit,
    onTextureFovChange: (String) -> Unit,
    onMaxPwrLevelChange: (String) -> Unit,
    onMinPwrLevelChange: (String) -> Unit,
    onCpuModeChange: (String) -> Unit,
    onGpuModeChange: (String) -> Unit,
    onLatencyModeChange: (String) -> Unit,
    onCpuSetChange: (String) -> Unit,
    onApplyRenderingClick: () -> Unit,
    onApplyPerformanceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = colorResource(id = R.color.main_bg),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Left Sidebar Navigation
            PicoSidebar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Right Main Content Pane
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = colorResource(id = R.color.content_bg),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = when (selectedTab) {
                            NavTab.HOME -> stringResource(id = R.string.tab_home)
                            NavTab.RENDERING -> stringResource(id = R.string.tab_rendering)
                            NavTab.PERFORMANCE -> stringResource(id = R.string.tab_performance)
                            NavTab.ABOUT -> stringResource(id = R.string.tab_about)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        textAlign = TextAlign.Center
                    )

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .picoScrollbar(scrollState, rightOffset = (-14).dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedTab) {
                            NavTab.HOME -> HomeTabContent(currentValues = currentValues, liveStats = liveStats)
                            NavTab.RENDERING -> RenderingTabContent(
                                resolution = resolution,
                                stencilMesh = stencilMesh,
                                ffr = ffr,
                                textureFov = textureFov,
                                onResolutionChange = onResolutionChange,
                                onStencilMeshChange = onStencilMeshChange,
                                onFfrChange = onFfrChange,
                                onTextureFovChange = onTextureFovChange
                            )

                            NavTab.PERFORMANCE -> PerformanceTabContent(
                                maxPwrLevel = maxPwrLevel,
                                minPwrLevel = minPwrLevel,
                                cpuMode = cpuMode,
                                gpuMode = gpuMode,
                                latencyMode = latencyMode,
                                cpuSet = cpuSet,
                                onMaxPwrLevelChange = onMaxPwrLevelChange,
                                onMinPwrLevelChange = onMinPwrLevelChange,
                                onCpuModeChange = onCpuModeChange,
                                onGpuModeChange = onGpuModeChange,
                                onLatencyModeChange = onLatencyModeChange,
                                onCpuSetChange = onCpuSetChange
                            )

                            NavTab.ABOUT -> AboutTabContent()
                        }
                    }

                    // Pinned Bottom Action Bar for Rendering & Performance Tabs
                    if (selectedTab == NavTab.RENDERING || selectedTab == NavTab.PERFORMANCE) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Status Feedback Box
                            if (status.isNotBlank()) {
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Action Buttons Row (Apply & Reset)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = if (selectedTab == NavTab.RENDERING) onApplyRenderingClick else onApplyPerformanceClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorResource(id = R.color.card_bg)
                                    )
                                ) {
                                    Text(
                                        text = if (selectedTab == NavTab.RENDERING) stringResource(id = R.string.apply_reboot) else stringResource(id = R.string.apply),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (selectedTab == NavTab.RENDERING) {
                                            onResolutionChange("1504")
                                            onStencilMeshChange("1")
                                            onFfrChange("1")
                                            onTextureFovChange("95")
                                        } else {
                                            onMaxPwrLevelChange("0")
                                            onMinPwrLevelChange("6")
                                            onCpuModeChange("2")
                                            onGpuModeChange("2")
                                            onLatencyModeChange("2")
                                            onCpuSetChange("2") // Default Stock
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorResource(id = R.color.card_bg)
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Restore Default",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PicoSidebar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(id = R.string.app_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            NavTabItem(
                title = stringResource(id = R.string.tab_home),
                icon = Icons.Rounded.Home,
                isSelected = selectedTab == NavTab.HOME,
                onClick = { onTabSelected(NavTab.HOME) }
            )
            NavTabItem(
                title = stringResource(id = R.string.tab_rendering),
                icon = Icons.Rounded.DisplaySettings,
                isSelected = selectedTab == NavTab.RENDERING,
                onClick = { onTabSelected(NavTab.RENDERING) }
            )
            NavTabItem(
                title = stringResource(id = R.string.tab_performance),
                icon = Icons.Rounded.Speed,
                isSelected = selectedTab == NavTab.PERFORMANCE,
                onClick = { onTabSelected(NavTab.PERFORMANCE) }
            )
        }

        // About Tab (Gravity to bottom)
        NavTabItem(
            title = stringResource(id = R.string.tab_about),
            icon = Icons.Rounded.Info,
            isSelected = selectedTab == NavTab.ABOUT,
            onClick = { onTabSelected(NavTab.ABOUT) }
        )
    }
}

@Composable
fun NavTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) colorResource(id = R.color.card_bg) else Color.Transparent
    val contentColor = if (isSelected) Color.LightGray else Color.Gray
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = fontWeight
            )
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    helpTitle: String? = null,
    helpMessage: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_bg)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Medium
                )
                if (helpTitle != null && helpMessage != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    HelpButton(title = helpTitle, message = helpMessage)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicoDropdownSelector(
    valueText: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    dropdownWidth: Dp = 240.dp
) {
    var isExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it },
        modifier = modifier.width(dropdownWidth)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
                .height(46.dp)
                .background(
                    color = colorResource(id = R.color.dropdown_menu),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = valueText,
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }

        val scrollState = rememberScrollState()
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            containerColor = colorResource(id = R.color.dropdown_item),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .offset(y = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .picoScrollbar(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(scrollState)
                    .padding(6.dp)
            ) {
                options.forEach { (key, label) ->
                    val isSelected = (label == valueText)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = colorResource(id = R.color.primary),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onOptionSelected(key)
                            isExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTabContent(currentValues: ConfigValues, liveStats: LiveStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // App Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_bg)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(20.dp)
            ) {
                val launcherIcon = rememberLauncherIcon()
                if (launcherIcon != null) {
                    Image(
                        bitmap = launcherIcon,
                        contentDescription = stringResource(id = R.string.app_name),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = colorResource(id = R.color.primary),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.app_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Pico VR 3D Resolution & Performance Utility",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }
        }

        // Description Card (@app_description)
        Card(
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_bg)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(id = R.string.app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    lineHeight = 20.sp
                )
            }
        }

        // Live Hardware Monitor Card
        Card(
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_bg)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Live Hardware Monitor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                GpuLoadCard(liveStats = liveStats)

                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 20.dp))

                // CPU Cores Header
                Text(
                    text = "Core Usage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val sortedCores = liveStats.cpuCores.sortedByDescending { it.coreId } // 7, 6, 5, 4, 3, 2, 1, 0
                val row1 = sortedCores.filter { it.coreId >= 4 } // 7, 6, 5, 4
                val row2 = sortedCores.filter { it.coreId < 4 }  // 3, 2, 1, 0

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        row1.forEach { core ->
                            CoreUsageGauge(core = core)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        row2.forEach { core ->
                            CoreUsageGauge(core = core)
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                PhysicalRamCard(memory = liveStats.memory)

                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                SwapCard(memory = liveStats.memory)
            }
        }

        // Current Active System Config Card
        Card(
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_bg)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(id = R.string.current_system_config),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(64.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusRowItem(label = stringResource(id = R.string.target_resolution), value = "${currentValues.resolution} px")
                        StatusRowItem(
                            label = stringResource(id = R.string.stencil_mesh),
                            value = if (currentValues.stencilMesh == "1") stringResource(id = R.string.enabled) else if (currentValues.stencilMesh == "0") stringResource(id = R.string.disabled) else currentValues.stencilMesh
                        )
                        StatusRowItem(
                            label = stringResource(id = R.string.foveated_rendering),
                            value = if (currentValues.ffr == "1") stringResource(id = R.string.enabled) else if (currentValues.ffr == "-1") stringResource(id = R.string.disabled) else currentValues.ffr
                        )
                        StatusRowItem(
                            label = stringResource(id = R.string.texture_fov),
                            value = if (currentValues.textureFov != "Unknown" && currentValues.textureFov != "N/A") "${currentValues.textureFov}°" else currentValues.textureFov
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusRowItem(label = stringResource(id = R.string.max_pwrlevel), value = currentValues.maxPwrLevel)
                        StatusRowItem(label = stringResource(id = R.string.min_pwrlevel), value = currentValues.minPwrLevel)
                        StatusRowItem(
                            label = stringResource(id = R.string.cpu_mode),
                            value = if (currentValues.cpuMode == "1") stringResource(id = R.string.tune_performance) else if (currentValues.cpuMode == "2") stringResource(id = R.string.tune_stock) else currentValues.cpuMode
                        )
                        StatusRowItem(
                            label = stringResource(id = R.string.gpu_mode),
                            value = if (currentValues.gpuMode == "1") stringResource(id = R.string.tune_performance) else if (currentValues.gpuMode == "2") stringResource(id = R.string.tune_stock) else currentValues.gpuMode
                        )
                        StatusRowItem(label = "Top App Cores", value = currentValues.topAppCpus)
                        StatusRowItem(label = "Foreground App Cores", value = currentValues.foregroundCpus)
                        StatusRowItem(label = "Background App Cores", value = currentValues.backgroundCpus)
                        StatusRowItem(label = "System App Cores", value = currentValues.systemBackgroundCpus)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRowItem(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun GpuLoadCard(liveStats: LiveStats) {
    val cleanUsageStr = liveStats.gpuUsage.replace("%", "").trim()
    val usageNum = cleanUsageStr.toIntOrNull() ?: 0
    val progress = (usageNum.toFloat() / 100f).coerceIn(0f, 1f)
    val picoGreen = colorResource(id = R.color.pico_green)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "GPU Load",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                )
                Text(
                    text = "$cleanUsageStr%",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = liveStats.gpuClock,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Linear Progress Bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = picoGreen,
            trackColor = Color.DarkGray.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun CoreUsageGauge(core: CpuCoreStat) {
    val usageNum = core.usagePercent.removeSuffix("%").toIntOrNull() ?: 0
    val sweepAngle = (usageNum / 100f) * 360f
    val pico_green = colorResource(id = R.color.pico_green)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4.dp.toPx()
                drawArc(
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawArc(
                    color = pico_green,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            Text(
                text = core.usagePercent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "C${core.coreId}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = pico_green
        )
        Text(
            text = core.freqMHz,
            style = MaterialTheme.typography.labelSmall,
            color = Color.LightGray.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PhysicalRamCard(memory: MemoryStats) {
    val picoGreen = colorResource(id = R.color.pico_green)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Physical RAM",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
            Text(
                text = "${memory.ramPercent}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = picoGreen
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { memory.ramPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = picoGreen,
            trackColor = Color.DarkGray.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Used",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
                Text(
                    text = memory.ramUsed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Free",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
                Text(
                    text = memory.ramFree,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
                Text(
                    text = memory.ramTotal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SwapCard(memory: MemoryStats) {
    val picoGreen = colorResource(id = R.color.pico_green)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Swap / ZRAM",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
            Text(
                text = "${memory.swapPercent}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = picoGreen
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { memory.swapPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = picoGreen,
            trackColor = Color.DarkGray.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Used",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
                Text(
                    text = memory.swapUsed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Free",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
                Text(
                    text = memory.swapFree,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
                Text(
                    text = memory.swapTotal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun RenderingTabContent(
    resolution: String,
    stencilMesh: String,
    ffr: String,
    textureFov: String,
    onResolutionChange: (String) -> Unit,
    onStencilMeshChange: (String) -> Unit,
    onFfrChange: (String) -> Unit,
    onTextureFovChange: (String) -> Unit
) {
    val pixelSuffix = stringResource(id = R.string.pixel)
    val enabledText = stringResource(id = R.string.enabled)
    val disabledText = stringResource(id = R.string.disabled)
    val degreeSuffix = stringResource(id = R.string.degree)

    val resOptions = listOf(
        "384" to stringResource(id = R.string.dropdown_value_format, "384", pixelSuffix, stringResource(id = R.string.preset_ultra_performance)),
        "752" to stringResource(id = R.string.dropdown_value_format, "752", pixelSuffix, stringResource(id = R.string.preset_performance)),
        "1504" to stringResource(id = R.string.dropdown_value_format, "1504", pixelSuffix, stringResource(id = R.string.preset_default)),
        "2160" to stringResource(id = R.string.dropdown_value_format, "2160", pixelSuffix, stringResource(id = R.string.preset_native)),
        "2448" to stringResource(id = R.string.dropdown_value_format, "2448", pixelSuffix, stringResource(id = R.string.preset_pico_specific)),
        "2816" to stringResource(id = R.string.dropdown_value_format, "2816", pixelSuffix, stringResource(id = R.string.preset_anti_aliasing))
    )
    val smOptions = listOf("1" to enabledText, "0" to disabledText)
    val ffrOptions = listOf("1" to enabledText, "-1" to disabledText)
    val tfOptions = listOf("95" to "95$degreeSuffix", "85" to "85$degreeSuffix", "75" to "75$degreeSuffix", "65" to "65$degreeSuffix")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Target Resolution
        SettingCard(
            title = stringResource(id = R.string.target_resolution),
            helpTitle = stringResource(id = R.string.target_resolution),
            helpMessage = stringResource(id = R.string.help_resolution_desc)
        ) {
            val selectedText = resOptions.find { it.first == resolution }?.second ?: "$resolution $pixelSuffix"
            PicoDropdownSelector(
                valueText = selectedText,
                options = resOptions,
                onOptionSelected = onResolutionChange,
                dropdownWidth = 240.dp
            )
        }

        // Stencil Mesh
        SettingCard(
            title = stringResource(id = R.string.stencil_mesh),
            helpTitle = stringResource(id = R.string.stencil_mesh),
            helpMessage = stringResource(id = R.string.help_stencil_mesh_desc)
        ) {
            val selectedText = smOptions.find { it.first == stencilMesh }?.second ?: stencilMesh
            PicoDropdownSelector(
                valueText = selectedText,
                options = smOptions,
                onOptionSelected = onStencilMeshChange,
                dropdownWidth = 240.dp
            )
        }

        // Foveated Rendering
        SettingCard(
            title = stringResource(id = R.string.foveated_rendering),
            helpTitle = stringResource(id = R.string.foveated_rendering),
            helpMessage = stringResource(id = R.string.help_ffr_desc)
        ) {
            val selectedText = ffrOptions.find { it.first == ffr }?.second ?: ffr
            PicoDropdownSelector(
                valueText = selectedText,
                options = ffrOptions,
                onOptionSelected = onFfrChange,
                dropdownWidth = 240.dp
            )
        }

        // Texture FOV
        SettingCard(
            title = stringResource(id = R.string.texture_fov),
            helpTitle = stringResource(id = R.string.texture_fov),
            helpMessage = stringResource(id = R.string.help_texture_fov_desc)
        ) {
            val selectedText = tfOptions.find { it.first == textureFov }?.second ?: if (textureFov.isNotBlank()) "$textureFov$degreeSuffix" else ""
            PicoDropdownSelector(
                valueText = selectedText,
                options = tfOptions,
                onOptionSelected = onTextureFovChange,
                dropdownWidth = 240.dp
            )
        }
    }
}

@Composable
fun PerformanceTabContent(
    maxPwrLevel: String,
    minPwrLevel: String,
    cpuMode: String,
    gpuMode: String,
    latencyMode: String,
    cpuSet: String,
    onMaxPwrLevelChange: (String) -> Unit,
    onMinPwrLevelChange: (String) -> Unit,
    onCpuModeChange: (String) -> Unit,
    onGpuModeChange: (String) -> Unit,
    onLatencyModeChange: (String) -> Unit,
    onCpuSetChange: (String) -> Unit
) {
    val pwrOptions = listOf(
        "0" to stringResource(id = R.string.pwr_peak_performance),
        "1" to stringResource(id = R.string.pwr_high_load),
        "2" to stringResource(id = R.string.pwr_standard_load),
        "3" to stringResource(id = R.string.pwr_balanced),
        "4" to stringResource(id = R.string.pwr_low_power),
        "5" to stringResource(id = R.string.pwr_minimum_power),
        "6" to stringResource(id = R.string.pwr_deep_idle)
    )

    val tuneOptions = listOf(
        "1" to stringResource(id = R.string.tune_performance),
        "2" to stringResource(id = R.string.tune_stock)
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Max Power Level
        SettingCard(
            title = stringResource(id = R.string.max_pwrlevel),
            helpTitle = stringResource(id = R.string.max_pwrlevel),
            helpMessage = stringResource(id = R.string.help_pwrlevel_desc)
        ) {
            val selectedText = pwrOptions.find { it.first == maxPwrLevel }?.second ?: maxPwrLevel
            PicoDropdownSelector(
                valueText = selectedText,
                options = pwrOptions,
                onOptionSelected = onMaxPwrLevelChange,
                dropdownWidth = 240.dp
            )
        }

        // Min Power Level
        SettingCard(
            title = stringResource(id = R.string.min_pwrlevel)
        ) {
            val selectedText = pwrOptions.find { it.first == minPwrLevel }?.second ?: minPwrLevel
            PicoDropdownSelector(
                valueText = selectedText,
                options = pwrOptions,
                onOptionSelected = onMinPwrLevelChange,
                dropdownWidth = 240.dp
            )
        }

        // CPU Mode
        SettingCard(
            title = stringResource(id = R.string.cpu_mode),
            helpTitle = stringResource(id = R.string.cpu_mode),
            helpMessage = stringResource(id = R.string.help_cpu_mode_desc)
        ) {
            val selectedText = tuneOptions.find { it.first == cpuMode }?.second ?: cpuMode
            PicoDropdownSelector(
                valueText = selectedText,
                options = tuneOptions,
                onOptionSelected = onCpuModeChange,
                dropdownWidth = 240.dp
            )
        }

        // GPU Mode
        SettingCard(
            title = stringResource(id = R.string.gpu_mode),
            helpTitle = stringResource(id = R.string.gpu_mode),
            helpMessage = stringResource(id = R.string.help_gpu_mode_desc)
        ) {
            val selectedText = tuneOptions.find { it.first == gpuMode }?.second ?: gpuMode
            PicoDropdownSelector(
                valueText = selectedText,
                options = tuneOptions,
                onOptionSelected = onGpuModeChange,
                dropdownWidth = 240.dp
            )
        }

        // Latency Mode
        SettingCard(
            title = stringResource(id = R.string.latency_mode),
            helpTitle = stringResource(id = R.string.latency_mode),
            helpMessage = stringResource(id = R.string.help_latency_mode_desc)
        ) {
            val selectedText = tuneOptions.find { it.first == latencyMode }?.second ?: latencyMode
            PicoDropdownSelector(
                valueText = selectedText,
                options = tuneOptions,
                onOptionSelected = onLatencyModeChange,
                dropdownWidth = 240.dp
            )
        }

        // CPU Set
        SettingCard(
            title = stringResource(id = R.string.cpu_set),
            helpTitle = stringResource(id = R.string.cpu_set),
            helpMessage = stringResource(id = R.string.help_cpu_set_desc)
        ) {
            val selectedText = tuneOptions.find { it.first == cpuSet }?.second ?: cpuSet
            PicoDropdownSelector(
                valueText = selectedText,
                options = tuneOptions,
                onOptionSelected = onCpuSetChange,
                dropdownWidth = 240.dp
            )
        }
    }
}

@Composable
fun AboutTabContent() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val versionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.3.1"
        } catch (_: Exception) {
            "1.3.1"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // App Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_bg)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(20.dp)
            ) {
                val launcherIcon = rememberLauncherIcon()
                if (launcherIcon != null) {
                    Image(
                        bitmap = launcherIcon,
                        contentDescription = stringResource(id = R.string.app_name),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = colorResource(id = R.color.primary),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.app_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }
        }

        // GitHub Repository Link Card (@github_link)
        Card(
            onClick = { uriHandler.openUri("https://github.com/chaixshot/Pico3dResolution") },
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_bg)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Repository",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.github_link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open Link",
                    tint = Color.Gray
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 1200)
@Composable
fun ResolutionControlPreview() {
    Pico3dResolutionTheme {
        ResolutionControlContent(
            selectedTab = NavTab.HOME,
            onTabSelected = {},
            currentValues = ConfigValues("1600", "1", "1", "95", "0", "6", "2", "2", "2", "2", "0-7", "0-1", "0-1", "0-1"),
            liveStats = LiveStats("585 MHz", "45%", listOf(
                CpuCoreStat(0, "1800 MHz", "12%"),
                CpuCoreStat(1, "1800 MHz", "10%"),
                CpuCoreStat(2, "1800 MHz", "15%"),
                CpuCoreStat(3, "1800 MHz", "8%"),
                CpuCoreStat(4, "2400 MHz", "45%"),
                CpuCoreStat(5, "2400 MHz", "40%"),
                CpuCoreStat(6, "2400 MHz", "50%"),
                CpuCoreStat(7, "2800 MHz", "75%")
            )),
            resolution = "2160",
            stencilMesh = "1",
            ffr = "1",
            textureFov = "95",
            maxPwrLevel = "0",
            minPwrLevel = "6",
            cpuMode = "2",
            gpuMode = "2",
            latencyMode = "2",
            cpuSet = "2",
            status = "test",
            onResolutionChange = {},
            onStencilMeshChange = {},
            onFfrChange = {},
            onTextureFovChange = {},
            onMaxPwrLevelChange = {},
            onMinPwrLevelChange = {},
            onCpuModeChange = {},
            onGpuModeChange = {},
            onLatencyModeChange = {},
            onCpuSetChange = {},
            onApplyRenderingClick = {},
            onApplyPerformanceClick = {}
        )
    }
}
