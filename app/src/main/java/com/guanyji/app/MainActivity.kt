package com.guanyji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.guanyji.app.navigation.FishApp
import com.guanyji.app.ui.theme.FishAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FishAppTheme {
                FishApp()
            }
        }
    }
}
