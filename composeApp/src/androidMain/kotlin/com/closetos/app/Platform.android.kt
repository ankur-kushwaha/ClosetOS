package com.closetos.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.closetos.app.data.model.Garment
import com.closetos.app.data.model.LaundryStatus
import com.closetos.app.data.model.NormalizationResult
import com.closetos.app.data.model.TravelCapsulePlan
import com.closetos.app.data.model.TravelDayOutfit
import com.closetos.app.data.model.TryOnResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
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
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
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
actual suspend fun runImageExtraction(
    path: String,
    onProgress: (status: String, progress: Float, label: String) -> Unit
): List<Garment>? {
    val context = PlatformStorage.applicationContext ?: return null
    return uploadImageToBackend(context, path, onProgress)
}

private suspend fun uploadImageToBackend(
    context: Context,
    imagePath: String,
    onProgress: (status: String, progress: Float, label: String) -> Unit
): List<Garment>? {
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
            
            val startUrl = "${baseUrl.trimEnd('/')}/digitize/start"
            println("Ingestion: Starting extraction job at: $startUrl")
            val url = java.net.URL(startUrl)
            connection = url.openConnection() as java.net.HttpURLConnection
            val boundary = "===Boundary-${System.currentTimeMillis()}==="
            
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
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

            val startResponseCode = connection.responseCode
            if (startResponseCode != java.net.HttpURLConnection.HTTP_OK) {
                println("Ingestion: Start job failed with code $startResponseCode")
                return@withContext null
            }

            val startJsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
            val startRoot = JSONObject(startJsonResponse)
            val jobId = startRoot.getString("job_id")
            println("Ingestion: Successfully started job: $jobId")
            connection.disconnect()

            var isRunning = true
            var retryCount = 0
            val maxRetries = 180 // 180 * 1000ms = 3 minutes max wait time
            var finalGarments: List<Garment>? = null

            while (isRunning && retryCount < maxRetries) {
                delay(1000)
                retryCount++

                val statusUrl = "${baseUrl.trimEnd('/')}/digitize/jobs/$jobId"
                val pollUrl = java.net.URL(statusUrl)
                var pollConn: java.net.HttpURLConnection? = null
                try {
                    pollConn = pollUrl.openConnection() as java.net.HttpURLConnection
                    pollConn.requestMethod = "GET"
                    pollConn.connectTimeout = 5000
                    pollConn.readTimeout = 5000

                    val code = pollConn.responseCode
                    if (code == java.net.HttpURLConnection.HTTP_OK) {
                        val pollJson = pollConn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(pollJson)
                        val status = root.getString("status")
                        
                        val currentStep = root.optString("current_step", "PRE_FLIGHT")
                        val progress = root.optDouble("progress", 0.0).toFloat()
                        
                        val stepsArray = root.optJSONArray("steps")
                        var stepLabel = "Processing clothing item..."
                        if (stepsArray != null) {
                            for (j in 0 until stepsArray.length()) {
                                val sObj = stepsArray.getJSONObject(j)
                                if (sObj.getString("name") == currentStep) {
                                    stepLabel = sObj.optString("label", stepLabel)
                                    break
                                }
                            }
                        }

                        println("Ingestion poll: $currentStep ($progress) - $stepLabel")
                        
                        onProgress(currentStep, progress, stepLabel)

                        if (status == "completed") {
                            isRunning = false
                            val garmentsArray = root.getJSONArray("garments")
                            val parsedGarments = mutableListOf<Garment>()
                            
                            for (i in 0 until garmentsArray.length()) {
                                val gObj = garmentsArray.getJSONObject(i)
                                val base64Img = gObj.getString("image_base64")
                                val straightenedBase64Img = gObj.optString("straightened_image_base64", base64Img)
                                val attr = gObj.getJSONObject("attributes")

                                val pngBytes = Base64.decode(base64Img, Base64.DEFAULT)
                                val croppedFile = File(context.filesDir, "cropped_server_${System.currentTimeMillis()}_$i.png")
                                FileOutputStream(croppedFile).use { fos ->
                                    fos.write(pngBytes)
                                }

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
                            finalGarments = parsedGarments
                        } else if (status == "failed") {
                            isRunning = false
                            val err = root.optString("error", "Unknown pipeline error")
                            println("Ingestion poll: Job failed with error: $err")
                        }
                    } else {
                        println("Ingestion poll: Server returned code $code")
                    }
                } catch (e: Exception) {
                    println("Ingestion poll: Connection error: ${e.message}")
                } finally {
                    pollConn?.disconnect()
                }
            }
            finalGarments
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

@Composable
actual fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit) {
    val context = LocalContext.current
    var resolved by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (!resolved) {
            resolved = true
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            onResult(granted)
        }
    }

    LaunchedEffect(Unit) {
        if (resolved) return@LaunchedEffect
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            resolved = true
            onResult(true)
        } else if (!requested) {
            requested = true
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}

private suspend fun getCurrentCoordinates(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    val context = PlatformStorage.applicationContext ?: return@withContext null
    val fineGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return@withContext null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    for (provider in providers) {
        try {
            val last = locationManager.getLastKnownLocation(provider)
            if (last != null && (last.latitude != 0.0 || last.longitude != 0.0)) {
                return@withContext Pair(last.latitude, last.longitude)
            }
        } catch (_: SecurityException) {
        }
    }

    suspendCancellableCoroutine { cont ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (cont.isActive) cont.resume(Pair(location.latitude, location.longitude))
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        try {
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        cont.invokeOnCancellation {
            locationManager.removeUpdates(listener)
        }
    }
}

private fun httpGet(urlString: String): String? {
    return try {
        val url = java.net.URL(urlString)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}

private fun resolveLocationName(lat: Double, lon: Double): String {
    val response = httpGet(
        "https://geocoding-api.open-meteo.com/v1/reverse?latitude=$lat&longitude=$lon&language=en&count=1"
    ) ?: return "Unknown location"
    return try {
        val json = JSONObject(response)
        val results = json.optJSONArray("results") ?: return "Unknown location"
        if (results.length() == 0) return "Unknown location"
        val place = results.getJSONObject(0)
        val name = place.optString("name", "")
        val admin1 = place.optString("admin1", "")
        when {
            name.isNotEmpty() && admin1.isNotEmpty() -> "$name, $admin1"
            name.isNotEmpty() -> name
            else -> place.optString("country", "Unknown location")
        }
    } catch (_: Exception) {
        "Unknown location"
    }
}

private fun fetchWeatherForCoordinates(lat: Double, lon: Double, locationName: String): WeatherInfo {
    val response = httpGet(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
    )
    if (response == null) {
        return WeatherInfo(23f, "Clear & Sunny", locationName)
    }
    return try {
        val jsonObj = JSONObject(response)
        val currentWeather = jsonObj.getJSONObject("current_weather")
        val tempC = currentWeather.getDouble("temperature").toFloat()
        val code = currentWeather.getInt("weathercode")
        WeatherInfo(tempC, describeWeatherCode(code), locationName)
    } catch (_: Exception) {
        WeatherInfo(23f, "Clear & Sunny", locationName)
    }
}

actual suspend fun fetchWeatherInfo(): WeatherInfo = withContext(Dispatchers.IO) {
    val coords = getCurrentCoordinates()
    if (coords != null) {
        val locationName = resolveLocationName(coords.first, coords.second)
        return@withContext fetchWeatherForCoordinates(coords.first, coords.second, locationName)
    }
    fetchWeatherForCoordinates(51.5074, -0.1278, "Location unavailable")
}

actual fun defaultBackendUrl(): String = "http://10.0.2.2:8000"

actual fun getEpochTimeMillis(): Long = System.currentTimeMillis()

actual suspend fun testBackendConnection(baseUrl: String): Boolean {
    return kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val testUrl = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
                baseUrl
            } else {
                "http://$baseUrl"
            }
            val url = java.net.URL(testUrl.trimEnd('/') + "/")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399 || code == 404
        } catch (e: Exception) {
            false
        }
    }
}

