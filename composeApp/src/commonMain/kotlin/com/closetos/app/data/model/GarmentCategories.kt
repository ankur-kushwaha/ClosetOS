package com.closetos.app.data.model

object GarmentCategories {
    val categories = listOf("Top", "Bottom", "Outerwear", "Shoes", "Accessory")

    private val subcategoriesByCategory = mapOf(
        "Top" to listOf(
            "Oxford Shirt", "Polo Shirt", "T-Shirt", "Silk Blouse", "Silk Camisole",
            "Sweater", "Hoodie", "Tank Top", "Dress Shirt"
        ),
        "Bottom" to listOf(
            "Chinos", "Pleated Trousers", "Denim Jeans", "Selvedge Jeans",
            "Shorts", "Beach Shorts", "Silk Midi Skirt", "Gym Shorts"
        ),
        "Outerwear" to listOf(
            "Blazer", "Bomber Jacket", "Linen Blazer", "Trench Coat",
            "Puffer Jacket", "Wool Coat", "Cardigan"
        ),
        "Shoes" to listOf(
            "Loafers", "Sneakers", "Canvas Sneakers", "Leather Loafers",
            "Boots", "Oxfords", "Derbies"
        ),
        "Accessory" to listOf(
            "Belt", "Scarf", "Hat", "Tie", "Watch", "Bag"
        )
    )

    fun subcategoriesFor(category: String): List<String> {
        return subcategoriesByCategory[category] ?: emptyList()
    }

    /** Human-readable label from YOLO detection class (TopWear → Top, etc.). */
    fun formatDetectionLabel(label: String?): String {
        if (label.isNullOrBlank()) return ""
        return when (label.lowercase()) {
            "topwear" -> "Top"
            "bottomwear" -> "Bottom"
            "footwear" -> "Shoes"
            "dress" -> "Dress"
            "clothing", "other" -> "Garment"
            else -> label.trim().replaceFirstChar { it.uppercase() }
        }
    }

    fun detectionTileName(label: String?, index: Int): String {
        formatDetectionLabel(label).takeIf { it.isNotBlank() }?.let { return it }
        return "Garment ${index + 1}"
    }

    fun ingestionItemDisplayName(item: IngestionItem): String {
        item.detectedGarment?.subcategory?.takeIf { it.isNotBlank() }?.let { return it }
        item.extractedAttributes?.subcategory?.takeIf { it.isNotBlank() }?.let { return it }
        formatDetectionLabel(item.label).takeIf { it.isNotBlank() }?.let { return it }
        val fromPath = item.originalImageUrl
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .removePrefix("gallery_crop_")
            .removePrefix("gallery_image_")
            .removePrefix("retailer_fetched_")
            .substringBeforeLast(".")
            .replace('_', ' ')
            .trim()
        if (fromPath.isNotBlank()) {
            return fromPath.replaceFirstChar { it.uppercase() }
        }
        return "Garment"
    }
}
