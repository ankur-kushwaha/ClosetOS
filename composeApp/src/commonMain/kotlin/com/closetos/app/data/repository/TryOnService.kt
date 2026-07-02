package com.closetos.app.data.repository

import com.closetos.app.*
import com.closetos.app.data.model.Outfit
import com.closetos.app.data.model.TryOnResult

object TryOnService {
    suspend fun renderOutfit(outfit: Outfit, forceRefresh: Boolean = false): TryOnResult? {
        if (!forceRefresh) {
            val cached = ClosetRepository.getTryOnImagePath(outfit.id)
            if (cached != null) {
                return TryOnResult(
                    imageBase64 = "",
                    provider = "cache",
                    imagePath = cached,
                    fromCache = true
                )
            }
        }

        val personPath = getDigitalTwinSelfiePath()
        if (personPath == null) {
            showToast("Set up your digital twin in onboarding first")
            return null
        }

        val result = renderTryOn(personPath, outfit.garments, outfit.id) ?: run {
            val detail = getLastTryOnError()
            showToast(detail ?: "Try-on failed. Check backend and GEMINI_API_KEY.")
            return null
        }

        val path = result.imagePath ?: saveBase64ImageToFile(result.imageBase64, "tryon_${outfit.id}")
        if (path != null) {
            ClosetRepository.cacheTryOnImage(outfit.id, path)
            return result.copy(imagePath = path)
        }
        return result
    }
}
