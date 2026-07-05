package com.lemonsquad.retrostash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lemonsquad.retrostash.ui.DownloadsScreen
import com.lemonsquad.retrostash.ui.RetroStashScreen
import com.lemonsquad.retrostash.ui.SettingsScreen
import com.lemonsquad.retrostash.ui.theme.RetroStashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RetroStashTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    var currentScreen by remember { mutableStateOf("main") }

                    when (currentScreen) {
                        "main" -> {
                            RetroStashScreen(
                                onSettingsClick = { currentScreen = "settings" },
                                onDownloadsClick = { currentScreen = "downloads" }
                            )
                        }
                        "settings" -> {
                            SettingsScreen(
                                onBackClick = { currentScreen = "main" }
                            )
                        }
                        "downloads" -> {
                            DownloadsScreen(
                                onBackClick = { currentScreen = "main" }
                            )
                        }
                    }
                }
            }
        }
    }
}
