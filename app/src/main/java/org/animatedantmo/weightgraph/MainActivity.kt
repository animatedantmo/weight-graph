package org.animatedantmo.weightgraph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.animatedantmo.weightgraph.ui.MainScreen
import org.animatedantmo.weightgraph.ui.theme.WeightGraphTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeightGraphTheme {
                MainScreen()
            }
        }
    }
}
