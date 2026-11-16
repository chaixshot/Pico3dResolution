package com.hamer.res3d

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.View
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

const val targetSystemPrefsPath = "/data/system/res3d_pwr_prefs.xml"
const val CONFIG_PATH = "/data/local/tmp/config.cfg"
const val DB_PATH = "/data/user_de/0/com.pvr.configuration/databases/config.db"
const val TEMP_APPLY_DB = "config_apply.db"

data class ConfigValues(
    val resolution: String = "1504",
    val stencilMesh: String = "1",
    val ffr: String = "1",
    val textureFov: String = "95",
    val maxPwrLevel: String = "0",
    val minPwrLevel: String = "6",
    val cpuMode: String = "2",
    val gpuMode: String = "2",
    val latencyMode: String = "2",
    val cpuSet: String = "3",
)

data class CpuCoreStat(
    val coreId: Int,
    val freqMHz: String,
    val usagePercent: String,
    val tempC: String = "N/A"
)

data class MemoryStats(
    val ramPercent: Int = 0,
    val ramUsed: String = "0 GB",
    val ramFree: String = "0 GB",
    val ramTotal: String = "0 GB",
    val swapPercent: Int = 0,
    val swapUsed: String = "0 GB",
    val swapFree: String = "0 GB",
    val swapTotal: String = "0 GB"
)

data class LiveStats(
    val gpuClock: String = "N/A",
    val gpuUsage: String = "N/A",
    val gpuTemp: String = "N/A",
    val cpuCores: List<CpuCoreStat> = emptyList(),
    val memory: MemoryStats = MemoryStats(),
    val maxPwrLevel: String = "N/A",
    val minPwrLevel: String = "N/A",
    val cpuMode: String = "N/A",
    val gpuMode: String = "N/A",
    val topAppCpus: String = "N/A",
    val immersiveAppCpus: String = "N/A",
    val vrForegroundCpus: String = "N/A",
    val compositorCpus: String = "N/A",
    val audioAppCpus: String = "N/A",
    val quickCameraCpus: String = "N/A",
    val backgroundCpus: String = "N/A",
    val systemBackgroundCpus: String = "N/A",
    val picoSystemCpus: String = "N/A",
    val cameraDaemonCpus: String = "N/A",
    val dex2oatCpus: String = "N/A",
    val trackingCpus: String = "N/A",
    val foregroundCpus: String = "N/A",
    val bg3rdAppCpus: String = "N/A",
    val appInshellCpus: String = "N/A",
    val shellAppCpus: String = "N/A",
    val immersiveAppProcs: String = "N/A",
    val codecProcs: String = "N/A",
    val codecCpus: String = "N/A"
)

data class CpuTick(val total: Long, val idle: Long)

fun readSysfs(path: String): String {
    try {
        val file = File(path)
        if (file.exists()) {
            return file.readText().trim()
        }
    } catch (_: Exception) {}
    return "N/A"
}

fun parseSysfsTemp(num: Long): Double {
    return when {
        num in 20000..100000 -> num / 1000.0
        num > 200000 -> (num / 1000.0) - 273.15
        num in 200..600 -> num / 10.0
        num > 1000 -> num / 100.0
        num > 100 -> num / 10.0
        else -> num.toDouble()
    }
}

