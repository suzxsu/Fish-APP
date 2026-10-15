package com.guanyji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.guanyji.app.data.local.CareNotificationScheduler
import com.guanyji.app.navigation.FishApp
import com.guanyji.app.ui.theme.FishAppTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guanyji.app.feature.profile.ProfileViewModel
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    private var openCareTaskId by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        openCareTaskId = intent.getStringExtra(CareNotificationScheduler.EXTRA_TASK_ID)
        CareNotificationScheduler.sync(this)
        setContent {
            val profile: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(application))
            val preferences by profile.preferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (preferences.theme) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            FishAppTheme(preferences.theme) {
                FishApp(openCareTaskId) { openCareTaskId = null }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCareTaskId = intent.getStringExtra(CareNotificationScheduler.EXTRA_TASK_ID)
    }

    override fun onResume() {
        super.onResume()
        CareNotificationScheduler.sync(this)
    }
}
