package com.closetos.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.closetos.app.data.model.Garment
import com.closetos.app.data.model.NormalizationResult
import com.closetos.app.data.model.TravelCapsulePlan
import com.closetos.app.data.model.TravelDayOutfit
import com.closetos.app.data.model.TryOnResult

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

data class WeatherInfo(
    val tempCelsius: Float,
    val description: String,
    val locationName: String
)

expect suspend fun fetchWeatherInfo(): WeatherInfo

@Composable
expect fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit)

fun describeWeatherCode(code: Int): String = when (code) {
    0 -> "Clear & Sunny"
    1, 2, 3 -> "Partly Cloudy"
    45, 48 -> "Foggy Weather"
    51, 53, 55 -> "Light Drizzle"
    61, 63, 65 -> "Rainy Day"
    71, 73, 75 -> "Snowy Day"
    80, 81, 82 -> "Showers"
    95, 96, 99 -> "Thunderstorms"
    else -> "Muted Day"
}

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

expect fun getDigitalTwinSelfiePath(): String?

expect suspend fun renderTryOn(
    personImagePath: String,
    garments: List<Garment>,
    outfitId: String? = null
): TryOnResult?

/** Last error message from a failed try-on API call, if any. */
expect fun getLastTryOnError(): String?

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

/** True when a local image file exists and is large enough to decode. */
expect fun isLocalImageFileValid(path: String): Boolean

