package com.silentcalendrer.ui

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Phones stay in portrait (the mobile artwork is portrait). Tablets rotate freely; on
        // Android 16+ large screens ignore orientation locks anyway.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    MainScreen(
                        ui,
                        onIssueClick = viewModel::openOnboarding,
                        onSettingsClick = viewModel::openSettings,
                        onResumeClick = viewModel::resumeSilencing,
                    )
                    if (ui.showSettings) {
                        BackHandler(onBack = viewModel::closeSettings)
                        SettingsScreen(
                            settings = ui.settings,
                            calendars = ui.calendars,
                            onLeadChange = viewModel::setLeadMinutes,
                            onTailChange = viewModel::setTailMinutes,
                            onReset = viewModel::resetTiming,
                            onCalendarToggle = viewModel::setCalendarIncluded,
                            onPermissions = viewModel::openOnboarding,
                            onDone = viewModel::closeSettings,
                        )
                    }
                    if (ui.showOnboarding) {
                        OnboardingScreen(
                            status = ui.permissions,
                            onPermissionResult = viewModel::refreshPermissions,
                            onDone = viewModel::finishOnboarding,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        viewModel.onPause()
        super.onPause()
    }
}
