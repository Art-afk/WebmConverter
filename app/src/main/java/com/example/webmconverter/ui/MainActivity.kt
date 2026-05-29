/*
 *  Copyright (c) 2026 Klimov Artem.
 *   All rights reserved.
 *
 *   For inquiries and questions, contact:
 *   Author: Klimov Artem
 *   LinkedIn: https://linkedin.com/in/artem-klimov-java
 */

package com.example.webmconverter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkManager
import com.example.webmconverter.R
import com.example.webmconverter.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: FileAdapter

    private val selectFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        android.util.Log.e("WebMConverter", "Files selected: ${uris.size}")
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        android.util.Log.e("WebMConverter", "Permissions result: $permissions")
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            selectFilesLauncher.launch("video/*")
        } else {
            Snackbar.make(binding.root, "Требуются разрешения для выбора файлов", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("WebMConverter", "onCreate called")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = FileAdapter(
            scope = lifecycleScope,
            onDelete = { viewModel.removeFile(it) },
            onOpen = { file ->
                file.outputPath?.let { path ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(path), "video/mp4")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Snackbar.make(binding.root, "Нет приложения для открытия видео", Snackbar.LENGTH_SHORT).show()
                    }
                }
            },
            onShare = { file ->
                file.outputPath?.let { path ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                    }
                    startActivity(Intent.createChooser(intent, "Поделиться видео"))
                }
            },
            onQualityChanged = { videoFile, videoQuality ->
                viewModel.updateQuality(videoFile, videoQuality)
            }
        )

        binding.recyclerView.adapter = adapter

        binding.fabConvert.setOnClickListener {
            viewModel.startConversion()
        }

        binding.emptyState.setOnClickListener {
            checkPermissionsAndSelectFiles()
        }

        binding.topAppBar.inflateMenu(R.menu.main_menu)
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            android.util.Log.e("WebMConverter", "Menu item clicked: ${menuItem.itemId}")
            when (menuItem.itemId) {
                R.id.action_add -> {
                    checkPermissionsAndSelectFiles()
                    true
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.files.collect { files ->
                        adapter.submitList(files)
                        if (files.isEmpty()) {
                            binding.emptyState.visibility = View.VISIBLE
                            binding.fabConvert.visibility = View.GONE
                        } else {
                            binding.emptyState.visibility = View.GONE
                            binding.fabConvert.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.isConverting.collect { isConverting ->
                        binding.overallProgress.visibility = if (isConverting) View.VISIBLE else View.GONE
                        binding.fabConvert.isEnabled = !isConverting
                    }
                }
            }
        }

        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData("conversion")
            .observe(this) { workInfoList ->
                viewModel.updateWorkStatus(workInfoList)
            }
    }

    private fun checkPermissionsAndSelectFiles() {
        android.util.Log.e("WebMConverter", "checkPermissionsAndSelectFiles called")
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            val granted = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            android.util.Log.e("WebMConverter", "Permission $it: granted=$granted")
            !granted
        }

        if (neededPermissions.isNotEmpty()) {
            android.util.Log.e("WebMConverter", "Requesting permissions: $neededPermissions")
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            android.util.Log.e("WebMConverter", "All permissions granted, launching picker")
            selectFilesLauncher.launch("video/*")
        }
    }
}
