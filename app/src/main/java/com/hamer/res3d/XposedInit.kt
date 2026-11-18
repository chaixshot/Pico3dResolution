package com.hamer.res3d

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File
import java.lang.reflect.Method

class XposedInit : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "Res3dHook"
    }

    private var systemPropertiesSetMethod: Method? = null
    private var systemPropertiesGetMethod: Method? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "android") return

        XposedBridge.log("$TAG: LSPosed module loaded for system_server")

        // Initialize reflection cache for SystemProperties.get and set
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            systemPropertiesGetMethod = clazz.getMethod("get", String::class.java, String::class.java)
            systemPropertiesSetMethod = clazz.getMethod("set", String::class.java, String::class.java)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: SystemProperties reflection error: ${e.message}")
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

        // Prevent OS moves processes from "/dev/cpuset/immersive-app" or "/dev/cpuset/codec"
        try {
            fun isPidInProtectedCpuset(pid: Int): Boolean {
                if (pid <= 0) return false
                try {
                    val cpusetFile = File("/proc/$pid/cpuset")
                    if (cpusetFile.exists()) {
                        val content = cpusetFile.readText().trim()
                        if (content.contains("immersive-app") || content.contains("codec")) {
                            return true
                        }
                    }
                } catch (_: Exception) {
                }
                return false
            }

            val cpusetHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val pid = extractPid(param.args.getOrNull(0)) ?: return
                    if (isPidInProtectedCpuset(pid)) {
                        param.result = null
                        XposedBridge.log("$TAG: Prevented OS from changing cpusets for PID $pid")
                    }
                }
            }

            // Hook android.os.Process methods
            val processClass = XposedHelpers.findClass("android.os.Process", lpparam.classLoader)
            val processMethodNames = listOf("setProcessGroup", "setThreadGroup", "setThreadGroupAndGroup")
            for (methodName in processMethodNames) {
                try {
                    XposedBridge.hookAllMethods(processClass, methodName, cpusetHook)
                    XposedBridge.log("$TAG: Hooked android.os.Process.$methodName")
                } catch (e: Exception) {
                    XposedBridge.log("$TAG: Error hooking android.os.Process.$methodName: ${e.message}")
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Cpuset protection hook error: ${e.message}")
        }

        // Hook process start to set cpuset to "/dev/cpuset/immersive-app" if it is a VR app
        try {
            val processClass = XposedHelpers.findClass("android.os.Process", lpparam.classLoader)
            XposedBridge.hookAllMethods(processClass, "start", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val niceName = param.args.getOrNull(1) as? String ?: return
                    val result = param.result ?: return
                    val pid = extractPid(result) ?: return
                    if (pid <= 0) return

                    Thread {
                        try {
                            val configs = getConfigs()
                            if (configs.cpuSet == "3") return@Thread

                            Thread.sleep(3000) // sys.pxr.mrserivce.user changes after app launched in delay

                            val vrPkg = getVrAppPackage()
                            if (vrPkg.isNotBlank() && niceName.startsWith(vrPkg)) {
                                val procCpusetFile = File("/proc/$pid/cpuset")
                                val currentCpuset = if (procCpusetFile.exists()) procCpusetFile.readText().trim() else ""
                                if (currentCpuset.contains("top-app")) {
                                    val cpusetFile = File("/dev/cpuset/immersive-app/cgroup.procs")
                                    if (cpusetFile.exists()) {
                                        cpusetFile.writeText(pid.toString())
                                        XposedBridge.log("$TAG: Successfully moved VR app process $niceName (PID $pid) to /dev/cpuset/immersive-app")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("$TAG: Error setting cpuset for process start ($niceName): ${e.message}")
                        }
                    }.start()
                }
            })
            XposedBridge.log("$TAG: Successfully hooked android.os.Process.start in system_server")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Process.start hook error: ${e.message}")
        }

        // Hook process warm starts and process reuse to set cpuset to "/dev/cpuset/immersive-app" if it is a VR app
        try {
            val processClass = XposedHelpers.findClass("android.os.Process", lpparam.classLoader)
            XposedBridge.hookMethod(processClass.getMethod("setProcessGroup", Int::class.java, Int::class.java), object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val pid = param.args.getOrNull(0) as? Int ?: return
                    if (pid <= 0) return

                    Thread {
                        try {
                            val configs = getConfigs()
                            if (configs.cpuSet == "3") return@Thread

                            Thread.sleep(3000) // sys.pxr.mrserivce.user changes after app launched in delay

                            val vrPkg = getVrAppPackage()
                            val cmdlineFile = File("/proc/$pid/cmdline")

                            if (vrPkg.isNotBlank() && cmdlineFile.exists()) {
                                val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                                val mainCmd = cmdline.split(Regex("\\s+")).firstOrNull() ?: cmdline

                                if (mainCmd == vrPkg || mainCmd.startsWith("$vrPkg:")) {
                                    val procCpusetFile = File("/proc/$pid/cpuset")
                                    val currentCpuset = if (procCpusetFile.exists()) procCpusetFile.readText().trim() else ""
                                    if (currentCpuset.contains("top-app")) {
                                        val cpusetFile = File("/dev/cpuset/immersive-app/cgroup.procs")
                                        if (cpusetFile.exists()) {
                                            cpusetFile.writeText(pid.toString())
                                            XposedBridge.log("$TAG: Successfully moved warm/reused VR app process $mainCmd (PID $pid) to /dev/cpuset/immersive-app")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("$TAG: Error in checkAndMovePidToImmersiveApp: ${e.message}")
                        }
                    }.start()
                }
            })
            XposedBridge.log("$TAG: Successfully hooked android.os.Process.setProcessGroup for warm starts")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: setProcessGroup hook error: ${e.message}")
        }
    }

    private fun getVrAppPackage(): String {
        fun isSystemPackage(pkg: String): Boolean {
            val systemPrefixes = listOf(
                "com.pvr.vrshell",
            )
            return pkg.startsWith("/") || systemPrefixes.any { pkg.startsWith(it) }
        }

        val mrUser = getSystemProperty("sys.pxr.mrserivce.user")
        val vrPkg = if (mrUser.isNotBlank() && !isSystemPackage(mrUser)) mrUser else ""
        return vrPkg
    }

    private fun extractPid(arg: Any?): Int? {
        if (arg == null) return null
        if (arg is Int) return arg
        return try {
            XposedHelpers.getIntField(arg, "pid")
        } catch (_: Exception) {
            try {
                (XposedHelpers.callMethod(arg, "getPid") as? Int)
            } catch (_: Exception) {
                null
            }
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

    private fun getSystemProperty(key: String, default: String = ""): String {
        return try {
            val method = systemPropertiesGetMethod ?: Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java).also { systemPropertiesGetMethod = it }
            (method.invoke(null, key, default) as? String) ?: default
        } catch (_: Exception) {
            default
        }
    }

}
