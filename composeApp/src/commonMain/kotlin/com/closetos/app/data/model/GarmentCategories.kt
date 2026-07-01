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
}
