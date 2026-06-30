package com.closetos.app.data.repository

import com.closetos.app.PlatformStorage
import com.closetos.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun init() {
        loadData()
    }

    private fun loadData() {
        val savedText = PlatformStorage.loadString("closet_os_data.txt")
        if (savedText == null) {
            seedInitialCloset()
            saveData()
            return
        }

        try {
            val (loadedGarments, loadedTaste, historyAndTrips) = deserializeData(savedText)
            _garments.value = loadedGarments
            _userTaste.value = loadedTaste
            _wearHistory.value = historyAndTrips.first
            _tripPlans.value = historyAndTrips.second
        } catch (e: Exception) {
            e.printStackTrace()
            seedInitialCloset()
            saveData()
        }
    }

    fun saveData() {
        try {
            val serialized = serializeData(
                _garments.value,
                _userTaste.value,
                _wearHistory.value,
                _tripPlans.value
            )
            PlatformStorage.saveString("closet_os_data.txt", serialized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serializeData(
        garments: List<Garment>,
        taste: UserTaste,
        history: List<WearEvent>,
        trips: List<TripPlan>
    ): String {
        val sb = StringBuilder()
        
        sb.append("[GARMENTS]\n")
        for (g in garments) {
            val labStr = g.labColor.joinToString(",")
            val seasonsStr = g.seasons.joinToString(",")
            val embedStr = g.embedding.joinToString(",")
            sb.append("${g.id}|${g.category}|${g.subcategory}|${g.colorName}|$labStr|${g.material}|${g.pattern}|${g.fit}|$seasonsStr|${g.formalityScore}|${g.silhouette}|${g.price}|${g.brand}|${g.imageUrl}|$embedStr|${g.costPerWear}|${g.wearCount}|${g.laundryStatus.name}|${g.dateAdded}\n")
        }
        
        sb.append("[TASTE]\n")
        val prefStylesStr = taste.preferredStyles.joinToString(",")
        val colorsAvoidedStr = taste.colorsAvoided.joinToString(",")
        val prefFitsStr = taste.preferredFits.joinToString(",")
        val occasionsStr = taste.occasions.joinToString(",")
        val tasteVecStr = taste.tasteVector.joinToString(",")
        sb.append("$prefStylesStr|$colorsAvoidedStr|$prefFitsStr|$occasionsStr|$tasteVecStr\n")
        
        sb.append("[HISTORY]\n")
        for (h in history) {
            val garmentsWornStr = h.garmentsWornIds.joinToString(",")
            sb.append("${h.id}|${h.date}|${h.outfitId}|$garmentsWornStr|${h.loved}|${h.skipped}|${h.selfieUrl ?: ""}\n")
        }
        
        sb.append("[TRIPS]\n")
        for (t in trips) {
            sb.append("${t.id}|${t.destination}|${t.startDate}|${t.endDate}|${t.tempLow}|${t.tempHigh}|${t.weatherCondition}\n")
        }
        
        return sb.toString()
    }

    private fun deserializeData(content: String): Triple<List<Garment>, UserTaste, Pair<List<WearEvent>, List<TripPlan>>> {
        val garmentsList = mutableListOf<Garment>()
        var taste = UserTaste()
        val historyList = mutableListOf<WearEvent>()
        val tripsList = mutableListOf<TripPlan>()
        
        var currentSection = ""
        val lines = content.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("[")) {
                currentSection = trimmed
                continue
            }
            
            val parts = trimmed.split("|")
            try {
                when (currentSection) {
                    "[GARMENTS]" -> {
                        if (parts.size >= 19) {
                            val id = parts[0]
                            val category = parts[1]
                            val subcategory = parts[2]
                            val colorName = parts[3]
                            val labColor = if (parts[4].isEmpty()) FloatArray(3) else parts[4].split(",").map { it.toFloat() }.toFloatArray()
                            val material = parts[5]
                            val pattern = parts[6]
                            val fit = parts[7]
                            val seasons = if (parts[8].isEmpty()) emptyList() else parts[8].split(",")
                            val formalityScore = parts[9].toFloat()
                            val silhouette = parts[10]
                            val price = parts[11].toDouble()
                            val brand = parts[12]
                            val imageUrl = parts[13]
                            val embedding = if (parts[14].isEmpty()) FloatArray(512) else parts[14].split(",").map { it.toFloat() }.toFloatArray()
                            val costPerWear = parts[15].toDouble()
                            val wearCount = parts[16].toInt()
                            val laundryStatus = LaundryStatus.valueOf(parts[17])
                            val dateAdded = parts[18].toLong()
                            
                            garmentsList.add(Garment(
                                id = id, category = category, subcategory = subcategory, colorName = colorName,
                                labColor = labColor, material = material, pattern = pattern, fit = fit,
                                seasons = seasons, formalityScore = formalityScore, silhouette = silhouette,
                                price = price, brand = brand, imageUrl = imageUrl, embedding = embedding,
                                costPerWear = costPerWear, wearCount = wearCount, laundryStatus = laundryStatus,
                                dateAdded = dateAdded
                            ))
                        }
                    }
                    "[TASTE]" -> {
                        if (parts.size >= 5) {
                            val prefStyles = if (parts[0].isEmpty()) emptyList() else parts[0].split(",")
                            val colorsAvoided = if (parts[1].isEmpty()) emptyList() else parts[1].split(",")
                            val prefFits = if (parts[2].isEmpty()) emptyList() else parts[2].split(",")
                            val occasions = if (parts[3].isEmpty()) emptyList() else parts[3].split(",")
                            val tasteVector = if (parts[4].isEmpty()) FloatArray(512) else parts[4].split(",").map { it.toFloat() }.toFloatArray()
                            taste = UserTaste(prefStyles, colorsAvoided, prefFits, occasions, tasteVector)
                        }
                    }
                    "[HISTORY]" -> {
                        if (parts.size >= 7) {
                            val id = parts[0]
                            val date = parts[1].toLong()
                            val outfitId = parts[2]
                            val garmentsWornIds = if (parts[3].isEmpty()) emptyList() else parts[3].split(",")
                            val loved = parts[4].toBoolean()
                            val skipped = parts[5].toBoolean()
                            val selfieUrl = parts[6].ifEmpty { null }
                            historyList.add(WearEvent(id, date, outfitId, garmentsWornIds, loved, skipped, selfieUrl))
                        }
                    }
                    "[TRIPS]" -> {
                        if (parts.size >= 7) {
                            val id = parts[0]
                            val destination = parts[1]
                            val startDate = parts[2].toLong()
                            val endDate = parts[3].toLong()
                            val tempLow = parts[4].toFloat()
                            val tempHigh = parts[5].toFloat()
                            val weatherCondition = parts[6]
                            tripsList.add(TripPlan(id, destination, startDate, endDate, tempLow, tempHigh, weatherCondition))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return Triple(garmentsList, taste, Pair(historyList, tripsList))
    }

    fun updateTaste(taste: UserTaste) {
        _userTaste.value = taste
        saveData()
    }

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

    fun addQueuedIngestionItems(items: List<IngestionItem>) {
        _ingestionQueue.value = _ingestionQueue.value + items
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

    fun editGarment(updatedGarment: Garment) {
        _garments.value = _garments.value.map { g ->
            if (g.id == updatedGarment.id) updatedGarment else g
        }
        saveData()
    }

    fun deleteGarment(garmentId: String) {
        _garments.value = _garments.value.filter { it.id != garmentId }
        saveData()
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

    fun logWear(outfit: Outfit, loved: Boolean) {
        val event = WearEvent(
            outfitId = outfit.id,
            garmentsWornIds = outfit.garments.map { it.id },
            loved = loved,
            skipped = false
        )
        _wearHistory.value = _wearHistory.value + event

        _garments.value = _garments.value.map { g ->
            if (event.garmentsWornIds.contains(g.id)) {
                val newCount = g.wearCount + 1
                g.copy(
                    wearCount = newCount,
                    costPerWear = g.price / newCount.toDouble(),
                    laundryStatus = LaundryStatus.DIRTY
                )
            } else {
                g
            }
        }

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

            val blendedVector = FloatArray(512)
            for (i in 0 until 512) {
                blendedVector[i] = (currentVector.getOrElse(i) { 0f } * 0.8f) + (outfitCentroid[i] * 0.2f)
            }
            
            _userTaste.value = currentTaste.copy(tasteVector = blendedVector)
        }
        saveData()
    }

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

    private fun getQueryVector(query: String): FloatArray {
        val vec = FloatArray(512)
        val term = query.lowercase()

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
        
        for (i in 0 until 512) {
            vec[i] += (kotlin.random.Random.nextDouble().toFloat() * 0.05f)
        }
        return vec
    }

    fun generateRecommendations(temperature: Float, occasionName: String): List<Outfit> {
        val allItems = _garments.value
        val cleanItems = allItems.filter { it.laundryStatus == LaundryStatus.CLEAN }
        
        if (cleanItems.isEmpty()) return emptyList()

        val tops = cleanItems.filter { it.category == "Top" }
        val bottoms = cleanItems.filter { it.category == "Bottom" }
        val shoes = cleanItems.filter { it.category == "Shoes" }
        val outerwears = cleanItems.filter { it.category == "Outerwear" }

        val candidates = mutableListOf<Outfit>()

        for (top in tops) {
            for (bottom in bottoms) {
                for (shoe in shoes) {
                    val basicOutfit = listOf(top, bottom, shoe)
                    if (passesHardFilters(basicOutfit) && passesContextFilters(basicOutfit, temperature, occasionName)) {
                        candidates.add(scoreAndBuildOutfit(basicOutfit, occasionName, temperature))
                    }

                    for (outer in outerwears) {
                        val fullOutfit = listOf(top, bottom, shoe, outer)
                        if (passesHardFilters(fullOutfit) && passesContextFilters(fullOutfit, temperature, occasionName)) {
                            candidates.add(scoreAndBuildOutfit(fullOutfit, occasionName, temperature))
                        }
                    }
                }
            }
        }

        val sortedCandidates = candidates.sortedByDescending { it.overallScore }

        val selected = mutableListOf<Outfit>()
        for (candidate in sortedCandidates) {
            if (selected.size >= 5) break
            
            val isTooSimilar = selected.any { existing ->
                val overlap = existing.garments.intersect(candidate.garments.toSet()).size
                overlap >= 2
            }
            if (!isTooSimilar || selected.isEmpty()) {
                selected.add(candidate)
            }
        }

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
        val subcategories = garments.map { it.subcategory }

        if (subcategories.contains("Puffer Jacket") && subcategories.contains("Shorts")) return false
        if (subcategories.contains("Oxfords") && subcategories.contains("Gym Shorts")) return false

        val blackCount = garments.count { it.colorName.lowercase().contains("black") }
        if (blackCount >= 3) return false

        if (subcategories.contains("Beach Shorts") && subcategories.contains("Tie")) return false

        return true
    }

    private fun passesContextFilters(garments: List<Garment>, temp: Float, occasion: String): Boolean {
        if (temp > 80f) {
            val hasHeavy = garments.any { it.subcategory == "Puffer Jacket" || it.subcategory == "Wool Coat" }
            if (hasHeavy) return false
        }
        if (temp < 50f) {
            val hasShorts = garments.any { it.subcategory == "Shorts" || it.subcategory == "Beach Shorts" }
            if (hasShorts) return false
            val hasOuter = garments.any { it.category == "Outerwear" }
            if (!hasOuter) return false
        }

        val isFormalOccasion = occasion.equals("Formal", ignoreCase = true) || occasion.contains("Wedding", ignoreCase = true)
        val isCasualOccasion = occasion.equals("Casual", ignoreCase = true) || occasion.contains("Gym", ignoreCase = true)

        if (isFormalOccasion) {
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
        val avgDist = if (pairsCount > 0) labDistSum / pairsCount else 50f
        val colorScore = when {
            avgDist in 40f..80f -> 1.0f
            avgDist in 20f..40f -> 0.7f
            avgDist in 80f..100f -> 0.6f
            else -> 0.3f
        }

        val formalities = garments.map { it.formalityScore }
        val avgFormality = formalities.average().toFloat()
        val variance = formalities.map { (it - avgFormality) * (it - avgFormality) }.sum() / formalities.size
        val coherenceScore = 1.0f - minOf(1.0f, sqrt(variance) * 2f)

        val tasteVec = _userTaste.value.tasteVector
        var tasteMatchSum = 0f
        for (g in garments) {
            tasteMatchSum += cosineSimilarity(tasteVec, g.embedding)
        }
        val tasteScore = if (garments.isNotEmpty()) tasteMatchSum / garments.size else 0.5f

        var underwornBoost = 0f
        var recentlyWornPenalty = 0f
        val recentWornIds = _wearHistory.value.takeLast(5).flatMap { it.garmentsWornIds }

        for (g in garments) {
            if (g.wearCount == 0 && g.price > 50) {
                underwornBoost += 0.15f
            } else if (g.costPerWear > 20) {
                underwornBoost += 0.05f
            }
            if (recentWornIds.contains(g.id)) {
                recentlyWornPenalty += 0.15f
            }
        }

        val baseScore = (colorScore * 0.3f) + (coherenceScore * 0.3f) + (tasteScore * 0.4f)
        val overall = (baseScore + underwornBoost - recentlyWornPenalty).coerceIn(0.0f, 1.0f)

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
                append("Surfacing your underworn ${unworn.brand} ${unworn.subcategory} to lower its cost-per-wear ($${unworn.costPerWear}).")
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

    fun generateTripPlan(destination: String, start: Long, end: Long, tempLow: Float, tempHigh: Float, condition: String) {
        val activeGarments = _garments.value.filter { it.laundryStatus == LaundryStatus.CLEAN }
        if (activeGarments.size < 5) return

        val capsule = mutableListOf<Garment>()
        val tops = activeGarments.filter { it.category == "Top" }.take(3)
        val bottoms = activeGarments.filter { it.category == "Bottom" }.take(2)
        val shoes = activeGarments.filter { it.category == "Shoes" }.take(1)
        val outer = activeGarments.filter { it.category == "Outerwear" }.take(1)

        capsule.addAll(tops)
        capsule.addAll(bottoms)
        capsule.addAll(shoes)
        capsule.addAll(outer)

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
                imageUrl = "g_shirt_oxford_blue",
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
                wearCount = 0,
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

        _userTaste.value = UserTaste(
            preferredStyles = listOf("Minimalist", "Classic Modern"),
            colorsAvoided = listOf("Neon Orange", "Lime Green"),
            preferredFits = listOf("Slim", "Tailored"),
            occasions = listOf("Business Casual", "Casual Outings"),
            tasteVector = FloatArray(512).apply {
                this[10] = 0.5f
                this[30] = 0.5f
                this[100] = 0.4f
                this[200] = 0.4f
            }
        )
    }
}
