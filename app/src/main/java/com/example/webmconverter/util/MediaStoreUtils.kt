/*
 *  Copyright (c) 2026 Klimov Artem.
 *   All rights reserved.
 *
 *   For inquiries and questions, contact:
 *   Author: Klimov Artem
 *   LinkedIn: https://linkedin.com/in/artem-klimov-java
 */

package com.example.webmconverter.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MediaStoreUtils {

    fun getTempOutputFile(context: Context, originalName: String): File {
        val baseName = originalName.substringBeforeLast(".")
        val finalName = "$baseName.mp4"
        return File(context.cacheDir, finalName)
    }

    fun saveToMediaStore(context: Context, tempFile: File, originalName: String): String? {
        val baseName = originalName.substringBeforeLast(".")
        val finalName = "$baseName.mp4"

        var outputPath: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/WebMConverter")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    outputPath = uri.toString()
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                }
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "WebMConverter")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, finalName)
            try {
                tempFile.copyTo(file, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
                outputPath = file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (tempFile.exists()) {
            tempFile.delete()
        }
        
        return outputPath
    }
}
