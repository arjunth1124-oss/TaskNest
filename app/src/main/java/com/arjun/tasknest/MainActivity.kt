package com.arjun.tasknest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arjun.tasknest.ui.navigation.TaskNestNavHost
import com.arjun.tasknest.ui.theme.TaskNestTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskNestTheme {
                TaskNestNavHost()
            }
        }
    }
}
