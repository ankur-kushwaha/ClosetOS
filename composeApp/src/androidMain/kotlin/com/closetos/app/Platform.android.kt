package com.closetos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.closetos.app.data.model.Garment
import com.closetos.app.data.model.LaundryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

actual object PlatformStorage {
    var applicationContext: Context? = null
        private set

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    actual fun saveString(key: String, value: String) {
        val context = applicationContext ?: return
        try {
            val file = File(context.filesDir, key)
            file.writeText(value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun loadString(key: String): String? {
        val context = applicationContext ?: return null
        return try {
            val file = File(context.filesDir, key)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

actual fun showToast(message: String) {
    try {
        val context = PlatformStorage.applicationContext
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } else {
            println("Toast log: $message")
        }
    } catch (e: Exception) {
        println("Toast log: $message")
    }
}

@Composable
actual fun rememberImageBitmap(path: String): ImageBitmap? {
    return remember(path) {
        try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
actual fun rememberCameraLauncher(onResult: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                try {
                    val file = File(context.filesDir, "digital_twin_selfie.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    onResult(file.absolutePath)
                } catch (e: Exception) {
                    onResult(null)
                }
            }
        } else {
            onResult(null)
        }
    }
    
    return { cameraLauncher.launch(null) }
}

@Composable
actual fun rememberImagePickerLauncher(onResult: (List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                try {
                    val localPaths = uris.mapIndexed { idx, uri ->
                        val fileName = "gallery_crop_${System.currentTimeMillis()}_$idx.jpg"
                        val file = File(context.filesDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            FileOutputStream(file).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        file.absolutePath
                    }
                    onResult(localPaths)
                } catch (e: Exception) {
                    onResult(emptyList())
                }
            }
        } else {
            onResult(emptyList())
        }
    }
    
    return { galleryLauncher.launch("image/*") }
}

// --- Platform Extraction Implementation ---

actual suspend fun runImageExtraction(path: String): List<Garment>? {
    val context = PlatformStorage.applicationContext ?: return null
    return uploadImageToBackend(context, path)
}

private suspend fun uploadImageToBackend(context: Context, imagePath: String): List<Garment>? {
    return withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext null

            val savedIp = PlatformStorage.loadString("backend_ip")?.trim() ?: "http://10.0.2.2:8000"
            val baseUrl = if (savedIp.startsWith("http://") || savedIp.startsWith("https://")) {
                savedIp
            } else {
                "http://$savedIp"
            }
            val fullUrl = if (baseUrl.endsWith("/digitize")) baseUrl else "${baseUrl.trimEnd('/')}/digitize"

            println("Ingestion: Connecting to backend server: $fullUrl")
            val url = java.net.URL(fullUrl)
            connection = url.openConnection() as java.net.HttpURLConnection
            val boundary = "===Boundary-${System.currentTimeMillis()}==="
            
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("User-Agent", "Android Ingest")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            connection.outputStream.use { out ->
                val writer = java.io.PrintWriter(java.io.OutputStreamWriter(out, "UTF-8"), true)
                writer.append("--$boundary").append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"").append("\r\n")
                writer.append("Content-Type: image/jpeg").append("\r\n")
                writer.append("\r\n").flush()

                file.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
                writer.append("\r\n")
                writer.append("--$boundary--").append("\r\n").flush()
            }

            val responseCode = connection.responseCode
            println("Ingestion: Response code from backend: $responseCode")
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                println("Ingestion: Reading response stream...")
                val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                println("Ingestion: Received response body length: ${jsonResponse.length}")
                
                val root = JSONObject(jsonResponse)
                val garmentsArray = root.getJSONArray("garments")
                val parsedGarments = mutableListOf<Garment>()
                
                for (i in 0 until garmentsArray.length()) {
                    val gObj = garmentsArray.getJSONObject(i)
                    val base64Img = gObj.getString("image_base64")
                    val straightenedBase64Img = gObj.optString("straightened_image_base64", base64Img)
                    val attr = gObj.getJSONObject("attributes")

                    println("Ingestion: Decoding base64 cropped image $i (length: ${base64Img.length})...")
                    val pngBytes = Base64.decode(base64Img, Base64.DEFAULT)
                    val croppedFile = File(context.filesDir, "cropped_server_${System.currentTimeMillis()}_$i.png")
                    FileOutputStream(croppedFile).use { fos ->
                        fos.write(pngBytes)
                    }
                    println("Ingestion: Saved transparent image $i to ${croppedFile.absolutePath}")

                    val straightenedPngBytes = Base64.decode(straightenedBase64Img, Base64.DEFAULT)
                    val straightenedFile = File(context.filesDir, "straightened_server_${System.currentTimeMillis()}_$i.png")
                    FileOutputStream(straightenedFile).use { fos ->
                        fos.write(straightenedPngBytes)
                    }

                    val embedArray = attr.getJSONArray("embedding")
                    val embedding = FloatArray(512) { embedArray.getDouble(it).toFloat() }

                    val labArray = attr.getJSONArray("labColor")
                    val labColor = FloatArray(3) { labArray.getDouble(it).toFloat() }

                    val seasonsArray = attr.getJSONArray("seasons")
                    val seasons = List(seasonsArray.length()) { seasonsArray.getString(it) }

                    val garment = Garment(
                        category = attr.getString("category"),
                        subcategory = attr.getString("subcategory"),
                        colorName = attr.getString("colorName"),
                        labColor = labColor,
                        material = attr.getString("material"),
                        pattern = attr.getString("pattern"),
                        fit = attr.getString("fit"),
                        seasons = seasons,
                        formalityScore = attr.getDouble("formalityScore").toFloat(),
                        silhouette = attr.getString("silhouette"),
                        brand = attr.optString("brand", "Unknown"),
                        price = attr.optDouble("price", 0.0),
                        imageUrl = croppedFile.absolutePath,
                        straightenedImageUrl = straightenedFile.absolutePath,
                        embedding = embedding
                    )
                    parsedGarments.add(garment)
                }
                println("Ingestion: Successfully parsed ${parsedGarments.size} garments from server response")
                parsedGarments
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }
}

private fun cropAndSegmentGarmentImage(context: Context, originalPath: String): String {
    try {
        val file = File(originalPath)
        if (!file.exists()) return originalPath
        val originalBitmap = BitmapFactory.decodeFile(originalPath) ?: return originalPath
        val width = originalBitmap.width
        val height = originalBitmap.height
        val cropWidth = (width * 0.75f).toInt()
        val cropHeight = (height * 0.75f).toInt()
        val startX = (width - cropWidth) / 2
        val startY = (height - cropHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(originalBitmap, startX, startY, cropWidth, cropHeight)
        val maskedBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val path = Path()
        val rect = RectF(0f, 0f, cropWidth.toFloat(), cropHeight.toFloat())
        path.addRoundRect(rect, cropWidth * 0.20f, cropHeight * 0.20f, Path.Direction.CW)

        canvas.clipPath(path)
        canvas.drawBitmap(croppedBitmap, 0f, 0f, paint)

        val croppedFileName = "cropped_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}.png"
        val croppedFile = File(context.filesDir, croppedFileName)
        FileOutputStream(croppedFile).use { out ->
            maskedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        originalBitmap.recycle()
        croppedBitmap.recycle()
        maskedBitmap.recycle()

        return croppedFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return originalPath
    }
}

private fun calculateAverageColor(bitmapPath: String): Int {
    try {
        val bitmap = BitmapFactory.decodeFile(bitmapPath) ?: return android.graphics.Color.GRAY
        var redBucket = 0L
        var greenBucket = 0L
        var blueBucket = 0L
        var pixelCount = 0L

        val step = 10
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val c = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(c)
                if (alpha > 50) {
                    redBucket += android.graphics.Color.red(c)
                    greenBucket += android.graphics.Color.green(c)
                    blueBucket += android.graphics.Color.blue(c)
                    pixelCount++
                }
            }
        }
        bitmap.recycle()
        if (pixelCount == 0L) return android.graphics.Color.GRAY

        val r = (redBucket / pixelCount).toInt()
        val g = (greenBucket / pixelCount).toInt()
        val b = (blueBucket / pixelCount).toInt()
        return android.graphics.Color.rgb(r, g, b)
    } catch (e: Exception) {
        return android.graphics.Color.GRAY
    }
}

private fun getColorNameFromRgb(color: Int): String {
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)

    return when {
        r > 220 && g > 220 && b > 220 -> "Ivory White"
        r < 50 && g < 50 && b < 50 -> "Midnight Black"
        r > 130 && g > 130 && b > 130 -> "Classic Gray"
        r > 150 && g < 80 && b < 80 -> "Crimson Red"
        r < 80 && g > 130 && b < 80 -> "Forest Green"
        r < 80 && g < 80 && b > 150 -> "Ocean Blue"
        r > 200 && g > 180 && b < 80 -> "Sun Yellow"
        r > 180 && g > 110 && b < 60 -> "Terracotta Orange"
        r > 110 && g > 80 && b < 50 -> "Chestnut Brown"
        r > 120 && g < 60 && b > 120 -> "Royal Purple"
        r > 90 && g > 110 && b > 90 -> "Sage Olive"
        else -> "Desert Khaki"
    }
}

private fun rgbToLab(rgb: Int): FloatArray {
    val r = android.graphics.Color.red(rgb) / 255f
    val g = android.graphics.Color.green(rgb) / 255f
    val b = android.graphics.Color.blue(rgb) / 255f

    val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) * 100f
    val a = (r - g) * 100f
    val bb = (g - b) * 100f
    return floatArrayOf(l, a, bb)
}

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

actual suspend fun fetchWeatherTemp(): Pair<Float, String> {
    return withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=51.5074&longitude=-0.1278&current_weather=true")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObj = org.json.JSONObject(response)
            val currentWeather = jsonObj.getJSONObject("current_weather")
            val tempC = currentWeather.getDouble("temperature").toFloat()
            val tempF = (tempC * 9f / 5f) + 32f
            val code = currentWeather.getInt("weathercode")
            val desc = when (code) {
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
            Pair(tempF, desc)
        } catch (e: Exception) {
            Pair(74f, "Clear & Sunny")
        }
    }
}

actual fun getEpochTimeMillis(): Long = System.currentTimeMillis()
