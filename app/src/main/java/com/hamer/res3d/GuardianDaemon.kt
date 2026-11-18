package com.hamer.res3d

import android.util.Log
import android.os.FileObserver
import android.os.Looper
import java.io.File
import java.lang.reflect.Method

class GuardianDaemon {
    companion object {
        const val CONFIG_PATH = "/data/local/tmp/config.cfg"
        const val TAG = "Res3Daemon"

        private data class DaemonState(
            var isWearing: Boolean? = null,
            var vrPackage: String? = null,
            var isPicoDockVisible: Boolean? = null,
            var isKeyboardVisible: Boolean? = null,
            var lastMovedVrPackage: String? = null,
            var lastMovedCpuSet: String? = null,
            var lastAppliedWearing: Boolean? = null,
            var lastAppliedVrPackage: String? = null,
        )

        private val daemonState = DaemonState()
        private val policyMaxFreqCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val policyMinFreqCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        private val systemPropertiesGetMethod: Method? by lazy {
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                clazz.getMethod("get", String::class.java, String::class.java)
            } catch (_: Exception) {
                null
            }
        }

        fun getSystemProperty(key: String, default: String = "0"): String {
            return try {
                (systemPropertiesGetMethod?.invoke(null, key, default) as? String) ?: default
            } catch (_: Exception) {
                default
            }
        }

        private fun isWearing(): Boolean {
            if (getSystemProperty("hamer.3dres.developermode", "0") == "1") return true

            val psensor = getSystemProperty("sys.pxr.psensor.status")
            val wearing = psensor == "0"
            if (wearing != daemonState.isWearing) {
                daemonState.isWearing = wearing
                if (wearing) {
                    Log.i(TAG, "Proximity Sensor: WEARING")
                } else {
                    Log.i(TAG, "Proximity Sensor: REMOVED")
                }
            }
            return wearing
        }

        private fun getVrAppPackage(): String {
            fun isSystemPackage(pkg: String): Boolean {
                val systemPrefixes = listOf(
                    "com.pvr.vrshell",
                )
                return pkg.startsWith("/") || systemPrefixes.any { pkg.startsWith(it) }
            }

            val mrUser = getSystemProperty("sys.pxr.mrserivce.user")
            val vrPkg = if (mrUser.isNotBlank() && !isSystemPackage(mrUser)) mrUser else ""
            if (vrPkg != daemonState.vrPackage) {
                daemonState.vrPackage = vrPkg
                if (vrPkg.isNotBlank()) {
                    Log.i(TAG, "VR Detection: $vrPkg")
                } else {
                    Log.i(TAG, "VR Detection: none")
                }
            }

            return vrPkg
        }

