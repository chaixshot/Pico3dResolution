package com.hamer.res3d

import com.hamer.res3d.Hook.GuardianHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedInit : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "Res3dHook"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "android") return

        XposedBridge.log("$TAG: LSPosed module loaded for system_server")

        // Auto start GuardianDaemon
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.SystemServiceManager",
                lpparam.classLoader,
                "startBootPhase",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val phase = param.args[0] as Int
                        XposedBridge.log("$TAG: Boot phase $phase reached")
                        
                        // Phase 1000 (PHASE_BOOT_COMPLETED) is the most stable for app starts
                        if (phase == 1000) {
                            startAppGuardian()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Boot phase hook error: ${e.message}")
        }

        GuardianHelper.init(lpparam)
    }

    private fun startAppGuardian() {
        Thread {
            try {
                Thread.sleep(5000)
                XposedBridge.log("$TAG: Triggering Guardian via ContentProvider...")
                val cmd = "content query --uri content://com.hamer.res3d.guardian"
                execCmd(cmd)
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Trigger exception: ${e.message}")
            }
        }.start()
    }
}
