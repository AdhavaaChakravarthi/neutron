package com.adhavaa.commander

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adhavaa.commander.ui.CommanderApp
import com.adhavaa.commander.ui.CommanderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommanderTheme {
                CommanderApp()
            }
        }
    }
}
