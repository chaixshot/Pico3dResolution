package com.hamer.res3d

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "android") return

        XposedBridge.log("Res3D: LSPosed module loaded for system_server")
        
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.SystemServiceManager",
                lpparam.classLoader,
                "startBootPhase",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val phase = param.args[0] as Int
                        XposedBridge.log("Res3D: Boot phase $phase reached")
                        
                        // Phase 1000 (PHASE_BOOT_COMPLETED) is the most stable for app starts
                        if (phase == 1000) {
                            startAppGuardian()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("Res3D: Hook error: ${e.message}")
        }
    }

    private fun startAppGuardian() {
        Thread {
            try {
                // Wait for package manager and content providers to be ready
                Thread.sleep(5000)
                
                XposedBridge.log("Res3D: Triggering Guardian via ContentProvider...")
                
                // Use content query to force start the app process. 
                // This bypasses Pico's ProcessIntercept for broadcasts.
                val cmd = "content query --uri content://com.hamer.res3d.guardian"
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
                
            } catch (e: Exception) {
                XposedBridge.log("Res3D: Trigger exception: ${e.message}")
            }
        }.start()
    }
}
