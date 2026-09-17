package com.hamer.res3d

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class GuardianService : Service() {
    companion object {
        private val isRunning = AtomicBoolean(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("Res3D", "GuardianService: onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("Res3D", "GuardianService: onStartCommand triggered")

        if (isRunning.compareAndSet(false, true)) {
            thread {
                Log.i("Res3D", "GuardianService: Starting infinite loop")
                try {
                    val relayPath = "/data/local/tmp/res3d_pwr.relay"
                    val relayFile = File(relayPath)

                    while (true) {
                        if (relayFile.exists()) {
                            try {
                                val content = relayFile.readText().trim()
                                if (content.contains(":")) {
                                    val levels = content.split(":")
                                    val maxPwr = levels.getOrNull(0)?.trim() ?: ""
                                    val minPwr = levels.getOrNull(1)?.trim() ?: ""
                                    val tuneMode = levels.getOrNull(2)?.trim() ?: ""

                                    if (maxPwr.isNotBlank())
                                        execRoot("echo $maxPwr > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
                                    if (minPwr.isNotBlank())
                                        execRoot("echo $minPwr > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")

                                    // Apply Tune Mode if selected
                                    if (tuneMode == "1") { // Performance
                                        // CPU 0 1 2 3
                                        execRoot("echo performance > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor")
                                        execRoot("MAX=\$(awk '{print \$NF}' /sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies) && echo \$MAX > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq && echo \$MAX > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq")

                                        // CPU 4 5 6
                                        execRoot("echo performance > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor")
                                        execRoot("MAX=\$(awk '{print \$NF}' /sys/devices/system/cpu/cpufreq/policy4/scaling_available_frequencies) && echo \$MAX > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq && echo \$MAX > /sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq")

                                        // CPU 7
                                        execRoot("echo performance > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor")
                                        execRoot("MAX=\$(awk '{print \$NF}' /sys/devices/system/cpu/cpufreq/policy7/scaling_available_frequencies) && echo \$MAX > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq && echo \$MAX > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq")

                                        // GPU
                                        execRoot("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
                                        execRoot("echo 0 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel")
                                        execRoot("echo 0 > /sys/class/kgsl/kgsl-3d0/throttling")

                                        //Latency
                                        execRoot("echo 5000000 > /proc/sys/kernel/sched_latency_ns")
                                        execRoot("echo 1000000 > /proc/sys/kernel/sched_min_granularity_ns")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("Res3D", "GuardianService: Error reading relay: ${e.message}")
                            }
                        } else {
                            Log.e("Res3D", "GuardianService: Relay file missing at $relayPath")
                        }

                        Thread.sleep(3000)
                    }
                } catch (e: InterruptedException) {
                    Log.i("Res3D", "GuardianService: Loop interrupted")
                } catch (e: Exception) {
                    Log.e("Res3D", "GuardianService: Error in loop: ${e.message}")
                } finally {
                    isRunning.set(false)
                }
            }
        } else {
            Log.i("Res3D", "GuardianService: Loop already running")
        }

        return START_STICKY
    }

    private fun execRoot(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor()
            // Log.i("Res3D", "execRoot executed: $command")
        } catch (e: Exception) {
            Log.e("Res3D", "execRoot failed: ${e.message}")
        }
    }
}
