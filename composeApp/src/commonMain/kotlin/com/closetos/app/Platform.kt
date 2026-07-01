package com.closetos.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.closetos.app.data.model.Garment
import com.closetos.app.data.model.NormalizationResult
import com.closetos.app.data.model.TravelCapsulePlan
import com.closetos.app.data.model.TravelDayOutfit

expect object PlatformStorage {
    fun saveString(key: String, value: String)
    fun loadString(key: String): String?
}

expect fun showToast(message: String)

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun rememberImageBitmap(path: String): ImageBitmap?

@Composable
expect fun rememberCameraLauncher(onResult: (String?) -> Unit): () -> Unit

@Composable
expect fun rememberImagePickerLauncher(onResult: (List<String>) -> Unit): () -> Unit

expect suspend fun runImageExtraction(
    path: String,
    onProgress: (status: String, progress: Float, label: String) -> Unit
): List<Garment>?

expect suspend fun fetchWeatherTemp(): Pair<Float, String>

expect fun getEpochTimeMillis(): Long

expect suspend fun testBackendConnection(baseUrl: String): Boolean

expect fun defaultBackendUrl(): String

expect suspend fun runGarmentDetection(path: String): List<com.closetos.app.data.model.DetectedBox>?

expect suspend fun normalizeGarmentCrop(
    cropBase64: String,
    label: String
): NormalizationResult?

expect suspend fun finalizeGarment(
    imageBase64: String,
    cropBase64: String,
    label: String,
    sourceImageId: String? = null
): Garment?

expect suspend fun cropImageToBase64(
    imagePath: String,
    cropLeft: Float,
    cropTop: Float,
    cropWidth: Float,
    cropHeight: Float
): String?

expect suspend fun saveBase64ImageToFile(base64: String, prefix: String): String?

expect suspend fun generateTravelCapsule(
    destination: String,
    tripDays: Int,
    tempLow: Float,
    tempHigh: Float,
    weatherCondition: String,
    garments: List<Garment>,
    preferredStyles: List<String>
): TravelCapsulePlan?

expect fun decodeBase64ToBitmap(base64: String): ImageBitmap?

