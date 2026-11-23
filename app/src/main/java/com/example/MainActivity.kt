package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GeoportApp
import com.example.ui.theme.GeoportEmfTheme
import com.example.viewmodel.InvestigationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: InvestigationViewModel = viewModel()
            val nightVisionEnabled by viewModel.nightVisionEnabled.collectAsStateWithLifecycle()
            val nightVisionBrightness by viewModel.nightVisionBrightness.collectAsStateWithLifecycle()
            GeoportEmfTheme(
                nightVision = nightVisionEnabled,
                nightVisionBrightness = nightVisionBrightness
            ) {
                GeoportApp(viewModel = viewModel)
            }
        }
    }
}
