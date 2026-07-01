package com.closetos.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.closetos.app.data.model.Garment
import kotlinx.browser.window
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

actual object PlatformStorage {
    actual fun saveString(key: String, value: String) {
        try {
            window.localStorage.setItem(key, value)
        } catch (e: Exception) {
            println("Storage failed: ${e.message}")
        }
    }

    actual fun loadString(key: String): String? {
        return try {
            window.localStorage.getItem(key)
        } catch (e: Exception) {
            println("Storage load failed: ${e.message}")
            null
        }
    }
}

actual fun showToast(message: String) {
    println("Toast: $message")
}

@Composable
actual fun rememberImageBitmap(path: String): ImageBitmap? {
    return null
}

@Composable
actual fun rememberCameraLauncher(onResult: (String?) -> Unit): () -> Unit {
    return {
        onResult("digital_twin_selfie.jpg")
    }
}

@Composable
actual fun rememberImagePickerLauncher(onResult: (List<String>) -> Unit): () -> Unit {
    return {
        onResult(listOf("gallery_image_shirt_oxford_blue.jpg"))
    }
}

// --- Platform Wasm Extraction Implementation ---

private data class GarmentTemplate(
    val category: String,
    val subcategory: String,
    val material: String,
    val pattern: String,
    val fit: String,
    val silhouette: String,
    val brand: String,
    val price: Double,
    val seasons: List<String>,
    val formalityScore: Float
)

private val garmentTemplates = listOf(
    GarmentTemplate("Top", "Oxford Shirt", "Organic Cotton", "Plain", "Regular", "Button-Down", "Ralph Lauren", 125.0, listOf("Spring", "Autumn", "Winter"), 0.6f),
    GarmentTemplate("Top", "Silk Blouse", "Morus Silk", "Plain", "Fluid", "Draped", "Equipment", 240.0, listOf("Spring", "Summer"), 0.8f),
    GarmentTemplate("Top", "T-Shirt", "Supima Cotton", "Plain", "Regular", "Crewneck", "A.P.C.", 45.0, listOf("Summer", "Spring"), 0.1f),
    GarmentTemplate("Bottom", "Pleated Trousers", "Linen Wool", "Plain", "Relaxed", "Wide-Leg", "Margaret Howell", 180.0, listOf("Spring", "Summer", "Autumn"), 0.7f),
    GarmentTemplate("Bottom", "Selvedge Jeans", "Japanese Denim", "Plain", "Straight", "Classic 5-Pocket", "OrSlow", 260.0, listOf("Autumn", "Winter", "Spring"), 0.2f),
    GarmentTemplate("Outerwear", "Linen Blazer", "Belgian Linen", "Plain", "Tailored", "Single-breasted", "Loro Piana", 320.0, listOf("Summer", "Spring"), 0.75f),
    GarmentTemplate("Outerwear", "Trench Coat", "Gabardine", "Plain", "Classic", "Double-breasted", "Burberry", 850.0, listOf("Spring", "Autumn"), 0.85f),
    GarmentTemplate("Shoes", "Leather Loafers", "Calfskin Leather", "Plain", "Standard", "Penny Loafer", "G.H. Bass", 175.0, listOf("Spring", "Summer", "Autumn"), 0.7f),
    GarmentTemplate("Shoes", "Canvas Sneakers", "Cotton Canvas", "Plain", "Standard", "Low-top", "Common Projects", 290.0, listOf("Summer", "Spring"), 0.1f)
)

