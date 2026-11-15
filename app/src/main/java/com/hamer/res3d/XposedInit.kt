package com.hamer.res3d

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

class XposedInit : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "Res3dHook"
    }

    private var systemPropertiesSetMethod: Method? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "android") return

        XposedBridge.log("$TAG: LSPosed module loaded for system_server")

        // Initialize reflection cache for SystemProperties.set
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            systemPropertiesSetMethod = clazz.getMethod("set", String::class.java, String::class.java)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: SystemProperties.set reflection error: ${e.message}")
        }

        // Auto start guardian daemon
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

        // Keyboard visibility setprop
        try {
            setSystemProperty("hamer.3dres.keyboard_status", "0")

            val immsClass = XposedHelpers.findClass(
                "com.android.server.inputmethod.InputMethodManagerService",
                lpparam.classLoader
            )

            // Hook all common show methods in InputMethodManagerService
            val showMethodNames = listOf(
                "showSoftInput",
                "showCurrentInputLocked"
            )
            for (methodName in showMethodNames) {
                try {
                    XposedBridge.hookAllMethods(immsClass, methodName, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            setSystemProperty("hamer.3dres.keyboard_status", "1")
                            XposedBridge.log("$TAG: SystemServer - $methodName -> keyboard_status=1")
                        }
                    })
                } catch (_: Exception) {
                }
            }

            // Hook all common hide, finish, and unbind methods in InputMethodManagerService
            val hideMethodNames = listOf(
                "hideCurrentInputLocked",
                "hideMySoftInput",
                "hideSoftInput"
            )
            for (methodName in hideMethodNames) {
                try {
                    XposedBridge.hookAllMethods(immsClass, methodName, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            setSystemProperty("hamer.3dres.keyboard_status", "0")
                            XposedBridge.log("$TAG: SystemServer - $methodName -> keyboard_status=0")
                        }
                    })
                } catch (_: Exception) {
                }
            }

            XposedBridge.log("Res3D: Successfully hooked InputMethodManagerService methods in system_server")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: InputMethodManagerService hook error: ${e.message}")
        }
    }

    private fun startAppGuardian() {
        Thread {
            try {
                Thread.sleep(5000)
                XposedBridge.log("$TAG: Triggering Guardian via ContentProvider...")
                val cmd = "content query --uri content://com.hamer.res3d.guardian"
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Trigger exception: ${e.message}")
            }
        }.start()
    }

    private fun setSystemProperty(key: String, value: String) {
        try {
            val method = systemPropertiesSetMethod
            if (method != null) {
                method.invoke(null, key, value)
            } else {
                val clazz = Class.forName("android.os.SystemProperties")
                val fallbackMethod = clazz.getMethod("set", String::class.java, String::class.java)
                fallbackMethod.invoke(null, key, value)
            }
        } catch (e: Exception) {
            try {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "setprop $key '$value'")).waitFor()
            } catch (_: Exception) {
            }
        }
    }
}
