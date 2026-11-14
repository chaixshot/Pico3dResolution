package com.hamer.res3d

import android.util.Log
import java.io.File

class GuardianDaemon {
    companion object {
        const val CONFIG_PATH = "/data/local/tmp/config.cfg"

        fun getSystemProperty(key: String, default: String = ""): String {
            val getSystemPropertyMethod by lazy {
                try {
                    val clazz = Class.forName("android.os.SystemProperties")
                    clazz.getMethod("get", String::class.java, String::class.java)
                } catch (e: Exception) {
                    null
                }
            }

            return try {
                (getSystemPropertyMethod?.invoke(null, key, default) as? String) ?: default
            } catch (e: Exception) {
                default
            }
        }

        private fun isWearing(): Boolean {
            val psensor = getSystemProperty("sys.pxr.psensor.status", "0")
            val wearing = psensor == "0"
            if (wearing) {
                Log.i("[Res3D]", "Proximity Sensor: WEARING")
            } else {
                Log.i("[Res3D]", "Proximity Sensor: REMOVED")
            }
            return wearing
        }

        private fun getVrAppPackage(): String {
            val systemPrefixes = listOf(
                "com.pvr.vrshell",
                "com.pico.",
                "com.picovr.",
                "com.picoxr.",
                "com.bytedance.",
                "com.android.",
                "android",
            )

            fun isSystemPackage(pkg: String): Boolean {
                return pkg.startsWith("/") || systemPrefixes.any { pkg.startsWith(it) }
            }

            val mrUser = getSystemProperty("sys.pxr.mrserivce.user").ifBlank {
                getSystemProperty("sys.pxr.mrservice.user")
            }

            if (mrUser.isNotBlank() && !isSystemPackage(mrUser)) {
                Log.i("[Res3D]", "VR Detection: $mrUser")
                return mrUser
            }

            Log.i("[Res3D]", "VR Detection: none")
            return ""
        }

        private fun isPicoDockVisible(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys window windows | grep -A 8 'package=com.pvr.shortcut'"))
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                process.waitFor()

                val isVisible = output.contains("mViewVisibility=0x0")

                if (isVisible) {
                    Log.i("[Res3D]", "Pico Dock: VISIBLE")
                } else {
                    Log.i("[Res3D]", "Pico Dock: NOT VISIBLE")
                }

                isVisible
            } catch (e: Exception) {
                Log.e("[Res3D]", "isPicoDockVisible error: ${e.message}")
                false
            }
        }