actual suspend fun runImageExtraction(
    path: String,
    onProgress: (status: String, progress: Float, label: String) -> Unit
): List<Garment>? {
    onProgress("UPLOAD", 0.1f, "Uploading and validating image...")
    kotlinx.coroutines.delay(400)
    onProgress("GROUNDED_SAM2", 0.25f, "YOLO-World + SAM: Detection + segmentation...")
    kotlinx.coroutines.delay(400)
    onProgress("QUALITY_VALIDATION", 0.4f, "Quality validation...")
    kotlinx.coroutines.delay(400)
    onProgress("NORMALIZATION", 0.55f, "Normalization (GPT Image / FLUX Kontext)...")
    kotlinx.coroutines.delay(400)
    onProgress("FLORENCE_2", 0.7f, "Florence-2: Attribute extraction...")
    kotlinx.coroutines.delay(400)
    onProgress("FASHION_CLIP", 0.85f, "FashionCLIP: Generating embeddings...")
    kotlinx.coroutines.delay(400)
    onProgress("DATABASE_PERSIST", 0.95f, "PostgreSQL + pgvector: Persisting garment...")
    kotlinx.coroutines.delay(400)
    
    val template = garmentTemplates.firstOrNull {
        path.contains(it.subcategory.replace(" ", "").lowercase()) ||
        path.contains(it.category.lowercase()) ||
        path.contains(it.material.lowercase())
    } ?: garmentTemplates.firstOrNull {
        path.contains("shirt") && it.category == "Top"
    } ?: garmentTemplates.firstOrNull {
        (path.contains("pants") || path.contains("trousers") || path.contains("jeans")) && it.category == "Bottom"
    } ?: garmentTemplates.firstOrNull {
        (path.contains("blazer") || path.contains("jacket") || path.contains("coat")) && it.category == "Outerwear"
    } ?: garmentTemplates.firstOrNull {
        (path.contains("loafers") || path.contains("sneakers") || path.contains("shoes")) && it.category == "Shoes"
    } ?: garmentTemplates[0]

    val detectedColorName = "Sky Blue"
    val detectedLabColor = floatArrayOf(80f, -10f, -20f)
    
    val embedding = FloatArray(512).apply {
        for (i in indices) this[i] = (Random.nextDouble() * 0.05).toFloat()
        this[template.category.hashCode().let { if (it < 0) -it else it } % 512] = 0.8f
        this[template.subcategory.hashCode().let { if (it < 0) -it else it } % 512] = 0.6f
        this[detectedColorName.hashCode().let { if (it < 0) -it else it } % 512] = 0.7f
    }
    
    return listOf(Garment(
        category = template.category,
        subcategory = template.subcategory,
        colorName = detectedColorName,
        labColor = detectedLabColor,
        material = template.material,
        pattern = template.pattern,
        fit = template.fit,
        seasons = template.seasons,
        formalityScore = template.formalityScore,
        silhouette = template.silhouette,
        price = template.price,
        brand = template.brand + " (Web Mock)",
        imageUrl = "", // Fall back to template icons
        embedding = embedding
    ))
}

actual suspend fun fetchWeatherTemp(): Pair<Float, String> {
    return Pair(72f, "Clear & Sunny")
}

actual fun defaultBackendUrl(): String = "http://127.0.0.1:8000"

private fun jsDateNow(): Double = js("Date.now()")

actual fun getEpochTimeMillis(): Long {
    return jsDateNow().toLong()
}

actual suspend fun testBackendConnection(baseUrl: String): Boolean {
    val testUrl = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
        baseUrl.trimEnd('/') + "/"
    } else {
        "http://${baseUrl.trimEnd('/')}/"
    }
    return suspendCoroutine { cont ->
        backendPing(testUrl) { ok -> cont.resume(ok) }
    }
}

@JsFun("(url, callback) => fetch(url).then(r => callback(r.status >= 200 && r.status <= 399 || r.status === 404)).catch(() => callback(false))")
private external fun backendPing(url: String, callback: (Boolean) -> Unit)

actual suspend fun runGarmentDetection(path: String): List<com.closetos.app.data.model.DetectedBox>? {
    return listOf(
        com.closetos.app.data.model.DetectedBox(
            bbox = listOf(50, 50, 200, 200),
            label = "TopWear",
            score = 0.9f,
            cropBase64 = ""
        )
    )
}

actual suspend fun normalizeAndFinalizeGarment(
    cropBase64: String,
    label: String,
    sourceImageId: String?
): Garment? {
    return null
}

actual fun decodeBase64ToBitmap(base64: String): ImageBitmap? {
    return null
}