fun readCpuTicks(): Map<Int, CpuTick> {
    val map = mutableMapOf<Int, CpuTick>()
    try {
        val file = File("/proc/stat")
        if (file.exists()) {
            file.readLines().forEach { line ->
                if (line.startsWith("cpu") && line.length > 3 && line[3].isDigit()) {
                    val parts = line.split("\\s+".toRegex())
                    val coreId = parts[0].removePrefix("cpu").toIntOrNull()
                    if (coreId != null && parts.size >= 5) {
                        val user = parts[1].toLongOrNull() ?: 0L
                        val nice = parts[2].toLongOrNull() ?: 0L
                        val system = parts[3].toLongOrNull() ?: 0L
                        val idle = parts[4].toLongOrNull() ?: 0L
                        val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                        val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                        val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L

                        val active = user + nice + system + irq + softirq
                        val total = active + idle + iowait
                        map[coreId] = CpuTick(total, idle)
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Res3D", "Error reading cpu ticks", e)
    }
    return map
}

fun fetchMemoryStats(): MemoryStats {
    var memTotalKb = 0L
    var memFreeKb = 0L
    var memAvailableKb = 0L
    var swapTotalKb = 0L
    var swapFreeKb = 0L

    try {
        val file = File("/proc/meminfo")
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.split(":\\s+".toRegex())
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSuffix(" kB").toLongOrNull() ?: 0L
                    when (key) {
                        "MemTotal" -> memTotalKb = value
                        "MemFree" -> memFreeKb = value
                        "MemAvailable" -> memAvailableKb = value
                        "SwapTotal" -> swapTotalKb = value
                        "SwapFree" -> swapFreeKb = value
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Res3D", "Error reading meminfo", e)
    }

    val ramTotalGB = memTotalKb / (1024.0 * 1024.0)
    val ramAvailable = if (memAvailableKb > 0) memAvailableKb else memFreeKb
    val ramUsedGB = (memTotalKb - ramAvailable).coerceAtLeast(0L) / (1024.0 * 1024.0)
    val ramFreeGB = ramAvailable / (1024.0 * 1024.0)
    val ramPercent = if (ramTotalGB > 0) ((ramUsedGB / ramTotalGB) * 100).toInt().coerceIn(0, 100) else 0

    val swapTotalGB = swapTotalKb / (1024.0 * 1024.0)
    val swapFreeGB = swapFreeKb / (1024.0 * 1024.0)
    val swapUsedGB = (swapTotalKb - swapFreeKb).coerceAtLeast(0L) / (1024.0 * 1024.0)
    val swapPercent = if (swapTotalGB > 0) ((swapUsedGB / swapTotalGB) * 100).toInt().coerceIn(0, 100) else 0

    return MemoryStats(
        ramPercent = ramPercent,
        ramUsed = String.format(java.util.Locale.US, "%.1f GB", ramUsedGB),
        ramFree = String.format(java.util.Locale.US, "%.1f GB", ramFreeGB),
        ramTotal = String.format(java.util.Locale.US, "%.1f GB", ramTotalGB),
        swapPercent = swapPercent,
        swapUsed = String.format(java.util.Locale.US, "%.1f GB", swapUsedGB),
        swapFree = String.format(java.util.Locale.US, "%.1f GB", swapFreeGB),
        swapTotal = String.format(java.util.Locale.US, "%.1f GB", swapTotalGB)
    )
}

fun fetchThermalDoubleMap(): Map<String, Double> {
    val map = mutableMapOf<String, Double>()
    try {
        val thermalDir = File("/sys/class/thermal")
        if (thermalDir.exists() && thermalDir.isDirectory) {
            thermalDir.listFiles()?.forEach { zoneDir ->
                if (zoneDir.name.startsWith("thermal_zone")) {
                    val type = readSysfs("${zoneDir.absolutePath}/type").lowercase().trim()
                    val tempStr = readSysfs("${zoneDir.absolutePath}/temp")
                    if (type.isNotBlank() && tempStr != "N/A") {
                        val num = tempStr.toLongOrNull()
                        if (num != null) {
                            val temp = parseSysfsTemp(num)
                            if (temp in 20.0..115.0) {
                                map[type] = temp
                            }
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return map
}

suspend fun fetchLiveStats(): LiveStats = withContext(Dispatchers.IO) {
    var gpuFreq = "N/A"
    var gpuUsage = "N/A"
    val cpuCores = mutableListOf<CpuCoreStat>()

    val thermalMap = fetchThermalDoubleMap()

    val gpuZoneKey = thermalMap.keys.find { it.contains("gpu") && it.contains("usr") } 
        ?: thermalMap.keys.find { it.contains("gpu") || it.contains("kgsl") || it.contains("gpuss") }
    val gpuTempCelsius = if (gpuZoneKey != null) {
        thermalMap[gpuZoneKey]!!
    } else {
        thermalMap["cpuss-0-usr"] ?: thermalMap["soc"] ?: thermalMap.values.maxOrNull() ?: 72.0
    }
    val gpuTemp = String.format(java.util.Locale.US, "%.0f°C", gpuTempCelsius)

    val usrCpuZones = thermalMap.filter { it.key.contains("cpu") && it.key.contains("usr") }
    val defaultCpuBase = if (usrCpuZones.isNotEmpty()) usrCpuZones.values.average() else 72.0

    try {
        val freqOut = readSysfs("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
        if (freqOut != "N/A" && freqOut.toLongOrNull() != null) {
            gpuFreq = "${freqOut.toLong() / 1000000} MHz"
        } else {
            val altFreq = readSysfs("/sys/class/kgsl/kgsl-3d0/gpuclk")
            if (altFreq != "N/A" && altFreq.toLongOrNull() != null) {
                gpuFreq = "${altFreq.toLong() / 1000000} MHz"
            }
        }

        val busyOut = readSysfs("/sys/class/kgsl/kgsl-3d0/devfreq/busy_percentage")
        if (busyOut != "N/A") {
            gpuUsage = "$busyOut%"
        } else {
            val altBusy = readSysfs("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
            if (altBusy != "N/A") gpuUsage = "$altBusy%"
        }

        val sample1 = readCpuTicks()
        delay(200L)
        val sample2 = readCpuTicks()

        for (i in 0..7) {
            val cpuFreqPath = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
            val freqVal = readSysfs(cpuFreqPath)
            val freqMHz = if (freqVal != "N/A" && freqVal.toLongOrNull() != null) {
                "${freqVal.toLong() / 1000} MHz"
            } else "Offline"

            val t1 = sample1[i]
            val t2 = sample2[i]
            val usage = if (t1 != null && t2 != null) {
                val totalDiff = t2.total - t1.total
                val idleDiff = t2.idle - t1.idle
                if (totalDiff > 0) {
                    val util = ((totalDiff - idleDiff) * 100) / totalDiff
                    "${util.coerceIn(0, 100)}%"
                } else "--%"
            } else "--%"

            val coreZoneKey = if (i < 4) {
                "cpu-0-$i-usr"
            } else {
                "cpu-1-${i - 4}-usr"
            }

            val directCoreTemp = thermalMap[coreZoneKey] 
                ?: thermalMap.keys.find { it.contains("cpu") && it.contains("$i") && it.contains("usr") }?.let { thermalMap[it] }

            val coreTempVal = if (directCoreTemp != null) {
                directCoreTemp
            } else {
                val usageFraction = (usage.removeSuffix("%").toIntOrNull() ?: 0) / 100f
                val freqInt = freqMHz.removeSuffix(" MHz").toIntOrNull() ?: 1800
                val loadOffset = (usageFraction * 3.0) + (if (freqInt > 2200) 2.0 else 0.0)
                defaultCpuBase + loadOffset
            }

            val coreTemp = String.format(java.util.Locale.US, "%.0f°C", coreTempVal)
            cpuCores.add(CpuCoreStat(i, freqMHz, usage, coreTemp))
        }
    } catch (e: Exception) {
        Log.e("Res3D", "Error fetching live stats", e)
    }

    val memoryStats = fetchMemoryStats()

    val defaults = ConfigValues()

    val maxPwrSysfs = readSysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel")
    val maxPwrVal = if (maxPwrSysfs != "N/A" && maxPwrSysfs.isNotBlank()) maxPwrSysfs.trim() else defaults.maxPwrLevel

    val minPwrSysfs = readSysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel")
    val minPwrVal = if (minPwrSysfs != "N/A" && minPwrSysfs.isNotBlank()) minPwrSysfs.trim() else defaults.minPwrLevel

    val cpuGov = readSysfs("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor")
    val cpuModeVal = if (cpuGov.contains("performance", ignoreCase = true)) "1" else if (cpuGov != "N/A" && cpuGov.isNotBlank()) "2" else defaults.cpuMode

    val gpuGov = readSysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
    val gpuModeVal = if (gpuGov.contains("performance", ignoreCase = true)) "1" else if (gpuGov != "N/A" && gpuGov.isNotBlank()) "2" else defaults.gpuMode

    val topAppCpusVal = readSysfs("/dev/cpuset/top-app/cpus")
    val immersiveAppCpusVal = readSysfs("/dev/cpuset/immersive-app/cpus")
    val codecCpusVal = readSysfs("/dev/cpuset/codec/cpus")
    val vrForegroundCpusVal = readSysfs("/dev/cpuset/vrforeground/cpus")
    val compositorCpusVal = readSysfs("/dev/cpuset/compositor/cpus")
    val audioAppCpusVal = readSysfs("/dev/cpuset/audio-app/cpus")
    val picoSystemCpusVal = readSysfs("/dev/cpuset/pico-system/cpus")
    val cameraDaemonCpusVal = readSysfs("/dev/cpuset/camera-daemon/cpus")
    val dex2oatCpusVal = readSysfs("/dev/cpuset/dex2oat/cpus")
    val trackingCpusVal = readSysfs("/dev/cpuset/tracking/cpus")
    val foregroundCpusVal = readSysfs("/dev/cpuset/foreground/cpus")
    val bg3rdAppCpusVal = readSysfs("/dev/cpuset/bg_3rd_app/cpus")
    val appInshellCpusVal = readSysfs("/dev/cpuset/app-inshell/cpus")
    val shellAppCpusVal = readSysfs("/dev/cpuset/shell-app/cpus")
    val quickCameraCpusVal = readSysfs("/dev/cpuset/quickcamera/cpus")
    val backgroundCpusVal = readSysfs("/dev/cpuset/background/cpus")
    val systemBackgroundCpusVal = readSysfs("/dev/cpuset/system-background/cpus")

    val immersiveAppProcsVal = run {
        try {
            val (success, output) = runRootCommand("for pid in \$(cat /dev/cpuset/immersive-app/cgroup.procs 2>/dev/null); do if [ -d /proc/\$pid ]; then echo \"\$pid: \$(cat /proc/\$pid/cmdline 2>/dev/null | tr '\\000' ' ')\"; fi; done")
            if (success && output.isNotBlank()) {
                val lines = output.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                val entries = mutableListOf<String>()
                for (line in lines) {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx != -1) {
                        val pidStr = line.substring(0, colonIdx).trim()
                        val cmdline = line.substring(colonIdx + 1).trim()
                        val name = if (cmdline.isNotBlank()) {
                            val parts = cmdline.split(Regex("\\s+"))
                            val firstArg = parts.firstOrNull()
                            val resolved = if (!firstArg.isNullOrBlank() && !firstArg.contains("app_process") && !firstArg.contains("zygote")) {
                                firstArg.substringAfterLast('/')
                            } else {
                                val pkgPart = parts.firstOrNull { it.contains(".") && !it.startsWith("/") && !it.contains("app_process") }
                                pkgPart?.substringAfterLast('/') ?: pidStr
                            }
                            if (resolved.isNotBlank() && resolved != "app_process") resolved else pidStr
                        } else {
                            pidStr
                        }
                        val shortName = if (name.length > 28) {
                            name.take(25) + "..."
                        } else {
                            name
                        }
                        val entry = "$shortName ($pidStr)"
                        if (!entries.contains(entry)) entries.add(entry)
                    }
                }
                if (entries.isNotEmpty()) entries.joinToString("\n") else "None"
            } else {
                "None"
            }
        } catch (_: Exception) {
            "N/A"
        }
    }

    val codecProcsVal = run {
        try {
            val (success, output) = runRootCommand("for pid in \$(cat /dev/cpuset/codec/cgroup.procs 2>/dev/null); do if [ -d /proc/\$pid ]; then echo \"\$pid: \$(cat /proc/\$pid/cmdline 2>/dev/null | tr '\\000' ' ')\"; fi; done")
            if (success && output.isNotBlank()) {
                val lines = output.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                val entries = mutableListOf<String>()
                for (line in lines) {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx != -1) {
                        val pidStr = line.substring(0, colonIdx).trim()
                        val cmdline = line.substring(colonIdx + 1).trim()
                        val name = if (cmdline.isNotBlank()) {
                            val parts = cmdline.split(Regex("\\s+"))
                            val firstArg = parts.firstOrNull()
                            val resolved = if (!firstArg.isNullOrBlank() && !firstArg.contains("app_process") && !firstArg.contains("zygote")) {
                                firstArg.substringAfterLast('/')
                            } else {
                                val pkgPart = parts.firstOrNull { it.contains(".") && !it.startsWith("/") && !it.contains("app_process") }
                                pkgPart?.substringAfterLast('/') ?: pidStr
                            }
                            if (resolved.isNotBlank() && resolved != "app_process") resolved else pidStr
                        } else {
                            pidStr
                        }
                        val shortName = if (name.length > 28) {
                            name.take(25) + "..."
                        } else {
                            name
                        }
                        val entry = "$shortName ($pidStr)"
                        if (!entries.contains(entry)) entries.add(entry)
                    }
                }
                if (entries.isNotEmpty()) entries.joinToString("\n") else "None"
            } else {
                "None"
            }
        } catch (_: Exception) {
            "N/A"
        }
    }

    LiveStats(
        gpuClock = gpuFreq,
        gpuUsage = gpuUsage,
        gpuTemp = gpuTemp,
        cpuCores = cpuCores,
        memory = memoryStats,
        maxPwrLevel = maxPwrVal,
        minPwrLevel = minPwrVal,
        cpuMode = cpuModeVal,
        gpuMode = gpuModeVal,
        topAppCpus = topAppCpusVal,
        immersiveAppCpus = immersiveAppCpusVal,
        vrForegroundCpus = vrForegroundCpusVal,
        compositorCpus = compositorCpusVal,
        audioAppCpus = audioAppCpusVal,
        picoSystemCpus = picoSystemCpusVal,
        cameraDaemonCpus = cameraDaemonCpusVal,
        dex2oatCpus = dex2oatCpusVal,
        trackingCpus = trackingCpusVal,
        foregroundCpus = foregroundCpusVal,
        bg3rdAppCpus = bg3rdAppCpusVal,
        appInshellCpus = appInshellCpusVal,
        shellAppCpus = shellAppCpusVal,
        quickCameraCpus = quickCameraCpusVal,
        backgroundCpus = backgroundCpusVal,
        systemBackgroundCpus = systemBackgroundCpusVal,
        immersiveAppProcs = immersiveAppProcsVal,
        codecProcs = codecProcsVal,
        codecCpus = codecCpusVal
    )
}

@Suppress("UNUSED_PARAMETER")
suspend fun fetchConfigValues(
    cacheDir: File,
    uid: Int,
    context: Context
): Pair<ConfigValues, String> =
    withContext(Dispatchers.IO) {
        getConfigs() to ""
    }

fun updateConfigFile(
    maxPwr: String? = null,
    minPwr: String? = null,
    cpuMode: String? = null,
    gpuMode: String? = null,
    latencyMode: String? = null,
    cpuSet: String? = null,
    res: String? = null,
    sm: String? = null,
    ffr: String? = null,
    tf: String? = null
) {
    try {
        val defaults = ConfigValues()
        var curMaxPwr = defaults.maxPwrLevel
        var curMinPwr = defaults.minPwrLevel
        var curCpuMode = defaults.cpuMode
        var curGpuMode = defaults.gpuMode
        var curLatencyMode = defaults.latencyMode
        var curCpuSet = defaults.cpuSet
        var curRes = defaults.resolution
        var curSm = defaults.stencilMesh
        var curFfr = defaults.ffr
        var curTf = defaults.textureFov

        val configFile = File(CONFIG_PATH)
        if (configFile.exists()) {
            val parts = configFile.readText().trim().split(":")
            curMaxPwr = parts.getOrNull(0)?.trim() ?: curMaxPwr
            curMinPwr = parts.getOrNull(1)?.trim() ?: curMinPwr
            curCpuMode = parts.getOrNull(2)?.trim() ?: curCpuMode
            curGpuMode = parts.getOrNull(3)?.trim() ?: curGpuMode
            curLatencyMode = parts.getOrNull(4)?.trim() ?: curLatencyMode
            curCpuSet = parts.getOrNull(5)?.trim() ?: curCpuSet
            curRes = parts.getOrNull(6)?.trim() ?: curRes
            curSm = parts.getOrNull(7)?.trim() ?: curSm
            curFfr = parts.getOrNull(8)?.trim() ?: curFfr
            curTf = parts.getOrNull(9)?.trim() ?: curTf
        }

        val finalMaxPwr = if (!maxPwr.isNullOrBlank()) maxPwr else curMaxPwr
        val finalMinPwr = if (!minPwr.isNullOrBlank()) minPwr else curMinPwr
        val finalCpuMode = if (!cpuMode.isNullOrBlank()) cpuMode else curCpuMode
        val finalGpuMode = if (!gpuMode.isNullOrBlank()) gpuMode else curGpuMode
        val finalLatencyMode = if (!latencyMode.isNullOrBlank()) latencyMode else curLatencyMode
        val finalCpuSet = if (!cpuSet.isNullOrBlank()) cpuSet else curCpuSet
        val finalRes = if (!res.isNullOrBlank()) res else curRes
        val finalSm = if (!sm.isNullOrBlank()) sm else curSm
        val finalFfr = if (!ffr.isNullOrBlank()) ffr else curFfr
        val finalTf = if (!tf.isNullOrBlank()) tf else curTf

        val configCmd = "echo $finalMaxPwr:$finalMinPwr:$finalCpuMode:$finalGpuMode:$finalLatencyMode:$finalCpuSet:$finalRes:$finalSm:$finalFfr:$finalTf > $CONFIG_PATH"
        runRootCommand(
            configCmd,
            "chmod 666 $CONFIG_PATH",
            "chown 1000:1000 $CONFIG_PATH"
        )
    } catch (e: Exception) {
        Log.e("[Res3D]", "Failed to update config file", e)
    }
}

suspend fun applyPerformanceSettings(
    maxPwr: String,
    minPwr: String,
    cpuMode: String,
    gpuMode: String,
    latencyMode: String,
    cpuSet: String,
    context: Context
) = withContext(Dispatchers.IO) {
    // Save config file
    updateConfigFile(maxPwr = maxPwr, minPwr = minPwr, cpuMode = cpuMode, gpuMode = gpuMode, latencyMode = latencyMode, cpuSet = cpuSet)

    // Start GuardianService => GuardianDaemon
    try {
        val serviceIntent = Intent(context, GuardianService::class.java)
        context.startService(serviceIntent)
    } catch (e: Exception) {
        Log.e("[Res3D]", "Failed to start GuardianService", e)
    }

    // Apply system config
    try {
        val initialRootCmds = mutableListOf<String>("setenforce 0 || true")
        if (maxPwr.isNotBlank())
            initialRootCmds.add("echo $maxPwr > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
        if (minPwr.isNotBlank())
            initialRootCmds.add("echo $minPwr > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")

        if (cpuMode == "1") {
            initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do echo performance > \$p/scaling_governor; done")
            initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do MAX=\$(awk '{print \$NF}' \$p/scaling_available_frequencies) && echo \$MAX > \$p/scaling_max_freq && echo \$MAX > \$p/scaling_min_freq; done")
        } else if (cpuMode == "2") { // Stock
            initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do echo schedutil > \$p/scaling_governor; done")
            initialRootCmds.add("for p in /sys/devices/system/cpu/cpufreq/policy*; do MIN=\$(cat \$p/cpuinfo_min_freq) && MAX=\$(cat \$p/cpuinfo_max_freq) && echo \$MIN > \$p/scaling_min_freq && echo \$MAX > \$p/scaling_max_freq; done")
        }

        if (gpuMode == "1") {
            initialRootCmds.add("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
            initialRootCmds.add("echo 0 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
            initialRootCmds.add("echo 0 > /sys/class/kgsl/kgsl-3d0/throttling")
        } else if (gpuMode == "2") { // Stock
            initialRootCmds.add("echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
            initialRootCmds.add("echo 6 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
            initialRootCmds.add("echo 1 > /sys/class/kgsl/kgsl-3d0/throttling")
        }

        if (latencyMode == "1") {
            initialRootCmds.add("echo 5000000 > /proc/sys/kernel/sched_latency_ns")
            initialRootCmds.add("echo 1000000 > /proc/sys/kernel/sched_min_granularity_ns")
        } else if (latencyMode == "2") { // Stock
            initialRootCmds.add("echo 10000000 > /proc/sys/kernel/sched_latency_ns")
            initialRootCmds.add("echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns")
        }

        if (cpuSet == "3") { // Stock
            initialRootCmds.add("echo 4-6 > /dev/cpuset/top-app/cpus || true")
            initialRootCmds.add("echo 4-5 > /dev/cpuset/vrforeground/cpus || true")

            initialRootCmds.add("echo 0-3,7 > /dev/cpuset/compositor/cpus || true")
            initialRootCmds.add("echo 0-6 > /dev/cpuset/pico-system/cpus || true")
            initialRootCmds.add("echo 0-3 > /dev/cpuset/audio-app/cpus || true")
            initialRootCmds.add("echo 0-3 > /dev/cpuset/camera-daemon/cpus || true")
            initialRootCmds.add("echo 0-3,7 > /dev/cpuset/dex2oat/cpus || true")
            initialRootCmds.add("echo 0-3,7 > /dev/cpuset/tracking/cpus || true")
            initialRootCmds.add("echo 0-3 > /dev/cpuset/quickcamera/cpus || true")

            initialRootCmds.add("echo 0-3 > /dev/cpuset/foreground/cpus || true")
            initialRootCmds.add("echo 0-2 > /dev/cpuset/bg_3rd_app/cpus || true")
            initialRootCmds.add("echo 0-1,4 > /dev/cpuset/app-inshell/cpus || true")
            initialRootCmds.add("echo 5-6 > /dev/cpuset/shell-app/cpus || true")
            initialRootCmds.add("echo 0-1 > /dev/cpuset/background/cpus || true")
            initialRootCmds.add("echo 0-1 > /dev/cpuset/system-background/cpus || true")
            initialRootCmds.add("echo 7 > /dev/cpuset/cluster-super/cpus || true")
            initialRootCmds.add("echo 0-7 > /dev/cpuset/restricted/cpus || true")
        }

        runRootCommand(*initialRootCmds.toTypedArray())
    } catch (e: Exception) {}

    // Apply system power level
    val storageContext = context.createDeviceProtectedStorageContext()
    try {
        val prefs = storageContext.getSharedPreferences(
            "pwr_prefs",
            Context.MODE_PRIVATE
        )
        prefs.edit().apply {
            putString("max_pwrlevel", maxPwr)
            putString("min_pwrlevel", minPwr)
            commit()
        }

        val dataDir = storageContext.dataDir
        val prefFile = File(dataDir, "shared_prefs/pwr_prefs.xml")

        if (prefFile.exists()) {
            prefFile.setReadable(true, false)
            prefFile.parentFile?.setReadable(true, false)
            prefFile.parentFile?.setExecutable(true, false)

            runRootCommand(
                "chmod 664 ${prefFile.absolutePath}",
                "chmod 775 ${prefFile.parentFile?.absolutePath}",
                "cp ${prefFile.absolutePath} $targetSystemPrefsPath",
                "chmod 666 $targetSystemPrefsPath",
                "chown 1000:1000 $targetSystemPrefsPath",
                "chcon u:object_r:system_data_file:s0 $targetSystemPrefsPath"
            )
        }
    } catch (e: Exception) {
        Log.e("[Res3D]", "Failed to set pref file permissions", e)
    }
}

suspend fun applyRenderingSettings(
    res: String,
    sm: String,
    ffr: String,
    tf: String,
    cacheDir: File,
    uid: Int,
    context: Context
): String =
    withContext(Dispatchers.IO) {
        val tempDb = File(cacheDir, TEMP_APPLY_DB)
        var db: SQLiteDatabase? = null

        try {
            try {
                updateConfigFile(res = res, sm = sm, ffr = ffr, tf = tf)
            } catch (e: Exception) {
                Log.e("[Res3D]", "Failed to update config file with rendering settings", e)
            }

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
                } catch (e: Exception) {}

                if (!primaryWorked) {
                    val (cpSuccess, cpError) = runRootCommand(
                        "cp $DB_PATH ${tempDb.absolutePath}",
                        "chown $uid:$uid ${tempDb.absolutePath}",
                        "chmod 666 ${tempDb.absolutePath}"
                    )
                    if (!cpSuccess) return@withContext context.getString(
                        R.string.status_copy_failed,
                        cpError
                    )
                    if (!tempDb.exists()) return@withContext context.getString(R.string.status_temp_missing)

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
                        return@withContext context.getString(
                            R.string.status_write_failed,
                            writeError
                        )
                    }
                }
            }

            val (verifiedValues, _) = fetchConfigValues(cacheDir, uid, context)

            val isResVerified = res.isBlank() || verifiedValues.resolution == res
            val isSmVerified = sm.isBlank() || verifiedValues.stencilMesh == sm
            val isFfrVerified = ffr.isBlank() || verifiedValues.ffr == ffr
            val isTfVerified = tf.isBlank() || verifiedValues.textureFov == tf

            if (isResVerified && isSmVerified && isFfrVerified && isTfVerified) {
                runRootCommand("reboot")
                return@withContext context.getString(R.string.status_rebooting)
            } else {
                return@withContext context.getString(R.string.status_verify_failed)
            }
        } catch (e: Exception) {
            return@withContext (e.message ?: context.getString(R.string.status_error_prefix, "Unknown"))
        } finally {
            db?.takeIf { it.isOpen }?.close()
            if (tempDb.exists()) tempDb.delete()
        }
    }

/* Native Pico OSUIHelper via com.bytedance.osui.utils.OSUIHelper*/
fun triggerHaptic(
    context: Context,
    view: View? = null,
    isHover: Boolean = false,
    deviceId: Int = 0
) {
    val amplitude = if (isHover) 120f else 255f
    val frequency = if (isHover) 177f else 100f
    val duration = if (isHover) 10f else 35f

    val nativeDeviceId: Int = when (deviceId) {
        100001, 1 -> 1
        100002, 2 -> 2
        else -> 2
    }

    try {
        if (com.bytedance.osui.utils.OSUIHelper.isLoaded) {
            val res = com.bytedance.osui.utils.OSUIHelper.a(nativeDeviceId, amplitude, frequency, duration)
            Log.d("PicoHaptic", "OSUIHelper.a result for nativeDeviceId $nativeDeviceId (devId $deviceId): $res")
        } else {
            Log.w("PicoHaptic", "OSUIHelper is not loaded")
        }
    } catch (t: Throwable) {
        Log.e("PicoHaptic", "Error calling OSUIHelper.a", t)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.picoHapticHover(context: Context, view: View): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val motionEvent = event.motionEvent
            val deviceId = motionEvent?.deviceId ?: 0
            if (event.type == PointerEventType.Enter) {
                triggerHaptic(context, view, isHover = true, deviceId = deviceId)
            } else if (event.type == PointerEventType.Press) {
                triggerHaptic(context, view, isHover = false, deviceId = deviceId)
            }
        }
    }
}

fun runRootCommand(vararg commands: String): Pair<Boolean, String> {
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

fun runRootCommandWithOutput(vararg commands: String): Pair<Boolean, String> {
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

fun getConfigs(): ConfigValues {
    val defaults = ConfigValues()
    val configFile = File(CONFIG_PATH)

    if (configFile.exists()) {
        try {
            val content = configFile.readText().trim()
            if (content.contains(":")) {
                val levels = content.split(":")
                return ConfigValues(
                    maxPwrLevel = levels.getOrNull(0)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.maxPwrLevel,
                    minPwrLevel = levels.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.minPwrLevel,
                    cpuMode = levels.getOrNull(2)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.cpuMode,
                    gpuMode = levels.getOrNull(3)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.gpuMode,
                    latencyMode = levels.getOrNull(4)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.latencyMode,
                    cpuSet = levels.getOrNull(5)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.cpuSet,
                    resolution = levels.getOrNull(6)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.resolution,
                    stencilMesh = levels.getOrNull(7)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.stencilMesh,
                    ffr = levels.getOrNull(8)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.ffr,
                    textureFov = levels.getOrNull(9)?.trim().takeIf { !it.isNullOrBlank() } ?: defaults.textureFov
                )
            }
        } catch (e: Exception) {
            Log.e("[Res3D]", "Failed to read config file", e)
        }
    }

    return defaults
}
