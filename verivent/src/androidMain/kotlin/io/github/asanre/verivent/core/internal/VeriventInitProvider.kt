package io.github.asanre.verivent.core.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Created by the system before `Application.onCreate`, only to keep the app's context: the core has
 * no other way to read the app's version, which every export names. Answers no queries.
 */
internal class VeriventInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        appContext = context?.applicationContext
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        @Volatile
        private var appContext: Context? = null

        /** The app's `versionName`, or null when it declares none or the provider hasn't run (host unit tests). */
        val appVersion: String?
            get() = appContext?.let { context ->
                runCatching {
                    @Suppress("DEPRECATION") // The PackageInfoFlags overload is API 33; minSdk is 24.
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.onFailure { DiagnosticLog.warn("core", "could not read the app's version", it) }.getOrNull()
            }
    }
}
