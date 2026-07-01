package com.closetos.app.data.repository

import android.content.Context
import com.closetos.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

object ClosetRepository {

    private val _garments = MutableStateFlow<List<Garment>>(emptyList())
    val garments: StateFlow<List<Garment>> = _garments.asStateFlow()

    private val _ingestionQueue = MutableStateFlow<List<IngestionItem>>(emptyList())
    val ingestionQueue: StateFlow<List<IngestionItem>> = _ingestionQueue.asStateFlow()

    private val _wearHistory = MutableStateFlow<List<WearEvent>>(emptyList())
    val wearHistory: StateFlow<List<WearEvent>> = _wearHistory.asStateFlow()

    private val _userTaste = MutableStateFlow(UserTaste())
    val userTaste: StateFlow<UserTaste> = _userTaste.asStateFlow()

    private val _tripPlans = MutableStateFlow<List<TripPlan>>(emptyList())
    val tripPlans: StateFlow<List<TripPlan>> = _tripPlans.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadData()
    }

    private fun loadData() {
        val context = appContext ?: return
        val file = File(context.filesDir, "closet_os_data.json")
        if (!file.exists()) {
            saveData()
            return
        }

        try {
            val jsonString = file.readText()
            val root = JSONObject(jsonString)

            // Load Garments
            val garmentsArray = root.getJSONArray("garments")
            val garmentsList = mutableListOf<Garment>()
            for (i in 0 until garmentsArray.length()) {
                val gObj = garmentsArray.getJSONObject(i)
                val id = gObj.getString("id")
                if (id.startsWith("g_")) continue
                val labJson = gObj.getJSONArray("labColor")
                val labColor = FloatArray(3) { labJson.getDouble(it).toFloat() }
                
                val embedJson = gObj.getJSONArray("embedding")
                val embedding = FloatArray(512) { embedJson.optDouble(it, 0.0).toFloat() }
                
                val seasonsJson = gObj.getJSONArray("seasons")
                val seasons = List(seasonsJson.length()) { seasonsJson.getString(it) }

                garmentsList.add(
                    Garment(
                        id = gObj.getString("id"),
                        category = gObj.getString("category"),
                        subcategory = gObj.getString("subcategory"),
                        colorName = gObj.getString("colorName"),
                        labColor = labColor,
                        material = gObj.getString("material"),
                        pattern = gObj.getString("pattern"),
                        fit = gObj.getString("fit"),
                        seasons = seasons,
                        formalityScore = gObj.getDouble("formalityScore").toFloat(),
                        silhouette = gObj.getString("silhouette"),
                        price = gObj.optDouble("price", 0.0),
                        brand = gObj.optString("brand", "Unknown"),
                        imageUrl = gObj.optString("imageUrl", ""),
                        straightenedImageUrl = gObj.optString("straightenedImageUrl", ""),
                        embedding = embedding,
                        costPerWear = gObj.optDouble("costPerWear", 0.0),
                        wearCount = gObj.optInt("wearCount", 0),
                        laundryStatus = LaundryStatus.valueOf(gObj.optString("laundryStatus", "CLEAN")),
                        dateAdded = gObj.optLong("dateAdded", System.currentTimeMillis())
                    )
                )
            }
            _garments.value = garmentsList

            // Load Taste
            val tasteObj = root.optJSONObject("userTaste")
            if (tasteObj != null) {
                val prefStylesJson = tasteObj.getJSONArray("preferredStyles")
                val prefStyles = List(prefStylesJson.length()) { prefStylesJson.getString(it) }
                val colorsAvoidedJson = tasteObj.getJSONArray("colorsAvoided")
                val colorsAvoided = List(colorsAvoidedJson.length()) { colorsAvoidedJson.getString(it) }
                val prefFits = tasteObj.optJSONArray("preferredFits")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList()
                val occasionsJson = tasteObj.getJSONArray("occasions")
                val occasions = List(occasionsJson.length()) { occasionsJson.getString(it) }
                val tVecJson = tasteObj.getJSONArray("tasteVector")
                val tasteVector = FloatArray(512) { tVecJson.optDouble(it, 0.0).toFloat() }

                _userTaste.value = UserTaste(
                    preferredStyles = prefStyles,
                    colorsAvoided = colorsAvoided,
                    preferredFits = prefFits,
                    occasions = occasions,
                    tasteVector = tasteVector
                )
            }

            // Load Wear History
            val wearHistoryArray = root.optJSONArray("wearHistory")
            if (wearHistoryArray != null) {
                val wearList = mutableListOf<WearEvent>()
                for (i in 0 until wearHistoryArray.length()) {
                    val wObj = wearHistoryArray.getJSONObject(i)
                    val gWornJson = wObj.getJSONArray("garmentsWornIds")
                    val gWorn = List(gWornJson.length()) { gWornJson.getString(it) }

                    wearList.add(
                        WearEvent(
                            id = wObj.getString("id"),
                            date = wObj.getLong("date"),
                            outfitId = wObj.getString("outfitId"),
                            garmentsWornIds = gWorn,
                            loved = wObj.optBoolean("loved", false),
                            skipped = wObj.optBoolean("skipped", false),
                            selfieUrl = if (wObj.isNull("selfieUrl")) null else wObj.optString("selfieUrl")
                        )
                    )
                }
                _wearHistory.value = wearList
            }

            // Load Trip Plans
            val tripPlansArray = root.optJSONArray("tripPlans")
            if (tripPlansArray != null) {
                val plansList = mutableListOf<TripPlan>()
                for (i in 0 until tripPlansArray.length()) {
                    val pObj = tripPlansArray.getJSONObject(i)
                    val id = pObj.getString("id")
                    if (id.startsWith("trip_")) continue
                    plansList.add(
                        TripPlan(
                            id = id,
                            destination = pObj.getString("destination"),
                            startDate = pObj.getLong("startDate"),
                            endDate = pObj.getLong("endDate"),
                            tempLow = pObj.getDouble("tempLow").toFloat(),
                            tempHigh = pObj.getDouble("tempHigh").toFloat(),
                            weatherCondition = pObj.getString("weatherCondition")
                        )
                    )
                }
                _tripPlans.value = plansList
            }
        } catch (e: Exception) {
            e.printStackTrace()
            saveData()
        }
    }

    fun saveData() {
        val context = appContext ?: return
        val file = File(context.filesDir, "closet_os_data.json")
        try {
            val root = JSONObject()

            // Save Garments
            val garmentsArray = JSONArray()
            for (g in _garments.value) {
                val gObj = JSONObject().apply {
                    put("id", g.id)
                    put("category", g.category)
                    put("subcategory", g.subcategory)
                    put("colorName", g.colorName)
                    
                    val labJson = JSONArray().apply { g.labColor.forEach { put(it.toDouble()) } }
                    put("labColor", labJson)
                    
                    put("material", g.material)
                    put("pattern", g.pattern)
                    put("fit", g.fit)
                    
                    val seasonsJson = JSONArray().apply { g.seasons.forEach { put(it) } }
                    put("seasons", seasonsJson)
                    
                    put("formalityScore", g.formalityScore.toDouble())
                    put("silhouette", g.silhouette)
                    put("price", g.price)
                    put("brand", g.brand)
                    put("imageUrl", g.imageUrl)
                    put("straightenedImageUrl", g.straightenedImageUrl)
                    
                    val embedJson = JSONArray().apply { g.embedding.forEach { put(it.toDouble()) } }
                    put("embedding", embedJson)
                    
                    put("costPerWear", g.costPerWear)
                    put("wearCount", g.wearCount)
                    put("laundryStatus", g.laundryStatus.name)
                    put("dateAdded", g.dateAdded)
                }
                garmentsArray.put(gObj)
            }
            root.put("garments", garmentsArray)

            // Save Taste
            val taste = _userTaste.value
            val tasteObj = JSONObject().apply {
                val prefStylesJson = JSONArray().apply { taste.preferredStyles.forEach { put(it) } }
                put("preferredStyles", prefStylesJson)
                val colorsAvoidedJson = JSONArray().apply { taste.colorsAvoided.forEach { put(it) } }
                put("colorsAvoided", colorsAvoidedJson)
                val prefFitsJson = JSONArray().apply { taste.preferredFits.forEach { put(it) } }
                put("preferredFits", prefFitsJson)
                val occasionsJson = JSONArray().apply { taste.occasions.forEach { put(it) } }
                put("occasions", occasionsJson)
                val tVecJson = JSONArray().apply { taste.tasteVector.forEach { put(it.toDouble()) } }
                put("tasteVector", tVecJson)
            }
            root.put("userTaste", tasteObj)

            // Save Wear History
            val wearHistoryArray = JSONArray()
            for (w in _wearHistory.value) {
                val wObj = JSONObject().apply {
                    put("id", w.id)
                    put("date", w.date)
                    put("outfitId", w.outfitId)
                    val gWornJson = JSONArray().apply { w.garmentsWornIds.forEach { put(it) } }
                    put("garmentsWornIds", gWornJson)
                    put("loved", w.loved)
                    put("skipped", w.skipped)
                    put("selfieUrl", w.selfieUrl ?: JSONObject.NULL)
                }
                wearHistoryArray.put(wObj)
            }
            root.put("wearHistory", wearHistoryArray)

            // Save Trip Plans
            val tripPlansArray = JSONArray()
            for (p in _tripPlans.value) {
                val pObj = JSONObject().apply {
                    put("id", p.id)
                    put("destination", p.destination)
                    put("startDate", p.startDate)
                    put("endDate", p.endDate)
                    put("tempLow", p.tempLow.toDouble())
                    put("tempHigh", p.tempHigh.toDouble())
                    put("weatherCondition", p.weatherCondition)
                }
                tripPlansArray.put(pObj)
            }
            root.put("tripPlans", tripPlansArray)

            file.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateTaste(taste: UserTaste) {
        _userTaste.value = taste
        saveData()
    }

    fun scoreLookbookOutfit(garments: List<Garment>): Outfit {
        return scoreAndBuildOutfit(garments, "Lookbook", 70f).copy(
            name = "Custom Lookbook Outfit"
        )
    }

    fun saveLookbookOutfit(garments: List<Garment>): Outfit {
        val outfit = scoreLookbookOutfit(garments)

        val currentTaste = _userTaste.value
        val currentVector = currentTaste.tasteVector
        val outfitCentroid = FloatArray(512)

        for (i in 0 until 512) {
            var sum = 0f
            for (g in garments) {
                sum += g.embedding.getOrElse(i) { 0f }
            }
            outfitCentroid[i] = sum / garments.size
        }

        val blendedVector = FloatArray(512) { i ->
            (currentVector.getOrElse(i) { 0f } * 0.85f) + (outfitCentroid[i] * 0.15f)
        }

        _userTaste.value = currentTaste.copy(tasteVector = blendedVector)
        saveData()
        return outfit
    }

    // SIMULATED INGESTION
    fun queueIngestionItems(urls: List<String>) {
        val newItems = urls.map { url ->
            IngestionItem(
                originalImageUrl = url,
                status = IngestionStatus.PRE_FLIGHT,
                progress = 0.1f,
                stepLabel = "Pre-flight: Checking size and exact duplicates..."
            )
        }
        _ingestionQueue.value = _ingestionQueue.value + newItems
    }

    fun updateIngestionItemProgress(itemId: String, status: IngestionStatus, progress: Float, label: String, garment: Garment? = null) {
        _ingestionQueue.value = _ingestionQueue.value.map { item ->
            if (item.id == itemId) {
                item.copy(status = status, progress = progress, stepLabel = label, detectedGarment = garment)
            } else {
                item
            }
        }
    }

    fun updateIngestionItemCrop(itemId: String, label: String, cropBase64: String) {
        _ingestionQueue.value = _ingestionQueue.value.map { item ->
            if (item.id == itemId) {
                item.copy(label = label, cropBase64 = cropBase64)
            } else {
                item
            }
        }
    }

    fun approveIngestionItem(itemId: String) {
        val item = _ingestionQueue.value.find { it.id == itemId }
        item?.detectedGarment?.let { garment ->
            _garments.value = _garments.value + garment
        }
        _ingestionQueue.value = _ingestionQueue.value.filter { it.id != itemId }
        saveData()
    }

    fun rejectIngestionItem(itemId: String) {
        _ingestionQueue.value = _ingestionQueue.value.filter { it.id != itemId }
    }

    fun editIngestedGarment(itemId: String, updatedGarment: Garment) {
        _ingestionQueue.value = _ingestionQueue.value.map { item ->
            if (item.id == itemId) {
                item.copy(detectedGarment = updatedGarment)
            } else {
                item
            }
        }
    }

    fun toggleGarmentLaundry(garmentId: String) {
        _garments.value = _garments.value.map { g ->
            if (g.id == garmentId) {
                val nextStatus = when (g.laundryStatus) {
                    LaundryStatus.CLEAN -> LaundryStatus.DIRTY
                    LaundryStatus.DIRTY -> LaundryStatus.IN_LAUNDRY
                    LaundryStatus.IN_LAUNDRY -> LaundryStatus.CLEAN
                }
                g.copy(laundryStatus = nextStatus)
            } else {
                g
            }
        }
        saveData()
    }

    // WEAR LOGGING & flywheel updates
    fun logWear(outfit: Outfit, loved: Boolean) {
        val event = WearEvent(
            outfitId = outfit.id,
            garmentsWornIds = outfit.garments.map { it.id },
            loved = loved,
            skipped = false
        )
        _wearHistory.value = _wearHistory.value + event

        // Update cost-per-wear and wear counts
        _garments.value = _garments.value.map { g ->
            if (event.garmentsWornIds.contains(g.id)) {
                val newCount = g.wearCount + 1
                g.copy(
                    wearCount = newCount,
                    costPerWear = g.price / newCount.toDouble(),
                    laundryStatus = LaundryStatus.DIRTY // Automatically mark dirty
                )
            } else {
                g
            }
        }

        // Flywheel: Update UserTaste vector centroid using the garments in the loved outfit
        if (loved) {
            val currentTaste = _userTaste.value
            val currentVector = currentTaste.tasteVector
            val outfitCentroid = FloatArray(512)
            
            for (i in 0 until 512) {
                var sum = 0f
                for (g in outfit.garments) {
                    sum += g.embedding.getOrElse(i) { 0f }
                }
                outfitCentroid[i] = sum / outfit.garments.size
            }

            // Blend current taste vector with loved outfit (80/20 mix)
            val blendedVector = FloatArray(512)
            for (i in 0 until 512) {
                blendedVector[i] = (currentVector.getOrElse(i) { 0f } * 0.8f) + (outfitCentroid[i] * 0.2f)
            }
            
            _userTaste.value = currentTaste.copy(tasteVector = blendedVector)
        }
        saveData()
    }

    // SIMULATED FASHIONCLIP EMBEDDING SEARCH
    // We map search keywords to conceptual vector coordinates to execute actual Cosine Similarity vector queries
    fun searchGarments(query: String): List<Pair<Garment, Float>> {
        if (query.isBlank()) {
            return _garments.value.map { it to 1.0f }
        }

        val queryVec = getQueryVector(query)
        return _garments.value.map { garment ->
            val score = cosineSimilarity(queryVec, garment.embedding)
            garment to score
        }.sortedByDescending { it.second }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in 0 until minOf(v1.size, v2.size)) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return (dot / (sqrt(normA) * sqrt(normB)))
    }

    // Generates a mock 512-dimension FashionCLIP vector based on semantic match
    private fun getQueryVector(query: String): FloatArray {
        val vec = FloatArray(512)
        val term = query.lowercase()

        // Distribute coordinates to represent semantic keys
        if (term.contains("blue") || term.contains("sky")) vec[10] = 0.8f
        if (term.contains("black") || term.contains("dark")) vec[20] = 0.9f
        if (term.contains("white") || term.contains("cream")) vec[30] = 0.8f
        if (term.contains("shirt") || term.contains("oxford")) vec[100] = 0.7f
        if (term.contains("pants") || term.contains("jeans") || term.contains("trousers")) vec[200] = 0.7f
        if (term.contains("jacket") || term.contains("blazer") || term.contains("outerwear")) vec[300] = 0.7f
        if (term.contains("shoes") || term.contains("boots") || term.contains("loafers")) vec[400] = 0.7f
        if (term.contains("linen") || term.contains("summer")) vec[50] = 0.6f
        if (term.contains("cotton")) vec[60] = 0.5f
        if (term.contains("leather")) vec[70] = 0.8f
        
        // Add minimal noise so it looks like a real continuous vector
        for (i in 0 until 512) {
            vec[i] += (Math.random().toFloat() * 0.05f)
        }
        return vec
    }

    // STYLING RECOMMENDATION ENGINE
    fun generateRecommendations(temperature: Float, occasionName: String): List<Outfit> {
        val allItems = _garments.value
        val cleanItems = allItems.filter { it.laundryStatus == LaundryStatus.CLEAN }
        
        if (cleanItems.isEmpty()) return emptyList()

        val tops = cleanItems.filter { it.category == "Top" }
        val bottoms = cleanItems.filter { it.category == "Bottom" }
        val shoes = cleanItems.filter { it.category == "Shoes" }
        val outerwears = cleanItems.filter { it.category == "Outerwear" }

        val candidates = mutableListOf<Outfit>()

        // Generate combinations (Tops x Bottoms x Shoes) with optional Outerwear
        for (top in tops) {
            for (bottom in bottoms) {
                for (shoe in shoes) {
                    // Try without Outerwear
                    val basicOutfit = listOf(top, bottom, shoe)
                    if (passesHardFilters(basicOutfit) && passesContextFilters(basicOutfit, temperature, occasionName)) {
                        candidates.add(scoreAndBuildOutfit(basicOutfit, occasionName, temperature))
                    }

                    // Try with Outerwear
                    for (outer in outerwears) {
                        val fullOutfit = listOf(top, bottom, shoe, outer)
                        if (passesHardFilters(fullOutfit) && passesContextFilters(fullOutfit, temperature, occasionName)) {
                            candidates.add(scoreAndBuildOutfit(fullOutfit, occasionName, temperature))
                        }
                    }
                }
            }
        }

        // Sort by score
        val sortedCandidates = candidates.sortedByDescending { it.overallScore }

        // Perform Diversity / MMR Selection to grab 3 distinct options
        val selected = mutableListOf<Outfit>()
        for (candidate in sortedCandidates) {
            if (selected.size >= 5) break
            
            // Avoid adding outfits that share too many of the exact same garments as already selected ones
            val isTooSimilar = selected.any { existing ->
                val overlap = existing.garments.intersect(candidate.garments.toSet()).size
                overlap >= 2 // Shared top + bottom is too similar
            }
            if (!isTooSimilar || selected.isEmpty()) {
                selected.add(candidate)
            }
        }

        // Fallback: if no candidates passed filters (e.g. closet too small), force a basic one
        if (selected.isEmpty()) {
            val fallbackT = tops.firstOrNull()
            val fallbackB = bottoms.firstOrNull()
            val fallbackS = shoes.firstOrNull()
            if (fallbackT != null && fallbackB != null && fallbackS != null) {
                selected.add(Outfit(
                    garments = listOf(fallbackT, fallbackB, fallbackS),
                    reason = "Standard rotation outfit (minimal constraints met)."
                ))
            }
        }

        return selected
    }

    private fun passesHardFilters(garments: List<Garment>): Boolean {
        val categories = garments.map { it.category }
        val subcategories = garments.map { it.subcategory }

        // Rule 1: No Winter Jacket + Shorts
        if (subcategories.contains("Puffer Jacket") && subcategories.contains("Shorts")) return false
        
        // Rule 2: No Formal Shoes + Gym Shorts
        if (subcategories.contains("Oxfords") && subcategories.contains("Gym Shorts")) return false

        // Rule 3: No Three Black Items
        val blackCount = garments.count { it.colorName.lowercase().contains("black") }
        if (blackCount >= 3) return false

        // Rule 4: No Beach Shorts + Tie
        if (subcategories.contains("Beach Shorts") && subcategories.contains("Tie")) return false

        return true
    }

    private fun passesContextFilters(garments: List<Garment>, temp: Float, occasion: String): Boolean {
        // Temperature limits
        if (temp > 80f) {
            // Hot weather: no heavy outerwear or heavy knits
            val hasHeavy = garments.any { it.subcategory == "Puffer Jacket" || it.subcategory == "Wool Coat" }
            if (hasHeavy) return false
        }
        if (temp < 50f) {
            // Cold weather: require long sleeves or outerwear, no shorts
            val hasShorts = garments.any { it.subcategory == "Shorts" || it.subcategory == "Beach Shorts" }
            if (hasShorts) return false
            val hasOuter = garments.any { it.category == "Outerwear" }
            if (!hasOuter) return false
        }

        // Occasion limits
        val isFormalOccasion = occasion.equals("Formal", ignoreCase = true) || occasion.contains("Wedding", ignoreCase = true)
        val isCasualOccasion = occasion.equals("Casual", ignoreCase = true) || occasion.contains("Gym", ignoreCase = true)

        if (isFormalOccasion) {
            // formal requires high average formality score
            val avgFormality = garments.map { it.formalityScore }.average()
            if (avgFormality < 0.6) return false
        }
        if (isCasualOccasion) {
            val avgFormality = garments.map { it.formalityScore }.average()
            if (avgFormality > 0.6) return false
        }

        return true
    }

    private fun scoreAndBuildOutfit(garments: List<Garment>, occasion: String, temp: Float): Outfit {
        // 1. Color Harmony (using simplified LAB calculations)
        // Muted tones and complementary matches (distance around ~50-80 in LAB) score higher.
        var labDistSum = 0f
        var pairsCount = 0
        for (i in garments.indices) {
            for (j in i + 1 until garments.size) {
                val g1 = garments[i]
                val g2 = garments[j]
                val dist = sqrt(
                    (g1.labColor[0] - g2.labColor[0]) * (g1.labColor[0] - g2.labColor[0]) +
                    (g1.labColor[1] - g2.labColor[1]) * (g1.labColor[1] - g2.labColor[1]) +
                    (g1.labColor[2] - g2.labColor[2]) * (g1.labColor[2] - g2.labColor[2])
                )
                labDistSum += dist
                pairsCount++
            }
        }
        // Ideal color distance: Not too matchy (0), not too clashing (120+). 40-70 is solid.
        val avgDist = if (pairsCount > 0) labDistSum / pairsCount else 50f
        val colorScore = when {
            avgDist in 40f..80f -> 1.0f
            avgDist in 20f..40f -> 0.7f
            avgDist in 80f..100f -> 0.6f
            else -> 0.3f
        }

        // 2. Formality Coherence (standard deviation of formality ratings)
        val formalities = garments.map { it.formalityScore }
        val avgFormality = formalities.average().toFloat()
        val variance = formalities.map { (it - avgFormality) * (it - avgFormality) }.sum() / formalities.size
        val coherenceScore = 1.0f - minOf(1.0f, sqrt(variance) * 2f) // lower standard dev = higher score

        // 3. User Taste alignment (cosine similarity against Taste Centroid)
        val tasteVec = _userTaste.value.tasteVector
        var tasteMatchSum = 0f
        for (g in garments) {
            tasteMatchSum += cosineSimilarity(tasteVec, g.embedding)
        }
        val tasteScore = if (garments.isNotEmpty()) tasteMatchSum / garments.size else 0.5f

        // 4. Wear Frequency & Underworn Surface Booster
        // Boost items with high cost-per-wear to encourage wearing them, but penalize recently worn items.
        var underwornBoost = 0f
        var recentlyWornPenalty = 0f
        val recentWornIds = _wearHistory.value.takeLast(5).flatMap { it.garmentsWornIds }

        for (g in garments) {
            if (g.wearCount == 0 && g.price > 50) {
                underwornBoost += 0.15f // heavy boost for unworn expensive items
            } else if (g.costPerWear > 20) {
                underwornBoost += 0.05f
            }
            if (recentWornIds.contains(g.id)) {
                recentlyWornPenalty += 0.15f // discount items worn very recently
            }
        }

        val baseScore = (colorScore * 0.3f) + (coherenceScore * 0.3f) + (tasteScore * 0.4f)
        val overall = (baseScore + underwornBoost - recentlyWornPenalty).coerceIn(0.0f, 1.0f)

        // Compile logic reasoning text for UI display
        val wearReason = buildString {
            append("This looks great today. ")
            if (temp > 75) append("It's lightweight for the hot ${temp.toInt()}°F weather. ")
            else if (temp < 55) append("It includes appropriate layers for the cool ${temp.toInt()}°F forecast. ")
            else append("It's a perfect match for the pleasant ${temp.toInt()}°F day. ")
            
            if (occasion.contains("Presentation", ignoreCase = true) || occasion.contains("Work", ignoreCase = true)) {
                append("Formality is tailored to your professional presentation. ")
            }
            val unworn = garments.find { it.wearCount == 0 }
            if (unworn != null) {
                append("Surfacing your underworn ${unworn.brand} ${unworn.subcategory} to lower its cost-per-wear ($${String.format("%.2f", unworn.costPerWear)}).")
            } else {
                append("The ${garments.first().colorName} and ${garments.getOrNull(1)?.colorName ?: ""} hues match beautifully.")
            }
        }

        return Outfit(
            garments = garments,
            compatibilityScore = baseScore,
            colorHarmonyScore = colorScore,
            formalityCoherence = coherenceScore,
            overallScore = overall,
            reason = wearReason
        )
    }

    // TRIP/CAPSULE GENERATION
    fun generateTripPlan(destination: String, start: Long, end: Long, tempLow: Float, tempHigh: Float, condition: String) {
        val activeGarments = _garments.value.filter { it.laundryStatus == LaundryStatus.CLEAN }
        if (activeGarments.size < 5) return

        // Form capsule wardrobe (2 tops, 2 bottoms, 1 jacket, 1 shoe)
        val capsule = mutableListOf<Garment>()
        val tops = activeGarments.filter { it.category == "Top" }.take(3)
        val bottoms = activeGarments.filter { it.category == "Bottom" }.take(2)
        val shoes = activeGarments.filter { it.category == "Shoes" }.take(1)
        val outer = activeGarments.filter { it.category == "Outerwear" }.take(1)

        capsule.addAll(tops)
        capsule.addAll(bottoms)
        capsule.addAll(shoes)
        capsule.addAll(outer)

        // Generate daily combinations from this capsule
        val dayOutfits = mutableListOf<Outfit>()
        val days = ((end - start) / (24 * 60 * 60 * 1000)).toInt().coerceIn(1, 7)
        val avgTemp = (tempLow + tempHigh) / 2f

        for (day in 1..days) {
            val top = tops.getOrNull((day - 1) % tops.size) ?: activeGarments.first { it.category == "Top" }
            val bottom = bottoms.getOrNull((day - 1) % bottoms.size) ?: activeGarments.first { it.category == "Bottom" }
            val shoe = shoes.firstOrNull() ?: activeGarments.first { it.category == "Shoes" }
            val jacket = if (avgTemp < 65) outer.firstOrNull() else null

            val dayGarments = listOfNotNull(top, bottom, shoe, jacket)
            dayOutfits.add(Outfit(
                garments = dayGarments,
                reason = "Day $day Capsule Outfit for $destination ($condition, ${avgTemp.toInt()}°F)."
            ))
        }

        val plan = TripPlan(
            destination = destination,
            startDate = start,
            endDate = end,
            tempLow = tempLow,
            tempHigh = tempHigh,
            weatherCondition = condition,
            capsuleGarments = capsule,
            dailyOutfits = dayOutfits
        )
        _tripPlans.value = _tripPlans.value + plan
        saveData()
    }

    // HELPER: SEED SAMPLES
    fun seedInitialCloset() {
        val sampleGarments = listOf(
            Garment(
                id = "g_shirt_oxford_blue",
                category = "Top",
                subcategory = "Oxford Shirt",
                colorName = "Sky Blue",
                labColor = floatArrayOf(80f, -10f, -20f),
                material = "Cotton",
                pattern = "Plain",
                fit = "Slim",
                seasons = listOf("Spring", "Summer", "Autumn"),
                formalityScore = 0.6f,
                silhouette = "Structured",
                price = 85.00,
                brand = "Ralph Lauren",
                imageUrl = "g_shirt_oxford_blue", // Refers to mockup image overlay
                embedding = FloatArray(512).apply { this[10] = 0.8f; this[100] = 0.7f; this[60] = 0.5f },
                wearCount = 4,
                costPerWear = 21.25,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_shirt_polo_cream",
                category = "Top",
                subcategory = "Polo Shirt",
                colorName = "Cream White",
                labColor = floatArrayOf(95f, 0f, 10f),
                material = "Pique Cotton",
                pattern = "Plain",
                fit = "Regular",
                seasons = listOf("Summer"),
                formalityScore = 0.4f,
                silhouette = "Relaxed",
                price = 68.00,
                brand = "Lacoste",
                imageUrl = "g_shirt_polo_cream",
                embedding = FloatArray(512).apply { this[30] = 0.8f; this[100] = 0.5f; this[60] = 0.5f },
                wearCount = 2,
                costPerWear = 34.00,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_shirt_tshirt_black",
                category = "Top",
                subcategory = "T-Shirt",
                colorName = "Charcoal Black",
                labColor = floatArrayOf(15f, 0f, 0f),
                material = "Supima Cotton",
                pattern = "Plain",
                fit = "Slim",
                seasons = listOf("Spring", "Summer", "Autumn", "Winter"),
                formalityScore = 0.2f,
                silhouette = "Fitted",
                price = 35.00,
                brand = "Uniqlo",
                imageUrl = "g_shirt_tshirt_black",
                embedding = FloatArray(512).apply { this[20] = 0.9f; this[100] = 0.3f; this[60] = 0.5f },
                wearCount = 12,
                costPerWear = 2.91,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_pants_chino_khaki",
                category = "Bottom",
                subcategory = "Chinos",
                colorName = "Sand Beige",
                labColor = floatArrayOf(75f, 5f, 25f),
                material = "Cotton Twill",
                pattern = "Plain",
                fit = "Slim",
                seasons = listOf("Spring", "Autumn", "Summer"),
                formalityScore = 0.5f,
                silhouette = "Tapered",
                price = 98.00,
                brand = "Bonobos",
                imageUrl = "g_pants_chino_khaki",
                embedding = FloatArray(512).apply { this[30] = 0.5f; this[200] = 0.7f; this[60] = 0.5f },
                wearCount = 5,
                costPerWear = 19.60,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_pants_trousers_gray",
                category = "Bottom",
                subcategory = "Pleated Trousers",
                colorName = "Slate Gray",
                labColor = floatArrayOf(50f, 0f, 0f),
                material = "Wool Blend",
                pattern = "Checks",
                fit = "Relaxed",
                seasons = listOf("Autumn", "Winter", "Spring"),
                formalityScore = 0.8f,
                silhouette = "Wide Leg",
                price = 150.00,
                brand = "Theory",
                imageUrl = "g_pants_trousers_gray",
                embedding = FloatArray(512).apply { this[200] = 0.8f; this[20] = 0.4f },
                wearCount = 0, // Surfacing underworn!
                costPerWear = 150.00,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_pants_jeans_denim",
                category = "Bottom",
                subcategory = "Denim Jeans",
                colorName = "Indigo Blue",
                labColor = floatArrayOf(35f, 5f, -30f),
                material = "Denim",
                pattern = "Plain",
                fit = "Regular",
                seasons = listOf("Spring", "Autumn", "Winter"),
                formalityScore = 0.3f,
                silhouette = "Straight",
                price = 110.00,
                brand = "Levi's 501",
                imageUrl = "g_pants_jeans_denim",
                embedding = FloatArray(512).apply { this[10] = 0.6f; this[200] = 0.8f },
                wearCount = 20,
                costPerWear = 5.50,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_jacket_bomber_green",
                category = "Outerwear",
                subcategory = "Bomber Jacket",
                colorName = "Olive Green",
                labColor = floatArrayOf(45f, -15f, 20f),
                material = "Nylon",
                pattern = "Plain",
                fit = "Relaxed",
                seasons = listOf("Autumn", "Spring"),
                formalityScore = 0.3f,
                silhouette = "Boxy",
                price = 145.00,
                brand = "Alpha Industries",
                imageUrl = "g_jacket_bomber_green",
                embedding = FloatArray(512).apply { this[300] = 0.7f; this[50] = 0.3f },
                wearCount = 3,
                costPerWear = 48.33,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_jacket_blazer_navy",
                category = "Outerwear",
                subcategory = "Blazer",
                colorName = "Navy Blue",
                labColor = floatArrayOf(25f, 0f, -20f),
                material = "Hopsack Wool",
                pattern = "Plain",
                fit = "Structured",
                seasons = listOf("Spring", "Autumn", "Winter"),
                formalityScore = 0.8f,
                silhouette = "Tailored",
                price = 280.00,
                brand = "Club Monaco",
                imageUrl = "g_jacket_blazer_navy",
                embedding = FloatArray(512).apply { this[10] = 0.7f; this[300] = 0.8f },
                wearCount = 1,
                costPerWear = 280.00,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_shoes_loafers_brown",
                category = "Shoes",
                subcategory = "Loafers",
                colorName = "Cognac Brown",
                labColor = floatArrayOf(40f, 20f, 30f),
                material = "Suede Leather",
                pattern = "Plain",
                fit = "Regular",
                seasons = listOf("Spring", "Summer", "Autumn"),
                formalityScore = 0.6f,
                silhouette = "Sleek",
                price = 210.00,
                brand = "G.H. Bass",
                imageUrl = "g_shoes_loafers_brown",
                embedding = FloatArray(512).apply { this[400] = 0.8f; this[70] = 0.8f },
                wearCount = 6,
                costPerWear = 35.00,
                laundryStatus = LaundryStatus.CLEAN
            ),
            Garment(
                id = "g_shoes_sneakers_white",
                category = "Shoes",
                subcategory = "Sneakers",
                colorName = "Minimalist White",
                labColor = floatArrayOf(98f, 0f, 0f),
                material = "Full-grain Leather",
                pattern = "Plain",
                fit = "Low Top",
                seasons = listOf("Spring", "Summer", "Autumn"),
                formalityScore = 0.3f,
                silhouette = "Clean",
                price = 120.00,
                brand = "Common Projects",
                imageUrl = "g_shoes_sneakers_white",
                embedding = FloatArray(512).apply { this[30] = 0.9f; this[400] = 0.7f; this[70] = 0.5f },
                wearCount = 15,
                costPerWear = 8.00,
                laundryStatus = LaundryStatus.CLEAN
            )
        )
        _garments.value = sampleGarments

        // Initialize user taste preferences seed
        _userTaste.value = UserTaste(
            preferredStyles = listOf("Minimalist", "Classic Modern"),
            colorsAvoided = listOf("Neon Orange", "Lime Green"),
            preferredFits = listOf("Slim", "Tailored"),
            occasions = listOf("Business Casual", "Casual Outings"),
            tasteVector = FloatArray(512).apply {
                this[10] = 0.5f  // Light Blue affinity
                this[30] = 0.5f  // White/Cream affinity
                this[100] = 0.4f // Shirt affinity
                this[200] = 0.4f // Chino affinity
            }
        )
    }
}