        private fun initSettingsObserver() {
            try {
                // Initial read on startup so daemonState.isPicoDockVisible is not null
                val file = File("/data/system/users/0/settings_global.xml")
                if (file.exists()) {
                    val isVisible = file.readText().contains("name=\"pvr_shortcut_status\" value=\"1\"")
                    daemonState.isPicoDockVisible = isVisible
                    Log.i(TAG, "Initial Pico Dock: ${if (isVisible) "VISIBLE" else "NOT VISIBLE"}")
                } else {
                    daemonState.isPicoDockVisible = false
                }

                val thread = Thread({
                    Looper.prepare()
                    val observer = object : FileObserver(File("/data/system/users/0"), CLOSE_WRITE or MOVED_TO or MODIFY) {
                        override fun onEvent(event: Int, path: String?) {
                            if (path == "settings_global.xml") {
                                try {
                                    val f = File("/data/system/users/0/settings_global.xml")
                                    if (f.exists()) {
                                        val isVisible = f.readText().contains("name=\"pvr_shortcut_status\" value=\"1\"")
                                        if (isVisible != daemonState.isPicoDockVisible) {
                                            daemonState.isPicoDockVisible = isVisible
                                            if (isVisible) {
                                                Log.i(TAG, "Pico Dock: VISIBLE")
                                            } else {
                                                Log.i(TAG, "Pico Dock: NOT VISIBLE")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to read settings_global.xml on event: ${e.message}")
                                }
                            }
                        }
                    }
                    observer.startWatching()
                    Looper.loop()
                }, "SettingsObserverThread")
                thread.isDaemon = true
                thread.start()
                Log.i(TAG, "Successfully initialized FileObserver for settings_global.xml")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FileObserver: ${e.message}")
            }
        }

        private fun isPicoDockVisible(): Boolean {
            return getSystemProperty("hamer.3dres.developermode", "0") != "1" && daemonState.isPicoDockVisible ?: false
        }

        private fun isKeyboardVisible(): Boolean {
            return try {
                val status = getSystemProperty("hamer.3dres.keyboard_status")
                val isVisible = status == "1"

                if (isVisible != daemonState.isKeyboardVisible) {
                    daemonState.isKeyboardVisible = isVisible
                    if (isVisible) {
                        Log.i(TAG, "Keyboard: VISIBLE")
                    } else {
                        Log.i(TAG, "Keyboard: NOT VISIBLE")
                    }
                }

                isVisible
            } catch (e: Exception) {
                Log.e(TAG, "isKeyboardVisible error: ${e.message}")
                false
            }
        }

        private fun execCmd(command: String) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                process.waitFor()
            } catch (_: Exception) {}
        }

        private fun setValueIfNeeded(path: String, value: String) {
            try {
                val file = File(path)
                if (!file.exists() || file.readText().trim() != value.trim()) {
                    file.writeText(value)
                }
            } catch (_: Exception) {
                try {
                    File(path).writeText(value)
                } catch (_: Exception) {}
            }
        }

        private fun movePackageToImmersiveAppSet(pkgName: String, toImmersive: Boolean = true) {
            if (pkgName.isNotBlank()) {
                val (sourceFile, targetFile, targetName) = if (toImmersive) {
                    Triple(
                        File("/dev/cpuset/top-app/cgroup.procs"),
                        File("/dev/cpuset/immersive-app/cgroup.procs"),
                        "immersive-app"
                    )
                } else {
                    Triple(
                        File("/dev/cpuset/immersive-app/cgroup.procs"),
                        File("/dev/cpuset/top-app/cgroup.procs"),
                        "top-app"
                    )
                }

                Thread {
                    Thread.sleep(2000) // Wait for subprocesses spawned

                    val sourcePids = try {
                        if (sourceFile.exists()) {
                            sourceFile.readLines().mapNotNull { it.trim().toIntOrNull() }
                        } else emptyList()
                    } catch (_: Exception) { emptyList() }

                    for (pid in sourcePids) {
                        val cmdlineFile = File("/proc/$pid/cmdline")
                        if (cmdlineFile.exists()) {
                            try {
                                val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                                val mainCmd = cmdline.split(Regex("\\s+")).firstOrNull() ?: cmdline
                                if (mainCmd == pkgName || mainCmd.startsWith("$pkgName:")) {
                                    targetFile.writeText(pid.toString())
                                    Log.i(TAG, "Move \"$pkgName\" to $targetName (PID: $pid)")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }.start()
            }
        }

        private fun movePackageToCodecAppSet(pkgName: String, toCodec: Boolean = true) {
            if (pkgName.isNotBlank()) {
                val (sourceFile, targetFile, targetName) = if (toCodec) {
                    Triple(
                        File("/dev/cpuset/foreground/cgroup.procs"),
                        File("/dev/cpuset/codec/cgroup.procs"),
                        "codec"
                    )
                } else {
                    Triple(
                        File("/dev/cpuset/codec/cgroup.procs"),
                        File("/dev/cpuset/foreground/cgroup.procs"),
                        "foreground"
                    )
                }

                val sourcePids = try {
                    if (sourceFile.exists()) {
                        sourceFile.readLines().mapNotNull { it.trim().toIntOrNull() }
                    } else emptyList()
                } catch (_: Exception) { emptySet() }

                for (pid in sourcePids) {
                    val cmdlineFile = File("/proc/$pid/cmdline")
                    if (cmdlineFile.exists()) {
                        try {
                            val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                            val mainCmd = cmdline.split(Regex("\\s+")).firstOrNull() ?: cmdline
                            if (mainCmd == pkgName || mainCmd.startsWith("$pkgName:")) {
                                targetFile.writeText(pid.toString())
                                Log.i(TAG, "Move \"$pkgName\" to $targetName (PID: $pid)")
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        private fun setupImmersiveAppCpuset() {
            try {
                val initialRootCmds = mutableListOf<String>()
                initialRootCmds.add("mkdir -p /dev/cpuset/immersive-app")
                initialRootCmds.add("cp /dev/cpuset/mems /dev/cpuset/immersive-app/mems || echo 0 > /dev/cpuset/immersive-app/mems")
                initialRootCmds.add("echo 4-7 > /dev/cpuset/immersive-app/cpus")
                initialRootCmds.add("chown -R system:system /dev/cpuset/immersive-app")
                initialRootCmds.add("chmod 755 /dev/cpuset/immersive-app")
                initialRootCmds.add("chmod 666 /dev/cpuset/immersive-app/tasks")
                initialRootCmds.add("chmod 666 /dev/cpuset/immersive-app/cgroup.procs")
                execCmd(initialRootCmds.joinToString(" && "))
                Log.i(TAG, "Successfully created /dev/cpuset/immersive-app")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create immersive-app cpuset: ${e.message}")
            }
        }

        private fun setupCodecCpuset() {
            try {
                val initialRootCmds = mutableListOf<String>()
                initialRootCmds.add("mkdir -p /dev/cpuset/codec")
                initialRootCmds.add("cp /dev/cpuset/mems /dev/cpuset/codec/mems || echo 0 > /dev/cpuset/codec/mems")
                initialRootCmds.add("echo 4-6 > /dev/cpuset/codec/cpus")
                initialRootCmds.add("chown -R system:system /dev/cpuset/codec")
                initialRootCmds.add("chmod 755 /dev/cpuset/codec")
                initialRootCmds.add("chmod 666 /dev/cpuset/codec/tasks")
                initialRootCmds.add("chmod 666 /dev/cpuset/codec/cgroup.procs")
                execCmd(initialRootCmds.joinToString(" && "))
                Log.i(TAG, "Successfully created /dev/cpuset/codec")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create codec cpuset: ${e.message}")
            }
        }

        private fun setupGuardianDaemonCpuset(myPid: Int){
            try {
                val bgCgroup = File("/dev/cpuset/background/cgroup.procs")
                if (bgCgroup.exists()) {
                    bgCgroup.writeText(myPid.toString())
                    Log.i(TAG, "Assigned GuardianDaemon to /dev/cpuset/background (PID $myPid)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to assign to background cpuset: ${e.message}")
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            var afterWearing = true
            val myPid = android.os.Process.myPid()
            val configs = getConfigs()

            Log.i(TAG, "Guardian Root Daemon starting (PID $myPid)...")

            // Kill any stale instances running from older executions
            execCmd("for p in \$(pgrep -f 'com.hamer.res3d.GuardianDaemon'); do if [ \"\$p\" != \"$myPid\" ]; then kill -9 \$p; fi; done || true")
            setupImmersiveAppCpuset()
            setupCodecCpuset()
            setupGuardianDaemonCpuset(myPid)
            initSettingsObserver()

            if(configs.cpuSet == "3") { // Stock
                movePackageToImmersiveAppSet( getVrAppPackage(), false)
                movePackageToCodecAppSet("media.codec", false)
                movePackageToCodecAppSet("media.swcodec", false)
            }

            while (true) {
                try {
                    var isWearing = isWearing()

                    if (!afterWearing && isWearing) {
                        Log.i(TAG, "Headset put back on: waiting 10s...")
                        Thread.sleep(10000)
                        isWearing = isWearing()
                    }

                    if (isWearing || afterWearing) {
                        val vrPkg = getVrAppPackage()
                        val isVrMode = isWearing && vrPkg.isNotBlank() && !isPicoDockVisible() && !isKeyboardVisible()
                        val isStreamingApp = vrPkg in listOf(
                            "com.picovr.picostreamassistant",
                            "alvr.client.dev",
                            "alvr.client",
                            "com.valvesoftware.steamlinekvr",
                            "com.valvesoftware.steamlink",
                            "VirtualDesktop.Android",
                            "app.rincast.vr",
                        )

                        if (isWearing != daemonState.lastAppliedWearing || vrPkg != daemonState.lastAppliedVrPackage) {
                            daemonState.lastAppliedWearing = isWearing
                            daemonState.lastAppliedVrPackage = vrPkg
                            // Apply Power Level
                            if (isWearing) {
                                if (configs.maxPwrLevel.isNotBlank())
                                    setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", configs.maxPwrLevel)
                                if (configs.minPwrLevel.isNotBlank())
                                    setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", configs.minPwrLevel)
                            } else { // Stock
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0")
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", "6")
                            }

                            // Apply CPU Mode
                            try {
                                val cpufreqDir = File("/sys/devices/system/cpu/cpufreq")
                                val policies = cpufreqDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("policy") } ?: emptyList()
                                for (policy in policies) {
                                    val policyPath = policy.absolutePath
                                    if (configs.cpuMode == "1" && isWearing) { // Performance
                                        setValueIfNeeded("$policyPath/scaling_governor", "performance")
                                        var maxFreq = policyMaxFreqCache[policyPath]
                                        if (maxFreq == null) {
                                            val availFreqFile = File(policy, "scaling_available_frequencies")
                                            if (availFreqFile.exists()) {
                                                maxFreq = availFreqFile.readText().trim().split(Regex("\\s+")).lastOrNull() ?: ""
                                                if (maxFreq.isNotBlank()) policyMaxFreqCache[policyPath] = maxFreq
                                            }
                                        }
                                        if (!maxFreq.isNullOrBlank()) {
                                            setValueIfNeeded("$policyPath/scaling_max_freq", maxFreq)
                                            setValueIfNeeded("$policyPath/scaling_min_freq", maxFreq)
                                        }
                                    } else { // Stock
                                        setValueIfNeeded("$policyPath/scaling_governor", "schedutil")
                                        var minFreq = policyMinFreqCache[policyPath]
                                        if (minFreq == null) {
                                            val minFreqFile = File(policy, "cpuinfo_min_freq")
                                            minFreq = if (minFreqFile.exists()) minFreqFile.readText().trim() else ""
                                            if (minFreq.isNotBlank()) policyMinFreqCache[policyPath] = minFreq
                                        }
                                        var maxFreq = policyMaxFreqCache[policyPath]
                                        if (maxFreq == null) {
                                            val maxFreqFile = File(policy, "cpuinfo_max_freq")
                                            maxFreq = if (maxFreqFile.exists()) maxFreqFile.readText().trim() else ""
                                            if (maxFreq.isNotBlank()) policyMaxFreqCache[policyPath] = maxFreq
                                        }
                                        if (minFreq.isNotBlank()) {
                                            setValueIfNeeded("$policyPath/scaling_min_freq", minFreq)
                                        }
                                        if (maxFreq.isNotBlank()) {
                                            setValueIfNeeded("$policyPath/scaling_max_freq", maxFreq)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to apply CPU mode: ${e.message}")
                            }

                            // Apply GPU Mode
                            if (configs.gpuMode == "1" && isWearing) { // Performance
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "performance")
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", "0")
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/throttling", "0")
                            } else { // Stock
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "msm-adreno-tz")
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", "6")
                                setValueIfNeeded("/sys/class/kgsl/kgsl-3d0/throttling", "1")
                            }

                            // Apply Latency Mode
                            if (configs.latencyMode == "1" && isWearing) { // Performance
                                setValueIfNeeded("/proc/sys/kernel/sched_latency_ns", "5000000")
                                setValueIfNeeded("/proc/sys/kernel/sched_min_granularity_ns", "1000000")
                            } else { // Stock
                                setValueIfNeeded("/proc/sys/kernel/sched_latency_ns", "10000000")
                                setValueIfNeeded("/proc/sys/kernel/sched_min_granularity_ns", "3000000")
                            }

                            if (configs.cpuSet in listOf("1", "2")) {
                                movePackageToImmersiveAppSet(vrPkg, true)
                                movePackageToCodecAppSet("media.codec", true)
                                movePackageToCodecAppSet("media.swcodec", true)
                            }
                        }

                        // Apply CPU Set
                        when (configs.cpuSet) {
                            "1" if (isWearing && !isStreamingApp) -> { // Performance
                                if (isVrMode) { // Full VR immersive
                                    setValueIfNeeded("/dev/cpuset/immersive-app/cpus", "4-7") // VR immersive app
                                    setValueIfNeeded("/dev/cpuset/pico-system/cpus", "0-1") // surfaceflinger, system_server, drm

                                    setValueIfNeeded("/dev/cpuset/top-app/cpus", "0-3") // Focused App, hwservicemanager, servicemanager
                                    setValueIfNeeded("/dev/cpuset/vrforeground/cpus", "4-7")  // Mixed Reality (pxrseethroughservice, pxrmrsystemservice)

                                    setValueIfNeeded("/dev/cpuset/foreground/cpus", "0-1") // Standard foreground tasks running in background state
                                } else { // 2D visible
                                    setValueIfNeeded("/dev/cpuset/immersive-app/cpus", "7")
                                    setValueIfNeeded("/dev/cpuset/pico-system/cpus", "0-6")

                                    setValueIfNeeded("/dev/cpuset/top-app/cpus", "4-7")
                                    setValueIfNeeded("/dev/cpuset/vrforeground/cpus", "4")

                                    setValueIfNeeded("/dev/cpuset/foreground/cpus", "0-4")
                                }

                                // Head and controller tracking (needs dedicated responsive cores)
                                setValueIfNeeded("/dev/cpuset/tracking/cpus", "0-3") // pvrtrackingservice

                                // Restrict to efficiency cores
                                setValueIfNeeded("/dev/cpuset/compositor/cpus", "0-3") // com.pico.xr.openxr_runtime
                                setValueIfNeeded("/dev/cpuset/audio-app/cpus", "0-2") // Audio playback/recording threads

                                // Confine low-priority background daemons strictly
                                setValueIfNeeded("/dev/cpuset/quickcamera/cpus", "0") // Camera start speed

                                // Starve general background tasks, confining them strictly
                                setValueIfNeeded("/dev/cpuset/bg_3rd_app/cpus", "0-1") // Background third-party apps, killed all by OS
                                setValueIfNeeded("/dev/cpuset/app-inshell/cpus", "0-1") // Apps running inside shell environments, empty by default OS
                            }
                            "2" if (isWearing && !isStreamingApp) -> { // Balance
                                if (isVrMode) {
                                    setValueIfNeeded("/dev/cpuset/immersive-app/cpus", "4-6")
                                    setValueIfNeeded("/dev/cpuset/pico-system/cpus", "0-1")

                                    setValueIfNeeded("/dev/cpuset/top-app/cpus", "0-3")
                                    setValueIfNeeded("/dev/cpuset/vrforeground/cpus", "4-6")

                                    setValueIfNeeded("/dev/cpuset/foreground/cpus", "0-1")
                                } else {
                                    setValueIfNeeded("/dev/cpuset/immersive-app/cpus", "6")
                                    setValueIfNeeded("/dev/cpuset/pico-system/cpus", "0-6")

                                    setValueIfNeeded("/dev/cpuset/top-app/cpus", "4-6")
                                    setValueIfNeeded("/dev/cpuset/vrforeground/cpus", "4")

                                    setValueIfNeeded("/dev/cpuset/foreground/cpus", "0-3")
                                }

                                setValueIfNeeded("/dev/cpuset/tracking/cpus", "0-3,7")

                                setValueIfNeeded("/dev/cpuset/compositor/cpus", "0-3,7")
                                setValueIfNeeded("/dev/cpuset/audio-app/cpus", "0-2")

                                setValueIfNeeded("/dev/cpuset/quickcamera/cpus", "0-2")

                                setValueIfNeeded("/dev/cpuset/bg_3rd_app/cpus", "0-2")
                                setValueIfNeeded("/dev/cpuset/app-inshell/cpus", "0-2")
                            }
                            in listOf("1", "2") if isStreamingApp -> { // Streaming PCVR (Power Saving)
                                if (isVrMode) {
                                    setValueIfNeeded("/dev/cpuset/codec/cpus", "4-5")
                                    setValueIfNeeded("/dev/cpuset/immersive-app/cpus", "6-7")

                                    setValueIfNeeded("/dev/cpuset/pico-system/cpus", "0-1")

                                    setValueIfNeeded("/dev/cpuset/top-app/cpus", "0-2")
                                    setValueIfNeeded("/dev/cpuset/vrforeground/cpus", "4-5")

                                    setValueIfNeeded("/dev/cpuset/foreground/cpus", "0-1")
                                } else {
                                    setValueIfNeeded("/dev/cpuset/codec/cpus", "7")
                                    setValueIfNeeded("/dev/cpuset/immersive-app/cpus", "7")

                                    setValueIfNeeded("/dev/cpuset/pico-system/cpus", "0-6")

                                    setValueIfNeeded("/dev/cpuset/top-app/cpus", "4-6")
                                    setValueIfNeeded("/dev/cpuset/vrforeground/cpus", "4")

                                    setValueIfNeeded("/dev/cpuset/foreground/cpus", "0-3")
                                }

                                setValueIfNeeded("/dev/cpuset/tracking/cpus", "0-5")

                                setValueIfNeeded("/dev/cpuset/compositor/cpus", "0-3")
                                setValueIfNeeded("/dev/cpuset/audio-app/cpus", "0-2")

                                setValueIfNeeded("/dev/cpuset/quickcamera/cpus", "0")

                                setValueIfNeeded("/dev/cpuset/bg_3rd_app/cpus", "0-1")
                                setValueIfNeeded("/dev/cpuset/app-inshell/cpus", "0-1")
                            }
                            else -> { // Stock
                                setValueIfNeeded("/dev/cpuset/top-app/cpus", "4-6")
                                setValueIfNeeded("/dev/cpuset/vrforeground/cpus", "4-5")
                                setValueIfNeeded("/dev/cpuset/compositor/cpus", "0-3,7")
                                setValueIfNeeded("/dev/cpuset/pico-system/cpus", "0-6")
                                setValueIfNeeded("/dev/cpuset/audio-app/cpus", "0-3")
                                setValueIfNeeded("/dev/cpuset/camera-daemon/cpus", "0-3")
                                setValueIfNeeded("/dev/cpuset/dex2oat/cpus", "0-3,7")
                                setValueIfNeeded("/dev/cpuset/tracking/cpus", "0-3,7")
                                setValueIfNeeded("/dev/cpuset/quickcamera/cpus", "0-3")
                                setValueIfNeeded("/dev/cpuset/foreground/cpus", "0-3")
                                setValueIfNeeded("/dev/cpuset/background/cpus", "0-1")
                                setValueIfNeeded("/dev/cpuset/bg_3rd_app/cpus", "0-2")
                                setValueIfNeeded("/dev/cpuset/app-inshell/cpus", "0-1,4")
                                setValueIfNeeded("/dev/cpuset/shell-app/cpus", "5-6")
                            }
                        }

                        afterWearing = isWearing

                        /* Test command
                            top -b -d 3.0 -p $(pgrep -f com.hamer.res3d.GuardianDaemon)

                            pids=$(tr '\n' ',' < /dev/cpuset/immersive-app/tasks | sed 's/,$//'); [ -n "$pids" ] && top -n 1 -p "$pids" || echo "No active processes in this cpuset."

                            PIDS=$(pidof app_process | tr ' ' ',')
                            [ -n "$PIDS" ] && top -b -H -n 1 -p "$PIDS" -o CPU,%CPU | awk '
                            $1 ~ /^[0-9]+$/ { core[$1] += $2 }
                                END {
                                    printf "%-8s %s\n", "CORE", "%CPU_USAGE"
                                    print "-------------------"
                                    for (c=0; c<=7; c++) printf "Core %-3d %.1f%%\n", c, core[c]+0
                                }' || echo "Target is not running."
                        */
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GuardianDaemon loop error: ${e.message}")
                }

                Thread.sleep(2000)
            }
        }
    }
}