actual suspend fun runGarmentDetection(path: String): List<com.closetos.app.data.model.DetectedBox>? {
    val context = PlatformStorage.applicationContext ?: return null
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val file = File(path)
            if (!file.exists()) return@withContext null

            val savedIp = PlatformStorage.loadString("backend_ip")?.trim() ?: "http://10.0.2.2:8000"
            val baseUrl = if (savedIp.startsWith("http://") || savedIp.startsWith("https://")) {
                savedIp
            } else {
                "http://$savedIp"
            }
            
            val detectUrl = "${baseUrl.trimEnd('/')}/yolo-world/detect"
            val url = java.net.URL(detectUrl)
            connection = url.openConnection() as java.net.HttpURLConnection
            val boundary = "===Boundary-${System.currentTimeMillis()}==="
            
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
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

            val code = connection.responseCode
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(jsonResponse)
            val sourceImageId = root.optString("source_image_id", "")
            val bboxesArray = root.getJSONArray("bboxes")
            val labelsArray = root.getJSONArray("labels")
            val scoresArray = root.getJSONArray("scores")
            val cropsArray = root.getJSONArray("crops_base64")
            
            val list = mutableListOf<com.closetos.app.data.model.DetectedBox>()
            for (i in 0 until bboxesArray.length()) {
                val bboxJson = bboxesArray.getJSONArray(i)
                val bbox = listOf(
                    bboxJson.getInt(0),
                    bboxJson.getInt(1),
                    bboxJson.getInt(2),
                    bboxJson.getInt(3)
                )
                val label = labelsArray.getString(i)
                val score = scoresArray.getDouble(i).toFloat()
                val cropBase64 = cropsArray.getString(i)
                
                list.add(
                    com.closetos.app.data.model.DetectedBox(
                        bbox = bbox,
                        label = label,
                        score = score,
                        cropBase64 = cropBase64,
                        sourceImageId = if (sourceImageId.isNotEmpty()) sourceImageId else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }
}

actual suspend fun normalizeGarmentCrop(
    cropBase64: String,
    label: String
): NormalizationResult? {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var normalizeConn: java.net.HttpURLConnection? = null
        try {
            val savedIp = PlatformStorage.loadString("backend_ip")?.trim() ?: "http://10.0.2.2:8000"
            val baseUrl = if (savedIp.startsWith("http://") || savedIp.startsWith("https://")) {
                savedIp
            } else {
                "http://$savedIp"
            }

            val normalizeUrl = "${baseUrl.trimEnd('/')}/yolo-world/gpt-normalize"
            val url1 = java.net.URL(normalizeUrl)
            normalizeConn = url1.openConnection() as java.net.HttpURLConnection
            normalizeConn.doOutput = true
            normalizeConn.doInput = true
            normalizeConn.useCaches = false
            normalizeConn.requestMethod = "POST"
            normalizeConn.connectTimeout = 120_000
            normalizeConn.readTimeout = 120_000
            normalizeConn.setRequestProperty("Content-Type", "application/json")

            val normPayload = org.json.JSONObject()
            normPayload.put("crop_base64", cropBase64)
            normPayload.put("label", label)

            normalizeConn.outputStream.use { out ->
                out.write(normPayload.toString().toByteArray(charset("UTF-8")))
                out.flush()
            }

            val code1 = normalizeConn.responseCode
            if (code1 != java.net.HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val res1 = normalizeConn.inputStream.bufferedReader().use { it.readText() }
            val root1 = org.json.JSONObject(res1)
            val normalizedBase64 = root1.getString("image_base64")
            val provider = root1.optString("provider", "gpt-image")
            NormalizationResult(imageBase64 = normalizedBase64, provider = provider)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            normalizeConn?.disconnect()
        }
    }
}

actual suspend fun finalizeGarment(
    imageBase64: String,
    cropBase64: String,
    label: String,
    sourceImageId: String?
): Garment? {
    val context = PlatformStorage.applicationContext ?: return null
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var finalizeConn: java.net.HttpURLConnection? = null
        try {
            val savedIp = PlatformStorage.loadString("backend_ip")?.trim() ?: "http://10.0.2.2:8000"
            val baseUrl = if (savedIp.startsWith("http://") || savedIp.startsWith("https://")) {
                savedIp
            } else {
                "http://$savedIp"
            }

            val finalizeUrl = "${baseUrl.trimEnd('/')}/yolo-world/finalize"
            val url2 = java.net.URL(finalizeUrl)
            finalizeConn = url2.openConnection() as java.net.HttpURLConnection
            finalizeConn.doOutput = true
            finalizeConn.doInput = true
            finalizeConn.useCaches = false
            finalizeConn.requestMethod = "POST"
            finalizeConn.connectTimeout = 90_000
            finalizeConn.readTimeout = 90_000
            finalizeConn.setRequestProperty("Content-Type", "application/json")

            val finalPayload = org.json.JSONObject()
            finalPayload.put("image_base64", imageBase64)
            finalPayload.put("crop_base64", cropBase64)
            finalPayload.put("label", label)
            if (sourceImageId != null) {
                finalPayload.put("source_image_id", sourceImageId)
            }

            finalizeConn.outputStream.use { out ->
                out.write(finalPayload.toString().toByteArray(charset("UTF-8")))
                out.flush()
            }

            val code2 = finalizeConn.responseCode
            if (code2 != java.net.HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val res2 = finalizeConn.inputStream.bufferedReader().use { it.readText() }
            val root2 = org.json.JSONObject(res2)

            val base64Img = root2.getString("image_base64")
            val straightenedBase64Img = root2.optString("straightened_image_base64", base64Img)
            val attr = root2.getJSONObject("attributes")

            val pngBytes = android.util.Base64.decode(base64Img, android.util.Base64.DEFAULT)
            val croppedFile = File(context.filesDir, "cropped_server_${System.currentTimeMillis()}.png")
            FileOutputStream(croppedFile).use { fos ->
                fos.write(pngBytes)
            }

            val cropPngBytes = android.util.Base64.decode(cropBase64, android.util.Base64.DEFAULT)
            val rawCropFile = File(context.filesDir, "raw_crop_${System.currentTimeMillis()}.png")
            FileOutputStream(rawCropFile).use { fos ->
                fos.write(cropPngBytes)
            }

            val straightenedPngBytes = android.util.Base64.decode(straightenedBase64Img, android.util.Base64.DEFAULT)
            val straightenedFile = File(context.filesDir, "straightened_server_${System.currentTimeMillis()}.png")
            FileOutputStream(straightenedFile).use { fos ->
                fos.write(straightenedPngBytes)
            }

            val embedArray = attr.getJSONArray("embedding")
            val embedding = FloatArray(512) { embedArray.getDouble(it).toFloat() }

            val labArray = attr.getJSONArray("labColor")
            val labColor = FloatArray(3) { labArray.getDouble(it).toFloat() }

            val seasonsArray = attr.getJSONArray("seasons")
            val seasons = List(seasonsArray.length()) { seasonsArray.getString(it) }

            Garment(
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
                brand = attr.optString("brand", "Inferred"),
                price = attr.optDouble("price", 0.0),
                imageUrl = rawCropFile.absolutePath,
                straightenedImageUrl = straightenedFile.absolutePath,
                embedding = embedding
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            finalizeConn?.disconnect()
        }
    }
}

actual suspend fun cropImageToBase64(
    imagePath: String,
    cropLeft: Float,
    cropTop: Float,
    cropWidth: Float,
    cropHeight: Float
): String? {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext null
            val left = (cropLeft * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val top = (cropTop * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val width = (cropWidth * bitmap.width).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - left)
            val height = (cropHeight * bitmap.height).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - top)
            val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
            val stream = java.io.ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

actual suspend fun saveBase64ImageToFile(base64: String, prefix: String): String? {
    val context = PlatformStorage.applicationContext ?: return null
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val pngBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { fos ->
                fos.write(pngBytes)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

private fun resolveBackendBaseUrl(): String {
    val savedIp = PlatformStorage.loadString("backend_ip")?.trim() ?: "http://10.0.2.2:8000"
    return if (savedIp.startsWith("http://") || savedIp.startsWith("https://")) {
        savedIp.trimEnd('/')
    } else {
        "http://$savedIp".trimEnd('/')
    }
}

actual suspend fun generateTravelCapsule(
    destination: String,
    tripDays: Int,
    tempLow: Float,
    tempHigh: Float,
    weatherCondition: String,
    garments: List<Garment>,
    preferredStyles: List<String>
): TravelCapsulePlan? {
    return withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL("${resolveBackendBaseUrl()}/travel/capsule")
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 90_000
            connection.setRequestProperty("Content-Type", "application/json")

            val garmentsArray = org.json.JSONArray()
            for (g in garments) {
                val gObj = org.json.JSONObject()
                gObj.put("id", g.id)
                gObj.put("category", g.category)
                gObj.put("subcategory", g.subcategory)
                gObj.put("colorName", g.colorName)
                gObj.put("material", g.material)
                gObj.put("pattern", g.pattern)
                gObj.put("fit", g.fit)
                gObj.put("seasons", org.json.JSONArray(g.seasons))
                gObj.put("formalityScore", g.formalityScore.toDouble())
                gObj.put("laundryStatus", g.laundryStatus.name)
                gObj.put("wearCount", g.wearCount)
                gObj.put("brand", g.brand)
                garmentsArray.put(gObj)
            }

            val payload = org.json.JSONObject()
            payload.put("destination", destination)
            payload.put("trip_days", tripDays)
            payload.put("temp_low_f", tempLow.toDouble())
            payload.put("temp_high_f", tempHigh.toDouble())
            payload.put("weather_condition", weatherCondition)
            payload.put("garments", garmentsArray)
            payload.put("preferred_styles", org.json.JSONArray(preferredStyles))

            connection.outputStream.use { out ->
                out.write(payload.toString().toByteArray(charset("UTF-8")))
                out.flush()
            }

            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(response)

            val capsuleIds = mutableListOf<String>()
            val capsuleArray = root.getJSONArray("capsule_garment_ids")
            for (i in 0 until capsuleArray.length()) {
                capsuleIds.add(capsuleArray.getString(i))
            }

            val dailyOutfits = mutableListOf<TravelDayOutfit>()
            val dailyArray = root.getJSONArray("daily_outfits")
            for (i in 0 until dailyArray.length()) {
                val dayObj = dailyArray.getJSONObject(i)
                val garmentIds = mutableListOf<String>()
                val idsArray = dayObj.getJSONArray("garment_ids")
                for (j in 0 until idsArray.length()) {
                    garmentIds.add(idsArray.getString(j))
                }
                dailyOutfits.add(
                    TravelDayOutfit(
                        day = dayObj.getInt("day"),
                        garmentIds = garmentIds,
                        reason = dayObj.optString("reason", "")
                    )
                )
            }

            TravelCapsulePlan(
                capsuleGarmentIds = capsuleIds,
                dailyOutfits = dailyOutfits,
                packingNotes = root.optString("packing_notes", ""),
                provider = root.optString("provider", "backend")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }
}

actual fun decodeBase64ToBitmap(base64: String): ImageBitmap? {
    return try {
        val decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
        decodedByte.asImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

actual fun getDigitalTwinSelfiePath(): String? {
    val context = PlatformStorage.applicationContext ?: return null
    val file = File(context.filesDir, "digital_twin_selfie.jpg")
    return if (file.exists()) file.absolutePath else null
}

private fun fileToBase64(path: String): String? {
    return try {
        val file = File(path)
        if (!file.exists()) return null
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private var lastTryOnError: String? = null

actual fun getLastTryOnError(): String? = lastTryOnError

actual suspend fun renderTryOn(
    personImagePath: String,
    garments: List<Garment>,
    outfitId: String?
): TryOnResult? {
    return withContext(Dispatchers.IO) {
        lastTryOnError = null
        var connection: java.net.HttpURLConnection? = null
        try {
            val personBase64 = fileToBase64(personImagePath) ?: return@withContext null

            val garmentsArray = org.json.JSONArray()
            for (g in garments) {
                val imagePath = g.straightenedImageUrl.ifBlank { g.imageUrl }
                val imageBase64 = fileToBase64(imagePath) ?: continue
                val gObj = org.json.JSONObject()
                gObj.put("id", g.id)
                gObj.put("category", g.category)
                gObj.put("subcategory", g.subcategory)
                gObj.put("colorName", g.colorName)
                gObj.put("image_base64", imageBase64)
                garmentsArray.put(gObj)
            }

            if (garmentsArray.length() == 0) {
                return@withContext null
            }

            val payload = org.json.JSONObject()
            payload.put("person_image_base64", personBase64)
            payload.put("garments", garmentsArray)
            if (outfitId != null) {
                payload.put("outfit_id", outfitId)
            }

            val url = java.net.URL("${resolveBackendBaseUrl()}/try-on/render")
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 180_000
            connection.setRequestProperty("Content-Type", "application/json")

            connection.outputStream.use { out ->
                out.write(payload.toString().toByteArray(charset("UTF-8")))
                out.flush()
            }

            val code = connection.responseCode
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                lastTryOnError = try {
                    org.json.JSONObject(err ?: "").optString("detail", err ?: "Try-on failed ($code)")
                } catch (_: Exception) {
                    err ?: "Try-on failed ($code)"
                }
                println("Try-on failed ($code): $lastTryOnError")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(response)
            TryOnResult(
                imageBase64 = root.getString("image_base64"),
                provider = root.optString("provider", "gemini-3.1-flash-lite-image")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }
}
