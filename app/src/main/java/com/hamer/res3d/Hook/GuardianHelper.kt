package com.hamer.res3d.Hook

import com.hamer.res3d.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class GuardianHelper {
    companion object {
        private const val TAG = "Res3dHook"
        private val vrAppCache = ConcurrentHashMap<String, Boolean>()

        fun init(lpparam: LoadPackageParam) {
            // Keyboard visibility changed
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

            // Prevent OS moves processes from `immersive-app` and `codec`
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

                // Hook android.os.Process.setProcessGroup only (avoiding high-frequency setThreadGroup)
                val processClass = XposedHelpers.findClass("android.os.Process", lpparam.classLoader)
                try {
                    XposedBridge.hookAllMethods(processClass, "setProcessGroup", cpusetHook)
                    XposedBridge.log("$TAG: Hooked android.os.Process.setProcessGroup")
                } catch (e: Exception) {
                    XposedBridge.log("$TAG: Error hooking android.os.Process.setProcessGroup: ${e.message}")
                }
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Cpuset protection hook error: ${e.message}")
            }

            // Process start to cpuset `immersive-app`
            try {
                val processClass = XposedHelpers.findClass("android.os.Process", lpparam.classLoader)
                XposedBridge.hookAllMethods(processClass, "start", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val packageName = param.args.getOrNull(1) as? String ?: return
                        val result = param.result ?: return
                        val pid = extractPid(result) ?: return
                        checkAndMovePidToImmersiveApp(pid, packageName)
                    }
                })
                XposedBridge.log("$TAG: Successfully hooked android.os.Process.start in system_server")
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Process.start hook error: ${e.message}")
            }

            // Process warm/reuse start to cpuset `immersive-app`
            try {
                val processClass = XposedHelpers.findClass("android.os.Process", lpparam.classLoader)
                XposedBridge.hookMethod(processClass.getMethod("setProcessGroup", Int::class.java, Int::class.java), object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val pid = param.args.getOrNull(0) as? Int ?: return
                        checkAndMovePidToImmersiveApp(pid, "")
                    }
                })
                XposedBridge.log("$TAG: Successfully hooked android.os.Process.setProcessGroup for warm starts")
            } catch (e: Exception) {
                XposedBridge.log("$TAG: setProcessGroup hook error: ${e.message}")
            }
        }

        private fun checkAndMovePidToImmersiveApp(pid: Int, packageName: String) {
            if (pid <= 0) return
            Thread {
                Thread.sleep(2000)

                try {
                    val configs = getConfigs()
                    if (configs.cpuSet == "3") return@Thread

                    val targetName = packageName.ifBlank {
                        val cmdlineFile = File("/proc/$pid/cmdline")
                        if (cmdlineFile.exists()) {
                            val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                            cmdline.split(Regex("\\s+")).firstOrNull() ?: cmdline
                        } else ""
                    }

                    if (isVrApp(targetName)) {
                        val procCpusetFile = File("/proc/$pid/cpuset")
                        val currentCpuset = if (procCpusetFile.exists()) procCpusetFile.readText().trim() else ""
                        if (currentCpuset.contains("top-app")) {
                            val cpusetFile = File("/dev/cpuset/immersive-app/cgroup.procs")
                            if (cpusetFile.exists()) {
                                cpusetFile.writeText(pid.toString())
                                XposedBridge.log("$TAG: Successfully moved VR app process $targetName (PID $pid) to /dev/cpuset/immersive-app")
                            }
                        }
                    }
                } catch (e: Exception) {
                    XposedBridge.log("$TAG: Error in checkAndMovePidToImmersiveApp: ${e.message}")
                }
            }.start()
        }

        private fun isVrApp(packageName: String): Boolean {
            val cleanPkg = packageName.substringBefore(':').trim()
            if (cleanPkg.isBlank() || cleanPkg == "com.pvr.vrshell") {
                return false
            }

            val mrUser = getSystemProperty("sys.pxr.mrserivce.user")
            if (mrUser == cleanPkg || cleanPkg.startsWith(mrUser) || mrUser.startsWith(cleanPkg)) {
                return true
            }

            return vrAppCache.computeIfAbsent(cleanPkg) { pkg ->
                try {
                    val appGlobalsClass = Class.forName("android.app.AppGlobals")
                    val getPackageManagerMethod = appGlobalsClass.getMethod("getPackageManager")
                    val ipm = getPackageManagerMethod.invoke(null)

                    // Check ApplicationInfo metaData
                    try {
                        val getApplicationInfoMethod = ipm.javaClass.getMethod("getApplicationInfo", String::class.java, Int::class.java, Int::class.java)
                        val appInfo = getApplicationInfoMethod.invoke(ipm, pkg, 128, 0) // GET_META_DATA = 128
                        if (appInfo != null) {
                            val metaDataField = appInfo.javaClass.getField("metaData")
                            val metaData = metaDataField.get(appInfo) as? android.os.Bundle
                            if (metaData != null) {
                                val appType = metaData.getString("pvr.app.type")
                                val sdkVersion = metaData.getString("pvr.sdk.version")
                                val oculusVr = metaData.getString("com.oculus.intent.category.VR")
                                val yvrVr = metaData.getString("com.yvr.intent.category.VR")
                                if (appType == "vr" || sdkVersion == "OpenXR" || oculusVr == "vr_only" || yvrVr == "vr_only") {
                                    return@computeIfAbsent true
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // Check PackageInfo reqFeatures for VR / OpenXR features (e.g. android.hardware.vr.headtracking, android.software.xr.api.openxr)
                    try {
                        val getPackageInfoMethod = ipm.javaClass.getMethod("getPackageInfo", String::class.java, Int::class.java, Int::class.java)
                        val pkgInfo = getPackageInfoMethod.invoke(ipm, pkg, 0x00004000, 0) // GET_CONFIGURATIONS = 0x00004000
                        if (pkgInfo != null) {
                            val reqFeaturesField = pkgInfo.javaClass.getField("reqFeatures")
                            val reqFeatures = reqFeaturesField.get(pkgInfo) as? Array<*>
                            if (reqFeatures != null) {
                                for (feature in reqFeatures) {
                                    if (feature != null) {
                                        val nameField = try { feature.javaClass.getField("name") } catch (_: Exception) { null }
                                        val featName = nameField?.get(feature) as? String ?: continue
                                        if (featName.contains("vr.headtracking") || featName.contains("xr.api.openxr") || featName.contains("vr.high_performance")) {
                                            return@computeIfAbsent true
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                } catch (_: Exception) {
                }
                false
            }
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
    }
}
