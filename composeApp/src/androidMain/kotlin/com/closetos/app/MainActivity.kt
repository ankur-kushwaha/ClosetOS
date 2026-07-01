package com.closetos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.data.repository.NotificationCenter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Android storage context
        PlatformStorage.init(applicationContext)
        
        // Initialize repository
        ClosetRepository.init()
        NotificationCenter.init()
        
        setContent {
            App()
        }
    }
}
