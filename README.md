# WebM to MP4 Converter

A fully-featured Android application for converting video files from WebM to MP4 format. The app utilizes the `ffmpeg-kit-android` library for video processing and is built with the MVVM architecture. It supports Android 8.0+ (API 26) with full optimization for modern OS versions (up to Android 15, API 35).

## Features
* **Multiple File Selection**: Batch add files using the standard `ActivityResultContracts.GetMultipleContents` picker.
* **Background Conversion**: Uses `WorkManager` and Foreground Services (for Android O+) to ensure conversion continues even if the app is minimized.
* **FFmpeg Kit**: Integration with `com.arthenica:ffmpeg-kit-android:6.0-2.LTS` for reliable encoding (`mpeg4`, `aac`).
* **Modern UI**: Material Design 3, Dynamic Color support, and adaptive layouts for tablets and phones. Uses Coroutines (`StateFlow`) for state management in `MainViewModel`.
* **Secure Storage**: Supports Scoped Storage (API 29+) via `MediaStore` and standard file saving for older versions (API 26-28).
* **Progress Notifications**: Tracks conversion progress both in the UI (via ProgressBar) and through system notifications.
* **Share/Open Integration**: Immediately watch the converted video in your favorite media player or share it.

## Architecture
* **MVVM**: Clear separation between UI and business logic.
* **Coroutines & Flow**: Asynchronous tasks and UI data flow management.
* **WorkManager**: Queue management for long-running background tasks.

## Building the Project
This project uses Gradle Kotlin DSL (`build.gradle.kts`).
1. Clone the repository.
2. Open it in Android Studio (Ladybug or a newer version).
3. Sync Gradle projects.
4. Run the app using the "Run" button or execute:
   ```bash
   ./gradlew assembleDebug
   ```

## Permissions
The app requests:
* `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` to select source files.
* `POST_NOTIFICATIONS` to display progress in the status bar.
* `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` for background video conversion.

## License
Distributed under the MIT License. See `LICENSE` for more information.
