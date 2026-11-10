package com.hamer.res3d

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

const val DB_PATH = "/data/user_de/0/com.pvr.configuration/databases/config.db"
const val TEMP_READ_DB = "config_temp.db"
const val TEMP_APPLY_DB = "config_apply.db"

data class ConfigValues(
    val resolution: String = "Unknown",
    val stencilMesh: String = "Unknown",
    val ffr: String = "Unknown",
    val textureFov: String = "Unknown",
    val maxPwrLevel: String = "Unknown",
    val minPwrLevel: String = "Unknown",
    val cpuMode: String = "Unknown",
    val gpuMode: String = "Unknown",
    val latencyMode: String = "Unknown",
    val cpuSet: String = "Unknown",
    val topAppCpus: String = "N/A",
    val vrForegroundCpus: String = "N/A",
    val compositorCpus: String = "N/A",
    val audioAppCpus: String = "N/A",
    val picoSystemCpus: String = "N/A",
    val cameraDaemonCpus: String = "N/A",
    val dex2oatCpus: String = "N/A",
    val trackingCpus: String = "N/A",
    val foregroundCpus: String = "N/A",
    val bg3rdAppCpus: String = "N/A",
    val appInshellCpus: String = "N/A",
    val shellAppCpus: String = "N/A"
)

data class CpuCoreStat(
    val coreId: Int,
    val freqMHz: String,
    val usagePercent: String
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
    val cpuCores: List<CpuCoreStat> = emptyList(),
    val memory: MemoryStats = MemoryStats()
)

data class CpuTick(val total: Long, val idle: Long)

fun readSysfs(path: String): String {
    val (success, output) = runRootCommandWithOutput("cat $path")
    return if (success) output else "N/A"
}

