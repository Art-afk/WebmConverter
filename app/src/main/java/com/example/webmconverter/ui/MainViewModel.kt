/*
 *  Copyright (c) 2026 Klimov Artem.
 *   All rights reserved.
 *
 *   For inquiries and questions, contact:
 *   Author: Klimov Artem
 *   LinkedIn: https://linkedin.com/in/artem-klimov-java
 */



package com.example.webmconverter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.webmconverter.model.ConversionStatus
import com.example.webmconverter.model.VideoFile
import com.example.webmconverter.util.FileUtils
import com.example.webmconverter.worker.ConversionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _files = MutableStateFlow<List<VideoFile>>(emptyList())
    val files: StateFlow<List<VideoFile>> = _files.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val workManager = WorkManager.getInstance(application)

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val newFiles = uris.map { uri ->
                val (name, size) = FileUtils.getFileNameAndSize(context, uri)
                val duration = FileUtils.getVideoDuration(context, uri)
                VideoFile(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    name = name,
                    size = size,
                    durationMs = duration
                )
            }
            _files.update { it + newFiles }
        }
    }

    fun removeFile(file: VideoFile) {
        _files.update { list -> list.filter { it.id != file.id } }
    }

    fun updateQuality(file: VideoFile, quality: com.example.webmconverter.model.VideoQuality) {
        _files.update { list ->
            list.map {
                if (it.id == file.id) it.copy(quality = quality)
                else it
            }
        }
    }

    fun startConversion() {
        val filesToConvert = _files.value.filter { it.status == ConversionStatus.WAITING || it.status == ConversionStatus.ERROR }
        if (filesToConvert.isEmpty()) return

        _isConverting.value = true

        var continuation = workManager.beginUniqueWork(
            "conversion_chain",
            ExistingWorkPolicy.REPLACE,
            createWorkRequest(filesToConvert.first())
        )

        for (i in 1 until filesToConvert.size) {
            continuation = continuation.then(createWorkRequest(filesToConvert[i]))
        }

        continuation.enqueue()

        _files.update { list ->
            list.map {
                if (filesToConvert.contains(it)) it.copy(status = ConversionStatus.WAITING, progress = 0)
                else it
            }
        }
    }

    private fun createWorkRequest(file: VideoFile) = OneTimeWorkRequestBuilder<ConversionWorker>()
        .setInputData(
            Data.Builder()
                .putString(ConversionWorker.KEY_ID, file.id)
                .putString(ConversionWorker.KEY_INPUT_URI, file.uri.toString())
                .putString(ConversionWorker.KEY_FILE_NAME, file.name)
                .putLong(ConversionWorker.KEY_DURATION_MS, file.durationMs)
                .putString(ConversionWorker.KEY_QUALITY, file.quality.name)
                .build()
        )
        .addTag("conversion")
        .addTag(file.id)
        .build()

    fun updateWorkStatus(workInfoList: List<WorkInfo>) {
        var anyRunning = false
        
        workInfoList.forEach { workInfo ->
            if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                anyRunning = true
            }

            val fileId = workInfo.tags.firstOrNull { tag -> _files.value.any { it.id == tag } } ?: return@forEach

            var progress = 0
            var outputPath: String? = null
            var status: ConversionStatus? = null

            if (workInfo.state == WorkInfo.State.RUNNING) {
                progress = workInfo.progress.getInt(ConversionWorker.KEY_PROGRESS, 0)
                status = ConversionStatus.CONVERTING
            } else if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                outputPath = workInfo.outputData.getString(ConversionWorker.KEY_OUTPUT_PATH)
                status = ConversionStatus.DONE
                progress = 100
            } else if (workInfo.state == WorkInfo.State.FAILED) {
                status = ConversionStatus.ERROR
            } else if (workInfo.state == WorkInfo.State.ENQUEUED) {
                status = ConversionStatus.WAITING
            }

            if (status != null) {
                _files.update { list ->
                    list.map {
                        if (it.id == fileId) {
                            if (it.status != ConversionStatus.DONE || status == ConversionStatus.DONE) {
                                it.copy(status = status, progress = progress, outputPath = outputPath ?: it.outputPath)
                            } else it
                        } else it
                    }
                }
            }
        }
        
        _isConverting.value = anyRunning
    }
}
