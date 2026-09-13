package com.hamer.res3d

import android.database.sqlite.SQLiteDatabase

class DbUpdater {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("ERROR: Missing arguments")
                return
            }

            val mode = args[0]
            val dbPath = args[1]

            try {
                if (mode == "read") {
                    val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
                    
                    fun queryValue(key: String): String {
                        db.rawQuery(
                            "SELECT LINKAGE_VALUE FROM RuleBean WHERE LINKAGE_KEY LIKE ? LIMIT 1",
                            arrayOf("%$key%")
                        ).use { cursor ->
                            if (cursor.moveToFirst()) {
                                return cursor.getString(0) ?: "null"
                            }
                        }
                        return "N/A"
                    }

                    val resVal = queryValue("sdk_eyebuffer")
                    val smVal = queryValue("sdk_enableFFRBySYS")
                    val ffrVal = queryValue("sdk_stencilMeshStatus")
                    val tfVal = queryValue("sdk_EyeTextureFov")

                    db.close()

                    println("RES=$resVal")
                    println("SM=$smVal")
                    println("FFR=$ffrVal")
                    println("TF=$tfVal")
                } else if (mode == "write") {
                    val res = args[2]
                    val sm = args[3]
                    val ffr = args[4]
                    val tf = args[5]

                    val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
                    db.beginTransaction()
                    try {
                        if (res.isNotBlank() && res != "null") {
                            db.execSQL(
                                "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_eyebuffer%'",
                                arrayOf(res)
                            )
                            db.execSQL(
                                "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_eyebuffer%'",
                                arrayOf(res, res)
                            )
                        }
                        if (sm.isNotBlank() && sm != "null") {
                            db.execSQL(
                                "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_enableFFRBySYS%'",
                                arrayOf(sm)
                            )
                            db.execSQL(
                                "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_enableFFRBySYS%'",
                                arrayOf(sm, sm)
                            )
                        }
                        if (ffr.isNotBlank() && ffr != "null") {
                            db.execSQL(
                                "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_stencilMeshStatus%'",
                                arrayOf(ffr)
                            )
                            db.execSQL(
                                "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_stencilMeshStatus%'",
                                arrayOf(ffr, ffr)
                            )
                        }
                        if (tf.isNotBlank() && tf != "null") {
                            db.execSQL(
                                "UPDATE RuleBean SET LINKAGE_VALUE = ? WHERE LINKAGE_KEY LIKE '%sdk_EyeTextureFov%'",
                                arrayOf(tf)
                            )
                            db.execSQL(
                                "UPDATE ConfigBean SET CONFIG_VALUE = ?, DEFAULT_CONFIG_VALUE = ? WHERE CONFIG_NAME LIKE '%sdk_EyeTextureFov%'",
                                arrayOf(tf, tf)
                            )
                        }
                        db.setTransactionSuccessful()
                        println("WRITE_SUCCESS")
                    } finally {
                        db.endTransaction()
                        db.close()
                    }
                }
            } catch (e: Exception) {
                println("ERROR: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
