package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.screens.AppNavigator
import com.example.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    
    // Instantiate Kotlin ViewModels for UI orchestration
    private val authViewModel by viewModels<AuthViewModel>()
    private val chatViewModel by viewModels<ChatViewModel>()
    private val callViewModel by viewModels<CallViewModel>()
    private val statusViewModel by viewModels<StatusViewModel>()
    private val settingsViewModel by viewModels<SettingsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Match edge-to-edge systemic notch alignments
        enableEdgeToEdge()
        
        setContent {
            AppNavigator(
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                callViewModel = callViewModel,
                statusViewModel = statusViewModel,
                settingsViewModel = settingsViewModel
            )
        }
    }
}
