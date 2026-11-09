package com.hamer.res3d

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

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
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            bitmap.asImageBitmap()
        }
    }
}

const val DB_PATH = "/data/user_de/0/com.pvr.configuration/databases/config.db"
const val TEMP_READ_DB = "config_temp.db"
const val TEMP_APPLY_DB = "config_apply.db"

@Composable
fun Modifier.picoScrollbar(
    state: ScrollState,
    rightOffset: Dp = 4.dp
): Modifier {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(state.isScrollInProgress, state.value) {
        if (state.maxValue > 0) {
            alpha.animateTo(0.6f, animationSpec = tween(durationMillis = 150))
            if (!state.isScrollInProgress) {
                delay(1000L)
                alpha.animateTo(0f, animationSpec = tween(durationMillis = 500))
            }
        } else {
            alpha.snapTo(0f)
        }
    }

    return this.drawWithContent {
        drawContent()
        if (state.maxValue > 0 && alpha.value > 0f) {
            val verticalInset = 12.dp.toPx()
            val barWidth = 3.dp.toPx()
            val viewHeight = size.height
            val drawableHeight = viewHeight - (verticalInset * 2)

            val minKnobHeight = 36.dp.toPx()
            val knobHeight = (drawableHeight * (drawableHeight / (drawableHeight + state.maxValue)))
                .coerceIn(minKnobHeight, drawableHeight)

            val scrollProgress = state.value.toFloat() / state.maxValue
            val topOffset = verticalInset + (scrollProgress * (drawableHeight - knobHeight))

            val xPos = size.width - rightOffset.toPx() - barWidth

            drawRoundRect(
                color = Color.White.copy(alpha = alpha.value),
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

data class ConfigValues(
    val resolution: String = "Unknown",
    val stencilMesh: String = "Unknown",
    val ffr: String = "Unknown",
    val textureFov: String = "Unknown",
    val maxPwrLevel: String = "Unknown",
    val minPwrLevel: String = "Unknown",
    val cpuMode: String = "Unknown",
    val gpuMode: String = "Unknown",
    val latencyMode: String = "Unknown"
)

@Composable
fun ResolutionControl(modifier: Modifier = Modifier, cacheDir: File) {
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
    var status by remember { mutableStateOf("") }
    var currentValues by remember { mutableStateOf(ConfigValues()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storageContext = context.createDeviceProtectedStorageContext()
    val resources = context.resources
    val appUid = android.os.Process.myUid()

    LaunchedEffect(Unit) {
        checkAndEnableXposedScope(context.packageName)
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
        resolution = resolution,
        stencilMesh = stencilMesh,
        ffr = ffr,
        textureFov = textureFov,
        maxPwrLevel = maxPwrLevel,
        minPwrLevel = minPwrLevel,
        cpuMode = cpuMode,
        gpuMode = gpuMode,
        latencyMode = latencyMode,
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
                        cacheDir = cacheDir,
                        uid = appUid,
                        context = storageContext,
                        databaseChanged = true
                    )
                    if (success) {
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
            if (maxPwrLevel.isNotBlank() || minPwrLevel.isNotBlank() || cpuMode.isNotBlank() || gpuMode.isNotBlank() || latencyMode.isNotBlank()) {
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
                        cacheDir = cacheDir,
                        uid = appUid,
                        context = storageContext,
                        databaseChanged = false
                    )
                    if (success) {
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
    resolution: String,
    stencilMesh: String,
    ffr: String,
    textureFov: String,
    maxPwrLevel: String,
    minPwrLevel: String,
    cpuMode: String,
    gpuMode: String,
    latencyMode: String,
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
                .padding(16.dp)
        ) {
            // Left Sidebar Navigation
            PicoSidebar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Right Main Content Pane
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = colorResource(id = R.color.content_bg),
                shape = RoundedCornerShape(20.dp)
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
                            NavTab.HOME -> HomeTabContent(currentValues = currentValues)
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
                                onMaxPwrLevelChange = onMaxPwrLevelChange,
                                onMinPwrLevelChange = onMinPwrLevelChange,
                                onCpuModeChange = onCpuModeChange,
                                onGpuModeChange = onGpuModeChange,
                                onLatencyModeChange = onLatencyModeChange
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
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (status.contains("Error") || status.contains("Failed"))
                                            colorResource(id = R.color.pico_red)
                                        else colorResource(id = R.color.pico_green)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = status,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
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
                                        containerColor = colorResource(id = R.color.primary)
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
                color = Color.White,
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
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
fun HomeTabContent(currentValues: ConfigValues) {
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
                        color = Color.LightGray.copy(alpha = 0.7f)
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
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(id = R.string.app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    lineHeight = 20.sp
                )
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
            color = Color.LightGray.copy(alpha = 0.7f)
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
    onMaxPwrLevelChange: (String) -> Unit,
    onMinPwrLevelChange: (String) -> Unit,
    onCpuModeChange: (String) -> Unit,
    onGpuModeChange: (String) -> Unit,
    onLatencyModeChange: (String) -> Unit
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
    }
}

@Composable
fun AboutTabContent() {
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            .size(60.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
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
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.app_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.3.1",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray.copy(alpha = 0.7f)
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
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.github_link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorResource(id = R.color.primary)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Open Link",
                    tint = colorResource(id = R.color.primary)
                )
            }
        }
    }
}

private fun readSysfs(path: String): String {
    val (success, output) = runRootCommandWithOutput("cat $path")
    return if (success) output else "N/A"
}

suspend fun fetchCurrentValues(
    cacheDir: File,
    uid: Int,
    context: Context
): Pair<ConfigValues, String> =
    withContext(Dispatchers.IO) {
        var resVal = "N/A"
        var smVal = "N/A"
        var ffrVal = "N/A"
        var tfVal = "N/A"
        var maxPwr = "N/A"
        var minPwr = "N/A"
        var cpuVal = "N/A"
        var gpuVal = "N/A"
        var latVal = "N/A"
        var errorMsg = ""

        try {
            val apkPath = context.applicationInfo.publicSourceDir
            val cmd =
                "export CLASSPATH=\"$apkPath\" && app_process /system/bin com.hamer.res3d.DbUpdater read \"$DB_PATH\""
            val (success, output) = runRootCommandWithOutput(cmd)

            if (success && output.contains("RES=")) {
                val lines = output.split("\n")
                for (line in lines) {
                    if (line.startsWith("RES=")) resVal = line.substringAfter("RES=")
                    if (line.startsWith("SM=")) smVal = line.substringAfter("SM=")
                    if (line.startsWith("FFR=")) ffrVal = line.substringAfter("FFR=")
                    if (line.startsWith("TF=")) tfVal = line.substringAfter("TF=")
                }
                maxPwr = readSysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel")
                minPwr = readSysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel")

                val gpuGov = readSysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
                gpuVal = if (gpuGov == "performance") "1" else if (gpuGov == "msm-adreno-tz") "2" else "N/A"

                val cpuGov = readSysfs("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor")
                cpuVal = if (cpuGov == "performance") "1" else if (cpuGov == "schedutil") "2" else "N/A"

                val schedLatency = readSysfs("/proc/sys/kernel/sched_latency_ns")
                latVal = if (schedLatency == "5000000") "1" else if (schedLatency == "10000000") "2" else "N/A"

                return@withContext ConfigValues(
                    resVal,
                    smVal,
                    ffrVal,
                    tfVal,
                    maxPwr,
                    minPwr,
                    cpuVal,
                    gpuVal,
                    latVal
                ) to ""
            }
        } catch (e: Exception) {
            // Silently fall through to fallback
        }

        val tempDb = File(cacheDir, TEMP_READ_DB)
        try {
            val (success, error) = runRootCommand(
                "setenforce 0 || true",
                "cp $DB_PATH ${tempDb.absolutePath}",
                "chown $uid:$uid ${tempDb.absolutePath}",
                "chmod 666 ${tempDb.absolutePath}"
            )

            if (success) {
                if (tempDb.exists()) {
                    val db = SQLiteDatabase.openDatabase(
                        tempDb.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )

                    fun queryValue(key: String): String {
                        db.rawQuery(
                            "SELECT LINKAGE_VALUE FROM RuleBean WHERE LINKAGE_KEY LIKE ? LIMIT 1",
                            arrayOf("%$key%")
                        ).use { cursor ->
                            if (cursor.moveToFirst()) {
                                return cursor.getString(0) ?: "null"
                            }
                        }
                        return "N/A"
                    }

                    resVal = queryValue("sdk_eyebuffer")
                    smVal = queryValue("sdk_enableFFRBySYS")
                    ffrVal = queryValue("sdk_stencilMeshStatus")
                    tfVal = queryValue("sdk_EyeTextureFov")

                    db.close()
                    tempDb.delete()
                } else {
                    errorMsg = "Temp file not created"
                }
            } else {
                errorMsg = error
            }

            maxPwr = readSysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel")
            minPwr = readSysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel")

            val gpuGov = readSysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
            gpuVal = if (gpuGov == "performance") "1" else if (gpuGov == "msm-adreno-tz") "2" else "N/A"

            val cpuGov = readSysfs("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor")
            cpuVal = if (cpuGov == "performance") "1" else if (cpuGov == "schedutil") "2" else "N/A"

            val schedLatency = readSysfs("/proc/sys/kernel/sched_latency_ns")
            latVal = if (schedLatency == "5000000") "1" else if (schedLatency == "10000000") "2" else "N/A"

        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
        } finally {
            if (tempDb.exists()) tempDb.delete()
        }

        ConfigValues(resVal, smVal, ffrVal, tfVal, maxPwr, minPwr, cpuVal, gpuVal, latVal) to errorMsg
    }

suspend fun applySettings(
    res: String,
    sm: String,
    ffr: String,
    tf: String,
    maxPwr: String,
    minPwr: String,
    cpuMode: String,
    gpuMode: String,
    latencyMode: String,
    cacheDir: File,
    uid: Int,
    context: Context,
    databaseChanged: Boolean
): Pair<Boolean, String> =
    withContext(Dispatchers.IO) {
        val tempDb = File(cacheDir, TEMP_APPLY_DB)
        var db: SQLiteDatabase? = null

        try {
            val initialRootCmds = mutableListOf<String>("setenforce 0 || true")
            if (maxPwr.isNotBlank())
                initialRootCmds.add("echo $maxPwr > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
            if (minPwr.isNotBlank())
                initialRootCmds.add("echo $minPwr > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")

            if (cpuMode == "1") {
                initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do echo performance > \$p/scaling_governor; done")
                initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do MAX=\$(awk '{print \$NF}' \$p/scaling_available_frequencies) && echo \$MAX > \$p/scaling_max_freq && echo \$MAX > \$p/scaling_min_freq; done")
            } else if (cpuMode == "2") {
                initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do echo schedutil > \$p/scaling_governor; done")
                initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do MIN=\$(cat \$p/cpuinfo_min_freq) && MAX=\$(cat \$p/cpuinfo_max_freq) && echo \$MIN > \$p/scaling_min_freq && echo \$MAX > \$p/scaling_max_freq; done")
            }

            if (gpuMode == "1") {
                initialRootCmds.add("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
                initialRootCmds.add("echo 0 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
                initialRootCmds.add("echo 0 > /sys/class/kgsl/kgsl-3d0/throttling")
            } else if (gpuMode == "2") {
                initialRootCmds.add("echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
                initialRootCmds.add("echo 6 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
                initialRootCmds.add("echo 1 > /sys/class/kgsl/kgsl-3d0/throttling")
            }

            if (latencyMode == "1") {
                initialRootCmds.add("echo 5000000 > /proc/sys/kernel/sched_latency_ns")
                initialRootCmds.add("echo 1000000 > /proc/sys/kernel/sched_min_granularity_ns")
            } else if (latencyMode == "2") {
                initialRootCmds.add("echo 10000000 > /proc/sys/kernel/sched_latency_ns")
                initialRootCmds.add("echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns")
            }

            runRootCommand(*initialRootCmds.toTypedArray())

            val storageContext = context.createDeviceProtectedStorageContext()
            val prefs = storageContext.getSharedPreferences(
                "pwr_prefs",
                Context.MODE_PRIVATE
            )
            prefs.edit().apply {
                putString("max_pwrlevel", maxPwr)
                putString("min_pwrlevel", minPwr)
                commit()
            }

            try {
                val dataDir = storageContext.dataDir
                val prefFile = File(dataDir, "shared_prefs/pwr_prefs.xml")

                if (prefFile.exists()) {
                    prefFile.setReadable(true, false)
                    prefFile.parentFile?.setReadable(true, false)
                    prefFile.parentFile?.setExecutable(true, false)

                    val targetSystemPrefsPath = "/data/system/res3d_pwr_prefs.xml"
                    val relayPath = "/data/local/tmp/res3d_pwr.relay"
                    val relayCmd = "echo $maxPwr:$minPwr:$cpuMode:$gpuMode:$latencyMode > $relayPath"

                    runRootCommand(
                        "chmod 664 ${prefFile.absolutePath}",
                        "chmod 775 ${prefFile.parentFile?.absolutePath}",
                        "cp ${prefFile.absolutePath} $targetSystemPrefsPath",
                        "chmod 666 $targetSystemPrefsPath",
                        "chown 1000:1000 $targetSystemPrefsPath",
                        "chcon u:object_r:system_data_file:s0 $targetSystemPrefsPath",
                        relayCmd,
                        "chmod 666 $relayPath",
                        "chown 1000:1000 $relayPath"
                    )
                }
            } catch (e: Exception) {
                Log.e("Res3D", "Failed to set pref file permissions", e)
            }

            try {
                val serviceIntent = Intent(context, GuardianService::class.java)
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("Res3D", "Failed to start GuardianService", e)
            }

            if (databaseChanged) {
                val hasParamUpdates = listOf(res, sm, ffr, tf).any { it.isNotBlank() }
                var primaryWorked = false

                if (hasParamUpdates) {
                    try {
                        val apkPath = context.applicationInfo.publicSourceDir
                        val cmd =
                            "export CLASSPATH=\"$apkPath\" && app_process /system/bin com.hamer.res3d.DbUpdater write \"$DB_PATH\" '$res' '$sm' '$ffr' '$tf'"
                        val (success, output) = runRootCommandWithOutput(cmd)
                        if (success && output.contains("WRITE_SUCCESS")) {
                            primaryWorked = true
                        }
                    } catch (e: Exception) {
                        // Silently fall through to fallback
                    }

                    if (!primaryWorked) {
                        val (cpSuccess, cpError) = runRootCommand(
                            "cp $DB_PATH ${tempDb.absolutePath}",
                            "chown $uid:$uid ${tempDb.absolutePath}",
                            "chmod 666 ${tempDb.absolutePath}"
                        )
                        if (!cpSuccess) return@withContext false to context.getString(
                            R.string.status_copy_failed,
                            cpError
                        )
                        if (!tempDb.exists()) return@withContext false to context.getString(R.string.status_temp_missing)

                        db = SQLiteDatabase.openDatabase(
                            tempDb.absolutePath,
                            null,
                            SQLiteDatabase.OPEN_READWRITE
                        )

                        db.beginTransaction()
                        try {
                            if (res.isNotBlank()) {
                                db.execSQL(
                                    "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_eyebuffer%'",
                                    arrayOf(res)
                                )
                                db.execSQL(
                                    "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_eyebuffer%'",
                                    arrayOf(res, res)
                                )
                            }
                            if (sm.isNotBlank()) {
                                db.execSQL(
                                    "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_enableFFRBySYS%'",
                                    arrayOf(sm)
                                )
                                db.execSQL(
                                    "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_enableFFRBySYS%'",
                                    arrayOf(sm, sm)
                                )
                            }
                            if (ffr.isNotBlank()) {
                                db.execSQL(
                                    "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_stencilMeshStatus%'",
                                    arrayOf(ffr)
                                )
                                db.execSQL(
                                    "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_stencilMeshStatus%'",
                                    arrayOf(ffr, ffr)
                                )
                            }
                            if (tf.isNotBlank()) {
                                db.execSQL(
                                    "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_EyeTextureFov%'",
                                    arrayOf(tf)
                                )
                                db.execSQL(
                                    "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_EyeTextureFov%'",
                                    arrayOf(tf, tf)
                                )
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                            db.close()
                            db = null
                        }

                        val (writeSuccess, writeError) = runRootCommand(
                            "cat ${tempDb.absolutePath} > $DB_PATH",
                            "sync"
                        )

                        if (!writeSuccess) {
                            return@withContext false to context.getString(
                                R.string.status_write_failed,
                                writeError
                            )
                        }
                    }
                }

                val (verifiedValues, _) = fetchCurrentValues(cacheDir, uid, context)

                val isResVerified = res.isBlank() || verifiedValues.resolution == res
                val isSmVerified = sm.isBlank() || verifiedValues.stencilMesh == sm
                val isFfrVerified = ffr.isBlank() || verifiedValues.ffr == ffr
                val isTfVerified = tf.isBlank() || verifiedValues.textureFov == tf

                if (isResVerified && isSmVerified && isFfrVerified && isTfVerified) {
                    runRootCommand("reboot")
                    return@withContext true to context.getString(R.string.status_rebooting)
                } else {
                    return@withContext false to context.getString(R.string.status_verify_failed)
                }
            } else {
                return@withContext true to context.getString(R.string.status_applied_success)
            }
        } catch (e: Exception) {
            return@withContext false to (e.message
                ?: context.getString(R.string.status_error_prefix, "Unknown"))
        } finally {
            db?.takeIf { it.isOpen }?.close()
            if (tempDb.exists()) tempDb.delete()
        }
    }

private fun runRootCommand(vararg commands: String): Pair<Boolean, String> {
    var process: Process? = null
    var os: DataOutputStream? = null
    var errorReader: BufferedReader? = null
    try {
        process = Runtime.getRuntime().exec("su")
        os = DataOutputStream(process.outputStream)
        for (cmd in commands) {
            os.writeBytes("$cmd\n")
        }
        os.writeBytes("exit\n")
        os.flush()
        val errors = StringBuilder()
        errorReader = BufferedReader(InputStreamReader(process.errorStream))
        var line: String?
        while (errorReader.readLine().also { line = it } != null) {
            errors.append(line).append(" ")
        }
        val exitValue = process.waitFor()
        return (exitValue == 0) to errors.toString().trim()
    } catch (e: Exception) {
        return false to (e.message ?: "su execution failed")
    } finally {
        try {
            os?.close()
        } catch (ignore: Exception) {
        }
        try {
            errorReader?.close()
        } catch (ignore: Exception) {
        }
        process?.destroy()
    }
}

private fun runRootCommandWithOutput(vararg commands: String): Pair<Boolean, String> {
    var process: Process? = null
    var os: DataOutputStream? = null
    var inputReader: BufferedReader? = null
    try {
        process = Runtime.getRuntime().exec("su")
        os = DataOutputStream(process.outputStream)
        for (cmd in commands) {
            os.writeBytes("$cmd\n")
        }
        os.writeBytes("exit\n")
        os.flush()
        val output = StringBuilder()
        inputReader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (inputReader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        val exitValue = process.waitFor()
        return (exitValue == 0) to output.toString().trim()
    } catch (e: Exception) {
        return false to (e.message ?: "su execution failed")
    } finally {
        try {
            os?.close()
        } catch (ignore: Exception) {
        }
        try {
            inputReader?.close()
        } catch (ignore: Exception) {
        }
        process?.destroy()
    }
}

suspend fun checkAndEnableXposedScope(packageName: String) = withContext(Dispatchers.IO) {
    val checkCmd = "/data/adb/lspd/cli scope ls $packageName || /data/adb/modules/zygisk_vector/cli scope ls $packageName"
    val (_, output) = runRootCommandWithOutput(checkCmd)

    if (!output.contains("system_server")) {
        val enableCmd = "/data/adb/lspd/cli modules enable $packageName || /data/adb/modules/zygisk_vector/cli modules enable $packageName"
        val addScopeCmd = "/data/adb/lspd/cli scope add $packageName system_server || /data/adb/modules/zygisk_vector/cli scope add $packageName system_server"
        runRootCommand(enableCmd, addScopeCmd)
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 500)
@Composable
fun ResolutionControlPreview() {
    Pico3dResolutionTheme {
        ResolutionControlContent(
            selectedTab = NavTab.HOME,
            onTabSelected = {},
            currentValues = ConfigValues("1600", "1", "1", "95", "0", "6", "2", "2", "2"),
            resolution = "2160",
            stencilMesh = "1",
            ffr = "1",
            textureFov = "95",
            maxPwrLevel = "0",
            minPwrLevel = "6",
            cpuMode = "2",
            gpuMode = "2",
            latencyMode = "2",
            status = "",
            onResolutionChange = {},
            onStencilMeshChange = {},
            onFfrChange = {},
            onTextureFovChange = {},
            onMaxPwrLevelChange = {},
            onMinPwrLevelChange = {},
            onCpuModeChange = {},
            onGpuModeChange = {},
            onLatencyModeChange = {},
            onApplyRenderingClick = {},
            onApplyPerformanceClick = {}
        )
    }
}
