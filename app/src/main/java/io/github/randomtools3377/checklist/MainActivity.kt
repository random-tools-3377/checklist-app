package io.github.randomtools3377.checklist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.randomtools3377.checklist.ui.ChecklistApp
import io.github.randomtools3377.checklist.ui.ChecklistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Privacy.apply(this)
        setContent {
            ChecklistTheme {
                ChecklistApp()
            }
        }
    }
}
