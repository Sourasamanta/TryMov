package com.example.trymov

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.trymov.ui.theme.TryMovTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TryMovTheme {
                TryMovApp()
            }
        }
    }
}

@Composable
fun TryMovApp() {
    Scaffold(
        containerColor = TryMovUiColors.Background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TryMovUiColors.Background)
                .padding(innerPadding)
        ) {
            FirstScreen(MovieViewModel())
        }
    }
}