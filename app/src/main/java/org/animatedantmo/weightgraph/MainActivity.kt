package org.animatedantmo.weightgraph

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.animatedantmo.weightgraph.ui.MainScreen
import org.animatedantmo.weightgraph.ui.theme.ThemePreferences
import org.animatedantmo.weightgraph.ui.theme.WeightGraphTheme
import org.animatedantmo.weightgraph.ui.theme.isDark

// The scrims enableEdgeToEdge uses by default behind a three-button navigation bar.
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreferences = ThemePreferences(this)
        setContent {
            var themeMode by remember { mutableStateOf(themePreferences.themeMode()) }
            val darkTheme = themeMode.isDark()

            // enableEdgeToEdge on its own picks status bar icon colours from the phone's setting,
            // which would leave dark icons on a dark app when only the app is set to Dark.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        darkTheme
                    },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            WeightGraphTheme(darkTheme = darkTheme) {
                MainScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themePreferences.setThemeMode(mode)
                        themeMode = mode
                    },
                )
            }
        }
    }
}