        private fun execCmd(command: String) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                process.waitFor()
            } catch (_: Exception) {}
        }

        private fun movePackageCpuset(pkgName: String, topapp: Boolean) {
            if (pkgName.isNotBlank()) {
                val pidsToMove = getAllPidsForPackage(pkgName)

                val topAppFile = File("/dev/cpuset/top-app/cgroup.procs")
                val bgFile = File("/dev/cpuset/background/cgroup.procs")

                val topAppPids = try {
                    if (topAppFile.exists()) topAppFile.readLines().mapNotNull { it.trim().toIntOrNull() } else emptyList()
                } catch (_: Exception) { emptyList() }

                val bgPids = try {
                    if (bgFile.exists()) bgFile.readLines().mapNotNull { it.trim().toIntOrNull() } else emptyList()
                } catch (_: Exception) { emptyList() }

                val targetPath = if (topapp) "/dev/cpuset/top-app/cgroup.procs" else "/dev/cpuset/background/cgroup.procs"

                for (pid in pidsToMove) {
                    if (!topapp && !topAppPids.contains(pid)) continue // false: check pid is in top-app to move to background
                    if (topapp && !bgPids.contains(pid)) continue    // true: check pid is in background to move to top-app

                    Log.i("[Res3D]", "Move PID $pid ($pkgName) to ${if (topapp) "top-app" else "background"}")
                    try {
                        execCmd("echo $pid > $targetPath || true")
                        execCmd("echo $pid > $targetPath || true")
                    } catch (e: Exception) {
                        Log.e("[Res3D]", "Failed to move PID $pid: ${e.message}")
                    }
                }
            }
        }

        private fun getAllPidsForPackage(pkgName: String): List<Int> {
            val pids = mutableListOf<Int>()
            try {
                val procDir = File("/proc")
                procDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        val p = file.name.toIntOrNull()
                        if (p != null && p > 0) {
                            val cmdlineFile = File(file, "cmdline")
                            if (cmdlineFile.exists()) {
                                val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                                if (cmdline == pkgName || cmdline.startsWith("$pkgName:") || cmdline.contains(" $pkgName") || cmdline.contains("/$pkgName")) {
                                    if (!pids.contains(p)) {
                                        pids.add(p)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            return pids
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val myPid = android.os.Process.myPid()
            Log.i("[Res3D]", "Guardian Root Daemon starting (PID $myPid)...")

            // Kill any stale instances running from older executions
            execCmd("for p in \$(pgrep -f 'com.hamer.res3d.GuardianDaemon'); do if [ \"\$p\" != \"$myPid\" ]; then kill -9 \$p; fi; done || true")

            val config = File(CONFIG_PATH)
            var afterWearing = true

            while (true) {
                try {
                    if (config.exists()) {
                        val content = config.readText().trim()

                        if (content.contains(":")) {
                            var isWearing = isWearing()

                            if (!afterWearing && isWearing) {
                                Log.i("[Res3D]", "Headset put back on: waiting 10s...")
                                Thread.sleep(10000)
                                isWearing = isWearing()
                            }

                            if (isWearing || afterWearing) {
                                val vrPkg = getVrAppPackage()
                                val isVrMode = isWearing && vrPkg.isNotBlank() && !isPicoDockVisible()

                                val levels = content.split(":")
                                val maxPwr = levels.getOrNull(0)?.trim() ?: ""
                                val minPwr = levels.getOrNull(1)?.trim() ?: ""
                                val cpuMode = levels.getOrNull(2)?.trim() ?: ""
                                val gpuMode = levels.getOrNull(3)?.trim() ?: ""
                                val latencyMode = levels.getOrNull(4)?.trim() ?: ""
                                val cpuSet = levels.getOrNull(5)?.trim() ?: ""

                                if(isWearing) {
                                    if (maxPwr.isNotBlank())
                                        execCmd("echo $maxPwr > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
                                    if (minPwr.isNotBlank())
                                        execCmd("echo $minPwr > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")
                                } else { // Stock
                                    execCmd("echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
                                    execCmd("echo 6 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")
                                }

                                // Apply CPU Mode
                                if (cpuMode == "1" && isWearing) { // Performance
                                    execCmd("for p in /sys/devices/system/cpu/cpufreq/policy*; do echo performance > \$p/scaling_governor; done")
                                    execCmd("for p in /sys/devices/system/cpu/cpufreq/policy*; do MAX=\$(awk '{print \$NF}' \$p/scaling_available_frequencies) && echo \$MAX > \$p/scaling_max_freq && echo \$MAX > \$p/scaling_min_freq; done")
                                } else{ // Stock
                                    execCmd("for p in /sys/devices/system/cpu/cpufreq/policy*; do echo schedutil > \$p/scaling_governor; done")
                                    execCmd("for p in /sys/devices/system/cpu/cpufreq/policy*; do MIN=\$(cat \$p/cpuinfo_min_freq) && MAX=\$(cat \$p/cpuinfo_max_freq) && echo \$MIN > \$p/scaling_min_freq && echo \$MAX > \$p/scaling_max_freq; done")
                                }

                                // Apply GPU Mode
                                if (gpuMode == "1" && isWearing) { // Performance
                                    execCmd("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
                                    execCmd("echo 0 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
                                    execCmd("echo 0 > /sys/class/kgsl/kgsl-3d0/throttling")
                                } else { // Stock
                                    execCmd("echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
                                    execCmd("echo 6 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
                                    execCmd("echo 1 > /sys/class/kgsl/kgsl-3d0/throttling")
                                }

                                // Apply Latency Mode
                                if (latencyMode == "1" && isWearing) { // Performance
                                    execCmd("echo 5000000 > /proc/sys/kernel/sched_latency_ns")
                                    execCmd("echo 1000000 > /proc/sys/kernel/sched_min_granularity_ns")
                                } else { // Stock
                                    execCmd("echo 10000000 > /proc/sys/kernel/sched_latency_ns")
                                    execCmd("echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns")
                                }

                                // Apply CPU Set
                                when (cpuSet) {
                                    "1" if isWearing -> { // Performance
                                        if (isVrMode) { // Restrict system components to efficiency cores to save thermal budget for VR
                                            execCmd("echo 0-1 > /dev/cpuset/pico-system/cpus || true") // surfaceflinger, system_server, drm

                                            movePackageCpuset(vrPkg, true)
                                        } else {
                                            execCmd("echo 2-7 > /dev/cpuset/pico-system/cpus || true") // surfaceflinger, system_server, drm

                                            movePackageCpuset(vrPkg, false)
                                        }

                                        // Active foreground app
                                        execCmd("echo 4-7 > /dev/cpuset/top-app/cpus || true") // Focused App, hwservicemanager, servicemanager
                                        execCmd("echo 2-7 > /dev/cpuset/vrforeground/cpus || true") // pxrseethroughservice, pxrmrsystemservice

                                        // Head and controller tracking (needs dedicated responsive cores)
                                        execCmd("echo 0-3 > /dev/cpuset/tracking/cpus || true") // pvrtrackingservice

                                        // Restrict to efficiency cores
                                        execCmd("echo 0-1 > /dev/cpuset/compositor/cpus || true") // com.pico.xr.openxr_runtime
                                        execCmd("echo 0-1 > /dev/cpuset/audio-app/cpus || true") // Audio playback/recording threads

                                        // Confine low-priority background daemons strictly
                                        execCmd("echo 0 > /dev/cpuset/quickcamera/cpus || true") // Camera start speed

                                        // Starve general background tasks, confining them strictly
                                        execCmd("echo 0-1 > /dev/cpuset/foreground/cpus || true") // Standard foreground tasks running in background state
                                        execCmd("echo 0-1 > /dev/cpuset/bg_3rd_app/cpus || true") // Background third-party apps, killed all by OS
                                        execCmd("echo 0-1 > /dev/cpuset/app-inshell/cpus || true") // Apps running inside shell environments, empty by default OS
                                    }
                                    "2" if isWearing -> { // Balanced
                                        if (isVrMode) {
                                            execCmd("echo 0-2 > /dev/cpuset/pico-system/cpus || true") // surfaceflinger, system_server, drm

                                            movePackageCpuset(vrPkg, true)
                                        } else {
                                            execCmd("echo 0-6 > /dev/cpuset/pico-system/cpus || true") // surfaceflinger, system_server, drm

                                            movePackageCpuset(vrPkg, false)
                                        }

                                        // Active foreground app (mid/big cores, avoiding prime core to save power)
                                        execCmd("echo 4-6 > /dev/cpuset/top-app/cpus || true") // Focused App, hwservicemanager, servicemanager
                                        execCmd("echo 4-6 > /dev/cpuset/vrforeground/cpus || true") // pxrseethroughservice, pxrmrsystemservice

                                        // Head and controller tracking (efficient tracking cores)
                                        execCmd("echo 0-3 > /dev/cpuset/tracking/cpus || true") // pvrtrackingservice

                                        // Restrict to efficiency cores
                                        execCmd("echo 0-2 > /dev/cpuset/audio-app/cpus || true") // Audio playback/recording threads
                                        execCmd("echo 0-2 > /dev/cpuset/compositor/cpus || true") // com.pico.xr.openxr_runtime

                                        // Confine low-priority background daemons strictly
                                        execCmd("echo 0 > /dev/cpuset/quickcamera/cpus || true") // Camera start speed

                                        // Starve general background tasks, confining them strictly
                                        execCmd("echo 0-3 > /dev/cpuset/foreground/cpus || true") // Standard foreground tasks running in background state
                                        execCmd("echo 0-3 > /dev/cpuset/bg_3rd_app/cpus || true") // Background third-party apps, killed all by OS
                                        execCmd("echo 0-3 > /dev/cpuset/app-inshell/cpus || true") // Apps running inside shell environments, empty by default OS
                                    }
                                    else -> { // Stock
                                        execCmd("echo 4-6 > /dev/cpuset/top-app/cpus || true")
                                        execCmd("echo 4-5 > /dev/cpuset/vrforeground/cpus || true")

                                        execCmd("echo 0-3,7 > /dev/cpuset/compositor/cpus || true")
                                        execCmd("echo 0-6 > /dev/cpuset/pico-system/cpus || true")
                                        execCmd("echo 0-3 > /dev/cpuset/audio-app/cpus || true")
                                        execCmd("echo 0-3 > /dev/cpuset/camera-daemon/cpus || true")
                                        execCmd("echo 0-3,7 > /dev/cpuset/dex2oat/cpus || true")
                                        execCmd("echo 0-3,7 > /dev/cpuset/tracking/cpus || true")
                                        execCmd("echo 0-3 > /dev/cpuset/quickcamera/cpus || true")

                                        execCmd("echo 0-3 > /dev/cpuset/foreground/cpus || true")
                                        execCmd("echo 0-2 > /dev/cpuset/bg_3rd_app/cpus || true")
                                        execCmd("echo 0-1,4 > /dev/cpuset/app-inshell/cpus || true")
                                        execCmd("echo 5-6 > /dev/cpuset/shell-app/cpus || true") // com.pvr.vrshell, com.pico.mrservice, Home, Mixed Reality (Camera with UI)
                                    }

                                    /* Test command
                                        pids=$(tr '\n' ',' < /dev/cpuset/top-app/tasks | sed 's/,$//'); [ -n "$pids" ] && top -n 1 -p "$pids" || echo "No active processes in this cpuset."

                                        PIDS=$(pidof com.maniscat2.sm64coopdx | tr ' ' ',')
                                        [ -n "$PIDS" ] && top -b -H -n 1 -p "$PIDS" -o CPU,%CPU | awk '
                                        $1 ~ /^[0-9]+$/ { core[$1] += $2 }
                                            END {
                                                printf "%-8s %s\n", "CORE", "%CPU_USAGE"
                                                print "-------------------"
                                                for (c=0; c<=7; c++) printf "Core %-3d %.1f%%\n", c, core[c]+0
                                            }' || echo "Target is not running."
                                    */
                                }
                            }

                            afterWearing = isWearing
                        }
                    }
                } catch (e: Exception) {
                    Log.e("[Res3D]", "GuardianDaemon loop error: ${e.message}")
                }

                Log.i("[Res3D]", "-----")
                Thread.sleep(3000)
            }
        }
    }
}
