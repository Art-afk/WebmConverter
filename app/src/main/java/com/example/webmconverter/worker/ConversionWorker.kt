/*
 *  Copyright (c) 2026 Klimov Artem.
 *   All rights reserved.
 *
 *   For inquiries and questions, contact:
 *   Author: Klimov Artem
 *   LinkedIn: https://linkedin.com/in/artem-klimov-java
 */

package com.example.webmconverter.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.example.webmconverter.R
import com.example.webmconverter.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ConversionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriString = inputData.getString(KEY_INPUT_URI) ?: return@withContext Result.failure()
        val originalName = inputData.getString(KEY_FILE_NAME) ?: "video.webm"
        val durationMs = inputData.getLong(KEY_DURATION_MS, 1L)
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val qualityName = inputData.getString(KEY_QUALITY) ?: "HIGH"
        val quality = try { com.example.webmconverter.model.VideoQuality.valueOf(qualityName) } catch (e: Exception) { com.example.webmconverter.model.VideoQuality.HIGH }

        val uri = Uri.parse(uriString)

        setForeground(createForegroundInfo(originalName, 0))

        val tempFile = MediaStoreUtils.getTempOutputFile(applicationContext, originalName)
        
        val inputPath = FFmpegKitConfig.getSafParameterForRead(applicationContext, uri)
        val outputPath = tempFile.absolutePath

        val videoParams = when (quality) {
            com.example.webmconverter.model.VideoQuality.LOW -> "-vf \"scale=-2:360\" -c:v mpeg4 -b:v 800k"
            com.example.webmconverter.model.VideoQuality.MEDIUM -> "-vf \"scale=-2:720\" -c:v mpeg4 -b:v 2500k"
            com.example.webmconverter.model.VideoQuality.HIGH -> "-vf \"scale=-2:1080\" -c:v mpeg4 -b:v 5000k"
            com.example.webmconverter.model.VideoQuality.LOSSLESS -> "-c:v mpeg4 -q:v 2" // q:v 2 is very high quality for mpeg4
        }

        val command = "-y -i \"$inputPath\" $videoParams -c:a aac -b:a 128k \"$outputPath\""

        var lastProgress = -1

        val returnCode = suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeAsync(command, { session ->
                continuation.resume(session.returnCode)
            }, { log -> }, { statistics ->
                val timeInMilliseconds = statistics.time
                if (timeInMilliseconds > 0 && durationMs > 0) {
                    val progress = ((timeInMilliseconds.toFloat() / durationMs.toFloat()) * 100).toInt()
                    if (progress > lastProgress && progress <= 100) {
                        lastProgress = progress
                        setProgressAsync(workDataOf(KEY_ID to id, KEY_PROGRESS to progress))
                        if (progress % 5 == 0) {
                            try {
                                setForegroundAsync(createForegroundInfo(originalName, progress))
                            } catch (e: Exception) {}
                        }
                    }
                }
            })

            continuation.invokeOnCancellation {
                session.cancel()
            }
        }

        if (ReturnCode.isSuccess(returnCode)) {
            val finalPath = MediaStoreUtils.saveToMediaStore(applicationContext, tempFile, originalName)
            if (finalPath != null) {
                return@withContext Result.success(workDataOf(KEY_ID to id, KEY_OUTPUT_PATH to finalPath))
            } else {
                return@withContext Result.failure(workDataOf(KEY_ID to id))
            }
        } else {
            if (tempFile.exists()) tempFile.delete()
            return@withContext Result.failure(workDataOf(KEY_ID to id))
        }
    }

    private fun createForegroundInfo(fileName: String, progress: Int): ForegroundInfo {
        val id = applicationContext.getString(R.string.conversion_notification_channel)
        val title = applicationContext.getString(R.string.conversion_notification_title)
        val cancel = applicationContext.getString(R.string.cancel)
        
        val intent = androidx.work.WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, title, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("Конвертация $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_INPUT_URI = "input_uri"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_QUALITY = "quality"
        private const val NOTIFICATION_ID = 101
    }
}
