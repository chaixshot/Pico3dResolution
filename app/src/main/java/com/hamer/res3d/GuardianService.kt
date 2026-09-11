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

                                    if (maxPwr.isNotBlank()) execRoot("echo $maxPwr > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
                                    if (minPwr.isNotBlank()) execRoot("echo $minPwr > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")
                                }
                            } catch (e: Exception) {
                                Log.e("Res3D", "GuardianService: Error reading relay: ${e.message}")
                            }
                        } else {
                            Log.e("Res3D", "GuardianService: Relay file missing at $relayPath")
                        }
                        
                        Thread.sleep(10000)
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
