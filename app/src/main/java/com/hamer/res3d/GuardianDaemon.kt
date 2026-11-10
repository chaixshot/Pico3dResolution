package com.hamer.res3d

import android.util.Log
import java.io.File

class GuardianDaemon {
    companion object {
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

            val relayPath = "/data/local/tmp/res3d_pwr.relay"
            val relayFile = File(relayPath)

            while (true) {
                try {
                    if (relayFile.exists()) {
                        val content = relayFile.readText().trim()
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
                            if (cpuSet == "1") {
                                execCmd("echo 2-7 > /dev/cpuset/top-app/cpus || true")
                                execCmd("echo 2-7 > /dev/cpuset/vrforeground/cpus || true")
                                execCmd("echo 0-2 > /dev/cpuset/compositor/cpus || true")
                                execCmd("echo 0-2 > /dev/cpuset/audio-app/cpus || true")
                                execCmd("echo 0-4 > /dev/cpuset/pico-system/cpus || true")
                                execCmd("echo 0-2 > /dev/cpuset/camera-daemon/cpus || true")
                                execCmd("echo 0-2 > /dev/cpuset/dex2oat/cpus || true")
                                execCmd("echo 0-3 > /dev/cpuset/tracking/cpus || true")
                                execCmd("echo 0-1 > /dev/cpuset/foreground/cpus || true")
                                execCmd("echo 0-1 > /dev/cpuset/bg_3rd_app/cpus || true")
                                execCmd("echo 0-1 > /dev/cpuset/app-inshell/cpus || true")
                                execCmd("echo 0-1 > /dev/cpuset/shell-app/cpus || true")
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
