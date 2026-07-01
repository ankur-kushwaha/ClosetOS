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
    val straightenedImageUrl: String = "", // Transparent PNG of straightened/aligned garment
    val embedding: FloatArray = FloatArray(512), // Marqo-FashionSigLIP (512-d)
    val costPerWear: Double = price,
    val wearCount: Int = 0,
    val laundryStatus: LaundryStatus = LaundryStatus.CLEAN,
    val dateAdded: Long = getEpochTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is Garment) return false
        return id == other.id &&
                category == other.category &&
                subcategory == other.subcategory &&
                colorName == other.colorName &&
                material == other.material &&
                pattern == other.pattern &&
                fit == other.fit &&
                formalityScore == other.formalityScore &&
                price == other.price &&
                brand == other.brand &&
                imageUrl == other.imageUrl &&
                straightenedImageUrl == other.straightenedImageUrl &&
                laundryStatus == other.laundryStatus &&
                wearCount == other.wearCount
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + subcategory.hashCode()
        result = 31 * result + brand.hashCode()
        return result
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
    val reason: String = "",
    val wornCount: Int = 0,
    val avgCostPerWear: Double = 0.0,
    val temperatureC: Float = 22f,
    val isFavorite: Boolean = false,
    val isSaved: Boolean = false,
    val isAiGenerated: Boolean = false,
    val lastWornMs: Long? = null,
    val bestFor: List<String> = emptyList(),
    val aiNote: String = ""
)

data class LookbookCollection(
    val id: String = generateUUID(),
    val name: String,
    val outfitIds: List<String> = emptyList(),
    val isUserCreated: Boolean = false
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

data class DetectedBox(
    val bbox: List<Int>,
    val label: String,
    val score: Float,
    val cropBase64: String,
    val isSelected: Boolean = true,
    val sourceImageId: String? = null
)

data class NormalizationResult(
    val imageBase64: String,
    val provider: String = "unknown"
)

data class IngestionItem(
    val id: String = generateUUID(),
    val originalImageUrl: String,
    val status: IngestionStatus,
    val progress: Float, // 0.0 to 1.0
    val stepLabel: String,
    val detectedGarment: Garment? = null,
    val label: String? = null,
    val cropBase64: String? = null,
    val normalizedBase64: String? = null,
    val sourceImageId: String? = null,
    val isManualUpload: Boolean = false
)

enum class IngestionStatus {
    PRE_FLIGHT,
    GROUNDING_DINO,
    SAM2,
    QUALITY_VALIDATION,
    NORMALIZATION,
    NORMALIZATION_REVIEW,
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
    val dailyOutfits: List<Outfit> = emptyList(),
    val packingNotes: String = "",
    val provider: String = "local"
)

data class TravelCapsulePlan(
    val capsuleGarmentIds: List<String>,
    val dailyOutfits: List<TravelDayOutfit>,
    val packingNotes: String = "",
    val provider: String = "local"
)

data class TravelDayOutfit(
    val day: Int,
    val garmentIds: List<String>,
    val reason: String
)
