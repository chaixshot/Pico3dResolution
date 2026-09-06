package com.hamer.res3d

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.content.Intent

class GuardianProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Log.i("Res3D", "GuardianProvider: onCreate called, starting service")
        try {
            val intent = Intent(context, GuardianService::class.java)
            context?.startService(intent)
        } catch (e: Exception) {
            Log.e("Res3D", "GuardianProvider: Failed to start service: ${e.message}")
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
