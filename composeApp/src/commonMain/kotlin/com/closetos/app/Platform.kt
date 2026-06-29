package com.closetos.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.closetos.app.data.model.Garment

expect object PlatformStorage {
    fun saveString(key: String, value: String)
    fun loadString(key: String): String?
}

expect fun showToast(message: String)

@Composable
expect fun rememberImageBitmap(path: String): ImageBitmap?

@Composable
expect fun rememberCameraLauncher(onResult: (String?) -> Unit): () -> Unit

@Composable
expect fun rememberImagePickerLauncher(onResult: (List<String>) -> Unit): () -> Unit

expect suspend fun runImageExtraction(path: String): Garment?

expect suspend fun fetchWeatherTemp(): Pair<Float, String>

expect fun getEpochTimeMillis(): Long

