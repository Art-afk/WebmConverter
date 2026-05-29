/*
 *  Copyright (c) 2026 Klimov Artem.
 *   All rights reserved.
 *
 *   For inquiries and questions, contact:
 *   Author: Klimov Artem
 *   LinkedIn: https://linkedin.com/in/artem-klimov-java
 */

package com.example.webmconverter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.webmconverter.R
import com.example.webmconverter.databinding.ItemVideoFileBinding
import com.example.webmconverter.model.ConversionStatus
import com.example.webmconverter.model.VideoFile
import com.example.webmconverter.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileAdapter(
    private val scope: CoroutineScope,
    private val onDelete: (VideoFile) -> Unit,
    private val onOpen: (VideoFile) -> Unit,
    private val onShare: (VideoFile) -> Unit,
    private val onQualityChanged: (VideoFile, com.example.webmconverter.model.VideoQuality) -> Unit
) : ListAdapter<VideoFile, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemVideoFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemVideoFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: VideoFile) {
            val context = binding.root.context
            binding.fileName.text = file.name
            binding.fileSize.text = FileUtils.formatSize(file.size)
            binding.fileDuration.text = "• ${FileUtils.formatDuration(file.durationMs)}"

            binding.deleteButton.setOnClickListener { onDelete(file) }
            binding.openButton.setOnClickListener { onOpen(file) }
            binding.shareButton.setOnClickListener { onShare(file) }

            binding.qualityToggleGroup.clearOnButtonCheckedListeners()
            when (file.quality) {
                com.example.webmconverter.model.VideoQuality.LOW -> binding.qualityToggleGroup.check(R.id.btnLow)
                com.example.webmconverter.model.VideoQuality.MEDIUM -> binding.qualityToggleGroup.check(R.id.btnMedium)
                com.example.webmconverter.model.VideoQuality.HIGH -> binding.qualityToggleGroup.check(R.id.btnHigh)
                com.example.webmconverter.model.VideoQuality.LOSSLESS -> binding.qualityToggleGroup.check(R.id.btnLossless)
            }
            
            binding.qualityToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newQuality = when (checkedId) {
                        R.id.btnLow -> com.example.webmconverter.model.VideoQuality.LOW
                        R.id.btnMedium -> com.example.webmconverter.model.VideoQuality.MEDIUM
                        R.id.btnHigh -> com.example.webmconverter.model.VideoQuality.HIGH
                        R.id.btnLossless -> com.example.webmconverter.model.VideoQuality.LOSSLESS
                        else -> com.example.webmconverter.model.VideoQuality.HIGH
                    }
                    if (newQuality != file.quality) {
                        onQualityChanged(file, newQuality)
                    }
                }
            }

            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    FileUtils.getVideoThumbnail(context, file.uri)
                }
                if (bitmap != null) {
                    binding.thumbnailView.setImageBitmap(bitmap)
                } else {
                    binding.thumbnailView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }

            when (file.status) {
                ConversionStatus.WAITING -> {
                    binding.fileStatus.text = context.getString(R.string.status_waiting)
                    binding.fileStatus.setTextColor(context.getColor(R.color.md_theme_light_onPrimaryContainer))
                    binding.itemProgress.visibility = View.INVISIBLE
                    binding.openButton.visibility = View.GONE
                    binding.shareButton.visibility = View.GONE
                    binding.deleteButton.visibility = View.VISIBLE
                    binding.qualityToggleGroup.visibility = View.VISIBLE
                    for (i in 0 until binding.qualityToggleGroup.childCount) {
                        binding.qualityToggleGroup.getChildAt(i).isEnabled = true
                    }
                }
                ConversionStatus.CONVERTING -> {
                    binding.fileStatus.text = context.getString(R.string.status_converting)
                    binding.fileStatus.setTextColor(context.getColor(R.color.md_theme_light_primary))
                    binding.itemProgress.visibility = View.VISIBLE
                    binding.itemProgress.progress = file.progress
                    binding.openButton.visibility = View.GONE
                    binding.shareButton.visibility = View.GONE
                    binding.deleteButton.visibility = View.GONE
                    binding.qualityToggleGroup.visibility = View.VISIBLE
                    for (i in 0 until binding.qualityToggleGroup.childCount) {
                        binding.qualityToggleGroup.getChildAt(i).isEnabled = false
                    }
                }
                ConversionStatus.DONE -> {
                    binding.fileStatus.text = context.getString(R.string.status_done)
                    binding.fileStatus.setTextColor(context.getColor(android.R.color.holo_green_dark))
                    binding.itemProgress.visibility = View.INVISIBLE
                    binding.openButton.visibility = View.VISIBLE
                    binding.shareButton.visibility = View.VISIBLE
                    binding.deleteButton.visibility = View.VISIBLE
                    binding.qualityToggleGroup.visibility = View.GONE
                }
                ConversionStatus.ERROR -> {
                    binding.fileStatus.text = context.getString(R.string.status_error)
                    binding.fileStatus.setTextColor(context.getColor(android.R.color.holo_red_dark))
                    binding.itemProgress.visibility = View.INVISIBLE
                    binding.openButton.visibility = View.GONE
                    binding.shareButton.visibility = View.GONE
                    binding.deleteButton.visibility = View.VISIBLE
                    binding.qualityToggleGroup.visibility = View.VISIBLE
                    for (i in 0 until binding.qualityToggleGroup.childCount) {
                        binding.qualityToggleGroup.getChildAt(i).isEnabled = true
                    }
                }
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<VideoFile>() {
        override fun areItemsTheSame(oldItem: VideoFile, newItem: VideoFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoFile, newItem: VideoFile): Boolean {
            return oldItem == newItem
        }
    }
}