fun readCpuTicks(): Map<Int, CpuTick> {
    val map = mutableMapOf<Int, CpuTick>()
    val (success, output) = runRootCommandWithOutput("cat /proc/stat")
    if (success) {
        output.lines().forEach { line ->
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
    return map
}

fun fetchMemoryStats(): MemoryStats {
    var memTotalKb = 0L
    var memFreeKb = 0L
    var memAvailableKb = 0L
    var swapTotalKb = 0L
    var swapFreeKb = 0L

    val (success, output) = runRootCommandWithOutput("cat /proc/meminfo")
    if (success) {
        output.lines().forEach { line ->
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

suspend fun fetchLiveStats(): LiveStats = withContext(Dispatchers.IO) {
    var gpuFreq = "N/A"
    var gpuUsage = "N/A"
    val cpuCores = mutableListOf<CpuCoreStat>()

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

            cpuCores.add(CpuCoreStat(i, freqMHz, usage))
        }
    } catch (e: Exception) {
        Log.e("Res3D", "Error fetching live stats", e)
    }

    val memoryStats = fetchMemoryStats()
    LiveStats(gpuFreq, gpuUsage, cpuCores, memoryStats)
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
        var cpuSetVal = "N/A"
        var topAppCpusVal = "N/A"
        var vrForegroundCpusVal = "N/A"
        var compositorCpusVal = "N/A"
        var audioAppCpusVal = "N/A"
        var picoSystemCpusVal = "N/A"
        var cameraDaemonCpusVal = "N/A"
        var dex2oatCpusVal = "N/A"
        var trackingCpusVal = "N/A"
        var foregroundCpusVal = "N/A"
        var bg3rdAppCpusVal = "N/A"
        var appInshellCpusVal = "N/A"
        var shellAppCpusVal = "N/A"
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

                topAppCpusVal = readSysfs("/dev/cpuset/top-app/cpus")
                vrForegroundCpusVal = readSysfs("/dev/cpuset/vrforeground/cpus")
                compositorCpusVal = readSysfs("/dev/cpuset/compositor/cpus")
                audioAppCpusVal = readSysfs("/dev/cpuset/audio-app/cpus")
                picoSystemCpusVal = readSysfs("/dev/cpuset/pico-system/cpus")
                cameraDaemonCpusVal = readSysfs("/dev/cpuset/camera-daemon/cpus")
                dex2oatCpusVal = readSysfs("/dev/cpuset/dex2oat/cpus")
                trackingCpusVal = readSysfs("/dev/cpuset/tracking/cpus")
                foregroundCpusVal = readSysfs("/dev/cpuset/foreground/cpus")
                bg3rdAppCpusVal = readSysfs("/dev/cpuset/bg_3rd_app/cpus")
                appInshellCpusVal = readSysfs("/dev/cpuset/app-inshell/cpus")
                shellAppCpusVal = readSysfs("/dev/cpuset/shell-app/cpus")

                cpuSetVal = if (topAppCpusVal.contains("2-7") || topAppCpusVal.contains("0-7")) "1" else "2"

                return@withContext ConfigValues(
                    resVal,
                    smVal,
                    ffrVal,
                    tfVal,
                    maxPwr,
                    minPwr,
                    cpuVal,
                    gpuVal,
                    latVal,
                    cpuSetVal,
                    topAppCpusVal,
                    vrForegroundCpusVal,
                    compositorCpusVal,
                    audioAppCpusVal,
                    picoSystemCpusVal,
                    cameraDaemonCpusVal,
                    dex2oatCpusVal,
                    trackingCpusVal,
                    foregroundCpusVal,
                    bg3rdAppCpusVal,
                    appInshellCpusVal,
                    shellAppCpusVal
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

            topAppCpusVal = readSysfs("/dev/cpuset/top-app/cpus")
            vrForegroundCpusVal = readSysfs("/dev/cpuset/vrforeground/cpus")
            compositorCpusVal = readSysfs("/dev/cpuset/compositor/cpus")
            audioAppCpusVal = readSysfs("/dev/cpuset/audio-app/cpus")
            picoSystemCpusVal = readSysfs("/dev/cpuset/pico-system/cpus")
            cameraDaemonCpusVal = readSysfs("/dev/cpuset/camera-daemon/cpus")
            dex2oatCpusVal = readSysfs("/dev/cpuset/dex2oat/cpus")
            trackingCpusVal = readSysfs("/dev/cpuset/tracking/cpus")
            foregroundCpusVal = readSysfs("/dev/cpuset/foreground/cpus")
            bg3rdAppCpusVal = readSysfs("/dev/cpuset/bg_3rd_app/cpus")
            appInshellCpusVal = readSysfs("/dev/cpuset/app-inshell/cpus")
            shellAppCpusVal = readSysfs("/dev/cpuset/shell-app/cpus")

            cpuSetVal = if (topAppCpusVal.contains("2-7") || topAppCpusVal.contains("0-7")) "1" else "2"

        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
        } finally {
            if (tempDb.exists()) tempDb.delete()
        }

        ConfigValues(
            resVal,
            smVal,
            ffrVal,
            tfVal,
            maxPwr,
            minPwr,
            cpuVal,
            gpuVal,
            latVal,
            cpuSetVal,
            topAppCpusVal,
            vrForegroundCpusVal,
            compositorCpusVal,
            audioAppCpusVal,
            picoSystemCpusVal,
            cameraDaemonCpusVal,
            dex2oatCpusVal,
            trackingCpusVal,
            foregroundCpusVal,
            bg3rdAppCpusVal,
            appInshellCpusVal,
            shellAppCpusVal
        ) to errorMsg
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
    cpuSet: String,
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

            if (cpuSet == "2") { // Stock
                initialRootCmds.add("echo 4-6 > /dev/cpuset/top-app/cpus || true")
                initialRootCmds.add("echo 4-5 > /dev/cpuset/vrforeground/cpus || true")

                initialRootCmds.add("echo 0-3,7 > /dev/cpuset/compositor/cpus || true")
                initialRootCmds.add("echo 0-6 > /dev/cpuset/pico-system/cpus || true")
                initialRootCmds.add("echo 0-3 > /dev/cpuset/audio-app/cpus || true")
                initialRootCmds.add("echo 0-3 > /dev/cpuset/camera-daemon/cpus || true")
                initialRootCmds.add("echo 0-3,7 > /dev/cpuset/dex2oat/cpus || true")
                initialRootCmds.add("echo 0-3,7 > /dev/cpuset/tracking/cpus || true")

                initialRootCmds.add("echo 0-3 > /dev/cpuset/foreground/cpus || true")
                initialRootCmds.add("echo 0-2 > /dev/cpuset/bg_3rd_app/cpus || true")
                initialRootCmds.add("echo 0-1,4 > /dev/cpuset/app-inshell/cpus || true")
                initialRootCmds.add("echo 5-6 > /dev/cpuset/shell-app/cpus || true")
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
                    val relayCmd = "echo $maxPwr:$minPwr:$cpuMode:$gpuMode:$latencyMode:$cpuSet > $relayPath"

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
