package com.chesspattern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.chesspattern.ui.ChessPatternApp
import com.chesspattern.ui.theme.ChessPatternAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessPatternAnalyzerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChessPatternApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
