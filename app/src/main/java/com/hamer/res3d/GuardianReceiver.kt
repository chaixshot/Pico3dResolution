package com.hamer.res3d

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GuardianReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.hamer.res3d.ACTION_START_GUARDIAN") {
            Log.d("Res3D", "GuardianReceiver: Received broadcast trigger")
            
            try {
                val serviceIntent = Intent(context, GuardianService::class.java)
                context.startService(serviceIntent)
                Log.d("Res3D", "GuardianReceiver: Successfully called startService")
            } catch (e: Exception) {
                Log.e("Res3D", "GuardianReceiver: Failed to start service: ${e.message}")
            }
        }
    }
}
