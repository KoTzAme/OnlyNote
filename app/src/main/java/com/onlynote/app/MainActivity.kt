package com.onlynote.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.onlynote.app.ui.OnlyNoteRoot
import com.onlynote.app.ui.theme.OnlyNoteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема всегда чёрная, поэтому системные панели тоже тёмные и прозрачные.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OnlyNoteTheme {
                OnlyNoteRoot()
            }
        }
    }
}
