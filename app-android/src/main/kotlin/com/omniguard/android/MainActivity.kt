package com.omniguard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.omniguard.android.navigation.OmniGuardNavHost
import com.omniguard.android.service.OmniGuardForegroundService
import com.omniguard.android.ui.theme.OmniGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OmniGuardApplication

        // Start battery-efficient background safety service (<5% / 12h)
        try {
            OmniGuardForegroundService.startService(this)
        } catch (e: Exception) {
            // Service startup handle for runtime permission flow
        }

        setContent {
            OmniGuardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    OmniGuardNavHost(
                        navController = navController,
                        app = app
                    )
                }
            }
        }
    }
}
