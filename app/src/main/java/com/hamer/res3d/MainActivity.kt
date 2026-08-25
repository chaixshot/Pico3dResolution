package com.hamer.res3d

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalUriHandler
import com.hamer.res3d.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

const val DB_PATH = "/data/user_de/0/com.pvr.configuration/databases/config.db"
const val TEMP_READ_DB = "config_temp.db"
const val TEMP_APPLY_DB = "config_apply.db"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Pico3dResolutionTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent // Ensure Scaffold doesn't draw a background
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
    val configValue: String = "Unknown",
    val defaultValue: String = "Unknown",
    val linkageValue: String = "Unknown"
)

@Composable
fun ResolutionControl(modifier: Modifier = Modifier, cacheDir: File) {
    var resolution by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var currentValues by remember { mutableStateOf(ConfigValues()) }
    val scope = rememberCoroutineScope()
    
    // Get current app UID for chown
    val appUid = android.os.Process.myUid()

    fun refreshValues() {
        scope.launch {
            val (values, error) = fetchCurrentValues(cacheDir, appUid)
            currentValues = values
            if (values.configValue != "Unknown" && values.configValue != "N/A") {
                resolution = values.configValue
            }
            if (error.isNotBlank() && status.isBlank()) {
                status = "Read Error: $error"
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshValues()
    }

    ResolutionControlContent(
        currentValues = currentValues,
        resolution = resolution,
        status = status,
        onResolutionChange = { resolution = it },
        onApplyClick = {
            if (resolution.isNotBlank()) {
                scope.launch {
                    status = "Applying..."
                    val (success, error) = applyResolution(resolution, cacheDir, appUid)
                    if (success) {
                        status = "Applied successfully!"
                        refreshValues()
                    } else {
                        status = "Error: $error"
                    }
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun ResolutionControlContent(
    currentValues: ConfigValues,
    resolution: String,
    status: String,
    onResolutionChange: (String) -> Unit,
    onApplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colorResource(id = R.color.main_bg),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(48.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pico 3D Resolution",
                style = MaterialTheme.typography.headlineMedium,
                color = colorResource(id = R.color.primary)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = colorResource(id = R.color.content_bg).copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Globally adjust the rendering resolution for all 3D VR applications on your Pico headset. Higher values improve clarity but may impact performance.\n\nThis app works by using root access to modify the system's PVR configuration database (config.db), specifically targeting the 'sdk_eyebuffer' parameters to force a custom resolution. A reboot is required to apply the changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Side: Current Config Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(id = R.color.content_bg)
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Current System Config",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorResource(id = R.color.primary)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                thickness = 1.dp,
                                color = colorResource(id = R.color.card_bg)
                            )
                            Text(
                                "CONFIG_VALUE: ${currentValues.configValue}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                            Text(
                                "DEFAULT_VALUE: ${currentValues.defaultValue}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                            Text(
                                "LINKAGE_VALUE: ${currentValues.linkageValue}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }
                    }

                    // Right Side: Dropdown and Action
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        val options = listOf(
                            "384" to "Ultra Performance",
                            "752" to "Performance",
                            "1504" to "Default",
                            "2160" to "Native Resolution",
                            "2448" to "Pico Specifically",
                            "3240" to "Anti-aliasing"
                        )

                        @OptIn(ExperimentalMaterial3Api::class)
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Target Resolution",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                                )
                                OutlinedTextField(
                                    value = options.find { it.first == resolution }?.let { "${it.first} - ${it.second}" } ?: resolution,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = colorResource(id = R.color.content_bg),
                                        unfocusedContainerColor = colorResource(id = R.color.content_bg),
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                            }

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .background(
                                        color = colorResource(id = R.color.dropdown_bg),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                options.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${option.first} - ${option.second}",
                                                color = Color.White
                                            )
                                        },
                                        onClick = {
                                            onResolutionChange(option.first)
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onApplyClick,
                            modifier = Modifier
                                .width(200.dp)
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(id = R.color.toggle_button)
                            )
                        ) {
                            Text(
                                "Apply & Reboot",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.contains("Error") || status.contains("Failed"))
                            colorResource(id = R.color.pico_red)
                        else colorResource(id = R.color.pico_green)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }

            val uriHandler = LocalUriHandler.current

            Card(
                onClick = { uriHandler.openUri("https://github.com/chaixshot/Pico3dResolution") },
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "GitHub: chaixshot/Pico3dResolution",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

suspend fun fetchCurrentValues(cacheDir: File, uid: Int): Pair<ConfigValues, String> = withContext(Dispatchers.IO) {
    val tempDb = File(cacheDir, TEMP_READ_DB)
    
    var configVal = "N/A"
    var defaultVal = "N/A"
    var linkageVal = "N/A"
    var errorMsg = ""

    try {
        val (success, error) = runRootCommand(
            "setenforce 0 || true", 
            "cp $DB_PATH ${tempDb.absolutePath}", 
            "chown $uid:$uid ${tempDb.absolutePath}",
            "chmod 666 ${tempDb.absolutePath}"
        )
        
        if (success) {
            if (tempDb.exists()) {
                val db = SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                db.rawQuery("SELECT CONFIG_VALUE, DEFAULT_CONFIG_VALUE FROM ConfigBean WHERE CONFIG_NAME LIKE '%sdk_eyebuffer%' LIMIT 1", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        configVal = cursor.getString(0) ?: "null"
                        defaultVal = cursor.getString(1) ?: "null"
                    }
                }
                db.rawQuery("SELECT LINKAGE_VALUE FROM RuleBean WHERE LINKAGE_KEY LIKE '%sdk_eyebuffer%' LIMIT 1", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        linkageVal = cursor.getString(0) ?: "null"
                    }
                }
                db.close()
                tempDb.delete()
            } else {
                errorMsg = "Temp file not created"
            }
        } else {
            errorMsg = error
        }
    } catch (e: Exception) {
        errorMsg = e.message ?: "Unknown error"
    }
    
    ConfigValues(configVal, defaultVal, linkageVal) to errorMsg
}

suspend fun applyResolution(res: String, cacheDir: File, uid: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val tempDb = File(cacheDir, TEMP_APPLY_DB)

    try {
        runRootCommand("setenforce 0 || true")

        val (cpSuccess, cpError) = runRootCommand(
            "cp $DB_PATH ${tempDb.absolutePath}",
            "chown $uid:$uid ${tempDb.absolutePath}",
            "chmod 666 ${tempDb.absolutePath}"
        )
        if (!cpSuccess) return@withContext false to "Copy failed: $cpError"

        if (!tempDb.exists()) return@withContext false to "Temp file missing"

        val db = SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_eyebuffer%'", arrayOf(res, res))
        db.execSQL("UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_eyebuffer%'", arrayOf(res))
        db.close()
        
        // 3. Write back and verify changes before rebooting
        android.util.Log.d("Res3D", "Step 3: Writing back and verifying changes...")
        val (writeSuccess, writeError) = runRootCommand(
            "cat ${tempDb.absolutePath} > $DB_PATH",
            "sync"
        )
        
        if (tempDb.exists()) tempDb.delete()

        if (!writeSuccess) {
            return@withContext false to "Write back failed: $writeError"
        }

        // Call fetchCurrentValues to verify the actual state of the system DB
        val (verifiedValues, fetchError) = fetchCurrentValues(cacheDir, uid)
        
        val isVerified = verifiedValues.configValue == res && 
                         verifiedValues.linkageValue == res

        if (isVerified) {
            android.util.Log.d("Res3D", "Verification success. Rebooting...")
            runRootCommand("reboot")
            return@withContext true to "Success. Rebooting..."
        } else {
            val details = "Got C:${verifiedValues.configValue}, L:${verifiedValues.linkageValue}"
            android.util.Log.e("Res3D", "Verification failed. Expected $res but $details. Error: $fetchError")
            return@withContext false to "Verification failed. DB values did not change."
        }
    } catch (e: Exception) {
        if (tempDb.exists()) tempDb.delete()
        return@withContext false to (e.message ?: "Unknown exception")
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
        val errorMsg = errors.toString().trim()
        
        return (exitValue == 0) to errorMsg
    } catch (e: Exception) {
        return false to (e.message ?: "su execution failed")
    } finally {
        try { os?.close() } catch (ignore: Exception) {}
        try { errorReader?.close() } catch (ignore: Exception) {}
        process?.destroy()
    }
}

@Preview(showBackground = true)
@Composable
fun ResolutionControlPreview() {
    Pico3dResolutionTheme {
        ResolutionControlContent(
            currentValues = ConfigValues(
                configValue = "1600",
                defaultValue = "1600",
                linkageValue = "1600"
            ),
            resolution = "2160",
            status = "Ready",
            onResolutionChange = {},
            onApplyClick = {}
        )
    }
}
