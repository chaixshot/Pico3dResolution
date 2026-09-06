package com.hamer.res3d

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import kotlin.concurrent.thread

class GuardianService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("Res3D", "GuardianService: onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("Res3D", "GuardianService: onStartCommand triggered")
        
        thread {
            try {
                // Initial delay to let system stabilize
                Thread.sleep(5000)
                
                val relayPath = "/data/local/tmp/res3d_pwr.relay"
                val relayFile = File(relayPath)
                
                if (!relayFile.exists()) {
                    Log.e("Res3D", "GuardianService: Relay file missing at $relayPath")
                    stopSelf()
                    return@thread
                }

                val content = relayFile.readText().trim()
                Log.i("Res3D", "GuardianService: Read relay: '$content'")
                
                if (!content.contains(":")) {
                    Log.e("Res3D", "GuardianService: Invalid relay format")
                    stopSelf()
                    return@thread
                }

                val levels = content.split(":")
                val maxPwr = levels.getOrNull(0)?.trim() ?: ""
                val minPwr = levels.getOrNull(1)?.trim() ?: ""

                Log.i("Res3D", "GuardianService: Applying levels once -> Max:$maxPwr Min:$minPwr")
                
                if (maxPwr.isNotBlank()) execRoot("echo $maxPwr > /sys/class/kgsl/kgsl-3d0/max_pwrlevel")
                if (minPwr.isNotBlank()) execRoot("echo $minPwr > /sys/class/kgsl/kgsl-3d0/min_pwrlevel")
                
            } catch (e: Exception) {
                Log.e("Res3D", "GuardianService: Error: ${e.message}")
            } finally {
                Log.i("Res3D", "GuardianService: Task finished. Stopping.")
                stopSelf()
            }
        }

        return START_NOT_STICKY
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
            Log.i("Res3D", "execRoot executed: $command")
        } catch (e: Exception) {
            Log.e("Res3D", "execRoot failed: ${e.message}")
        }
    }
}
