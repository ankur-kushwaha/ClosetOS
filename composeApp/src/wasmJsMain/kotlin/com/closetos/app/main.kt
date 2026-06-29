package com.closetos.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.closetos.app.data.repository.ClosetRepository

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Initialize repository
    ClosetRepository.init()
    
    // Attach to HTML canvas
    CanvasBasedWindow(title = "ClosetOS") {
        App()
    }
}
