/*
 *  Copyright (c) 2026 Klimov Artem.
 *   All rights reserved.
 *
 *   Unauthorized reproduction, modification, distribution, or disclosure is prohibited.
 *
 *   For inquiries and questions, contact:
 *   Author: Klimov Artem
 *   Email: A976632@gmail.com
 */

package com.example.webmconverter.model

import android.net.Uri

enum class ConversionStatus {
    WAITING, CONVERTING, DONE, ERROR
}

enum class VideoQuality {
    LOW, MEDIUM, HIGH, LOSSLESS
}

data class VideoFile(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val durationMs: Long,
    val status: ConversionStatus = ConversionStatus.WAITING,
    val progress: Int = 0,
    val outputPath: String? = null,
    val quality: VideoQuality = VideoQuality.HIGH
)
