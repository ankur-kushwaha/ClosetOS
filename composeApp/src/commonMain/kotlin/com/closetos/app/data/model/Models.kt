package com.closetos.app.data.model

import kotlin.random.Random
import com.closetos.app.getEpochTimeMillis

fun generateUUID(): String {
    val charPool = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..16)
        .map { Random.nextInt(0, charPool.length) }
        .map(charPool::get)
        .joinToString("")
}

data class Garment(
    val id: String = generateUUID(),
    val category: String,
    val subcategory: String,
    val colorName: String,
    val labColor: FloatArray = floatArrayOf(50f, 0f, 0f), // L*, a*, b* color spaces
    val material: String,
    val pattern: String,
    val fit: String,
    val seasons: List<String>,
    val formalityScore: Float, // 0.0 (casual) to 1.0 (formal)
    val silhouette: String,
    val price: Double = 0.0,
    val brand: String = "Unknown",
    val imageUrl: String = "", // Used to load the segmented RGBA PNG
    val embedding: FloatArray = FloatArray(512), // Marqo-FashionSigLIP (512-d)
    val costPerWear: Double = price,
    val wearCount: Int = 0,
    val laundryStatus: LaundryStatus = LaundryStatus.CLEAN,
    val dateAdded: Long = getEpochTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is Garment) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

enum class LaundryStatus {
    CLEAN, DIRTY, IN_LAUNDRY
}

data class Outfit(
    val id: String = generateUUID(),
    val garments: List<Garment>,
    val name: String = "Lookbook Outfit",
    val compatibilityScore: Float = 0.0f,
    val colorHarmonyScore: Float = 0.0f,
    val formalityCoherence: Float = 0.0f,
    val overallScore: Float = 0.0f,
    val tags: List<String> = emptyList(),
    val reason: String = ""
)

data class UserTaste(
    val preferredStyles: List<String> = emptyList(),
    val colorsAvoided: List<String> = emptyList(),
    val preferredFits: List<String> = emptyList(),
    val occasions: List<String> = emptyList(),
    val tasteVector: FloatArray = FloatArray(512)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is UserTaste) return false
        return tasteVector.contentEquals(other.tasteVector)
    }

    override fun hashCode(): Int {
        return tasteVector.contentHashCode()
    }
}

data class WearEvent(
    val id: String = generateUUID(),
    val date: Long = getEpochTimeMillis(),
    val outfitId: String,
    val garmentsWornIds: List<String>,
    val loved: Boolean = false,
    val skipped: Boolean = false,
    val selfieUrl: String? = null
)

data class IngestionItem(
    val id: String = generateUUID(),
    val originalImageUrl: String,
    val status: IngestionStatus,
    val progress: Float, // 0.0 to 1.0
    val stepLabel: String,
    val detectedGarment: Garment? = null
)

enum class IngestionStatus {
    PRE_FLIGHT,
    GROUNDING_DINO,
    SAM2,
    CROP_GARMENT,
    FASHION_CLIP,
    FLORENCE_2,
    READY,
    FAILED
}

data class TripPlan(
    val id: String = generateUUID(),
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val tempLow: Float,
    val tempHigh: Float,
    val weatherCondition: String,
    val capsuleGarments: List<Garment> = emptyList(),
    val dailyOutfits: List<Outfit> = emptyList()
)
