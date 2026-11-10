package com.hamer.res3d

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.DataOutputStream

/**
 * GuardianService acts as the lightweight manager service for Pico3dResolution.
 * Its core responsibilities are:
 * 1. Protecting the app process from system kills and background restrictions (OOM score -1000, deviceidle whitelist).
 * 2. Spawning and maintaining the standalone root performance daemon [GuardianDaemon] via app_process.
 */
class GuardianService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("[Res3D]", "GuardianService: onCreate called")
        protectFromSystemKill()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("[Res3D]", "GuardianService: onStartCommand triggered")
        protectFromSystemKill()
        return START_STICKY
    }

    private fun protectFromSystemKill() {
        startRootDaemon()
        try {
            val pid = android.os.Process.myPid()
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("echo -1000 > /proc/$pid/oom_score_adj || true\n")
                os.writeBytes("dumpsys deviceidle whitelist +com.hamer.res3d || true\n")
                os.writeBytes("am set-standby-bucket com.hamer.res3d active || true\n")
                os.writeBytes("cmd appops set com.hamer.res3d RUN_IN_BACKGROUND allow || true\n")
                os.writeBytes("cmd appops set com.hamer.res3d RUN_ANY_IN_BACKGROUND allow || true\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor()
            Log.i("[Res3D]", "GuardianService: Protected from system kill (OOM adj -1000 & whitelisted)")
        } catch (e: Exception) {
            Log.e("[Res3D]", "Failed to protect from system kill: ${e.message}")
        }
    }

    private fun startRootDaemon() {
        try {
            val apkPath = applicationInfo.publicSourceDir
            // Terminate any stale GuardianDaemon instances and launch the fresh root daemon process
            val cmd = "pkill -f 'com.hamer.res3d.GuardianDaemon' 2>/dev/null || true; sleep 0.3; export CLASSPATH=\"$apkPath\" && exec app_process /system/bin com.hamer.res3d.GuardianDaemon &"

            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$cmd\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor()
            Log.i("[Res3D]", "GuardianService: Stale root daemon terminated & fresh GuardianDaemon launched")
        } catch (e: Exception) {
            Log.e("[Res3D]", "Failed to launch Root daemon: ${e.message}")
        }
    }
}
