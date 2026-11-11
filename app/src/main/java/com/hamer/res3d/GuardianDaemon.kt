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

        private fun isVrAppRunning(): Boolean {
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

            // Fast Path 1: Pico Native System Property (Microsecond direct memory read)
            val mrUser = getSystemProperty("sys.pxr.mrserivce.user").ifBlank {
                getSystemProperty("sys.pxr.mrservice.user")
            }

            if (mrUser.isNotBlank() && !isSystemPackage(mrUser)) {
                Log.i("[Res3D]", "VR Detection [Fast Path 1 - SystemProperty]: Immersive VR App active: $mrUser")
                return true
            }

            // Fast Path 2: Direct Kernel Cgroup procfs read (Sub-millisecond file I/O, no shell spawn)
            try {
                val vrCgroupFile = File("/dev/cpuset/vrforeground/cgroup.procs")
                if (vrCgroupFile.exists()) {
                    val pids = vrCgroupFile.readLines().mapNotNull { it.trim().toIntOrNull() }
                    for (pid in pids) {
                        val cmdlineFile = File("/proc/$pid/cmdline")
                        if (cmdlineFile.exists()) {
                            val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                            val pkg = cmdline.split(" ").firstOrNull()?.trim()
                            if (!pkg.isNullOrEmpty() && !isSystemPackage(pkg)) {
                                Log.i("[Res3D]", "VR Detection [Fast Path 2 - Cgroup procfs]: Immersive VR App process active: $pkg (PID $pid)")
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("[Res3D]", "Fast Path 2 Cgroup check error: ${e.message}")
            }

            Log.i("[Res3D]", "VR Detection: No 3D Immersive VR App active")
            return false
        }

        private fun isPicoDockVisible(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys window windows | grep -A 8 'package=com.pvr.shortcut'"))
                val output = process.inputStream.bufferedReader().use { it.readText() }
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

        @JvmStatic
        fun main(args: Array<String>) {
            val myPid = android.os.Process.myPid()
            Log.i("[Res3D]", "Guardian Root Daemon starting (PID $myPid)...")

            // Kill any stale instances running from older executions
            execCmd("for p in \$(pgrep -f 'com.hamer.res3d.GuardianDaemon'); do if [ \"\$p\" != \"$myPid\" ]; then kill -9 \$p; fi; done || true")

            val config = File(CONFIG_PATH)

            while (true) {
                try {
                    if (config.exists()) {
                        val content = config.readText().trim()
                        if (content.contains(":")) {
                            val levels = content.split(":")
                            val maxPwr = levels.getOrNull(0)?.trim() ?: ""
                            val minPwr = levels.getOrNull(1)?.trim() ?: ""
                            val cpuMode = levels.getOrNull(2)?.trim() ?: ""
                            val gpuMode = levels.getOrNull(3)?.trim() ?: ""
                            val latencyMode = levels.getOrNull(4)?.trim() ?: ""
                            val cpuSet = levels.getOrNull(5)?.trim() ?: ""

                            if (maxPwr.isNotBlank())
                                execCmd("echo $maxPwr > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
                            if (minPwr.isNotBlank())
                                execCmd("echo $minPwr > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")

                            // Apply CPU Mode
                            if (cpuMode == "1") {
                                execCmd("for p in /sys/devices/system/cpu/cpufreq/policy*; do echo performance > \$p/scaling_governor; done")
                                execCmd("for p in /sys/devices/system/cpu/cpufreq/policy*; do MAX=\$(awk '{print \$NF}' \$p/scaling_available_frequencies) && echo \$MAX > \$p/scaling_max_freq && echo \$MAX > \$p/scaling_min_freq; done")
                            }

                            // Apply GPU Mode
                            if (gpuMode == "1") {
                                execCmd("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
                                execCmd("echo 0 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
                                execCmd("echo 0 > /sys/class/kgsl/kgsl-3d0/throttling")
                            }

                            // Apply Latency Mode
                            if (latencyMode == "1") {
                                execCmd("echo 5000000 > /proc/sys/kernel/sched_latency_ns")
                                execCmd("echo 1000000 > /proc/sys/kernel/sched_min_granularity_ns")
                            }

                            // Apply CPU Set
                            if (cpuSet == "1" && isWearing()) {
                                if(isVrAppRunning() && !isPicoDockVisible()) {
                                    // Give 3D VR app all high-performance cores
                                    execCmd("echo 2-7 > /dev/cpuset/top-app/cpus || true") // Active foreground app
                                    execCmd("echo 2-7 > /dev/cpuset/vrforeground/cpus || true") // Active VR application

                                    execCmd("echo 0-1 > /dev/cpuset/compositor/cpus || true")
                                    execCmd("echo 0-2 > /dev/cpuset/pico-system/cpus || true")
                                    execCmd("echo 0-2 > /dev/cpuset/camera-daemon/cpus || true")
                                    execCmd("echo 0-2 > /dev/cpuset/tracking/cpus || true")
                                }else{
                                    // Give 2D foreground app all high-performance cores
                                    execCmd("echo 0-7 > /dev/cpuset/top-app/cpus || true")
                                    execCmd("echo 5-6 > /dev/cpuset/vrforeground/cpus || true")

                                    execCmd("echo 0-7 > /dev/cpuset/compositor/cpus || true")
                                    execCmd("echo 0-7 > /dev/cpuset/pico-system/cpus || true")
                                    execCmd("echo 0-7 > /dev/cpuset/camera-daemon/cpus || true")
                                    execCmd("echo 0-7 > /dev/cpuset/tracking/cpus || true")
                                }

                                execCmd("echo 0-1 > /dev/cpuset/audio-app/cpus || true")
                                execCmd("echo 0-2 > /dev/cpuset/dex2oat/cpus || true")

                                // Starve background tasks, confining them strictly to efficiency cores
                                execCmd("echo 0-1 > /dev/cpuset/foreground/cpus || true") // Standard foreground tasks
                                execCmd("echo 0-1 > /dev/cpuset/bg_3rd_app/cpus || true") // Background third-party apps
                                execCmd("echo 0-1 > /dev/cpuset/app-inshell/cpus || true") // Shell app cgroups
                                execCmd("echo 0-1 > /dev/cpuset/shell-app/cpus || true") // ADB / shell app execution
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("[Res3D]", "GuardianDaemon loop error: ${e.message}")
                }

                Thread.sleep(3000)
            }
        }
    }
}
