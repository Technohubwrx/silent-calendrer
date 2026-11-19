package com.silentcalendrer.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentcalendrer.R

private val Navy = Color(0xFF0B2A5B)
private val Accent = Color(0xFF0A5BC4)
private val Granted = Color(0xFF2E8B2E)

/**
 * Explains each permission and opens the right dialog or settings screen. Every step can be
 * skipped; missing permissions are shown later as a banner on the main screen.
 */
@Composable
fun OnboardingScreen(status: PermissionStatus, onPermissionResult: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var calendarDenied by rememberSaveable { mutableStateOf(false) }
    var notificationsDenied by rememberSaveable { mutableStateOf(false) }

    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarDenied = !granted
        onPermissionResult()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsDenied = !granted
        onPermissionResult()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC021F45))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding"),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            Modifier.widthIn(max = 560.dp).padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F8FF)),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.onb_title), color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.onb_intro), color = Navy, fontSize = 15.sp)

                Step(
                    R.string.onb_calendar_title, R.string.onb_calendar_body, status.calendar,
                    useSettings = calendarDenied,
                    onGrant = {
                        if (calendarDenied) context.launch(PermissionIntents.appDetails(context))
                        else calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                )
                Step(R.string.onb_dnd_title, R.string.onb_dnd_body, status.dnd, useSettings = true) {
                    context.launch(PermissionIntents.dndAccess())
                }
                Step(R.string.onb_alarm_title, R.string.onb_alarm_body, status.exactAlarm, useSettings = true) {
                    context.launch(PermissionIntents.exactAlarm(context))
                }
                Step(
                    R.string.onb_notif_title, R.string.onb_notif_body, status.notifications,
                    useSettings = notificationsDenied,
                    onGrant = {
                        if (notificationsDenied) context.launch(PermissionIntents.appDetails(context))
                        else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
                Step(R.string.onb_battery_title, R.string.onb_battery_body, status.batteryUnrestricted, useSettings = true) {
                    context.launch(PermissionIntents.batterySettings())
                }

                if (!status.calendar || !status.dnd) {
                    Text(stringResource(R.string.onb_skip_warning), color = Color(0xFF8A1C14), fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding-done"),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text(stringResource(R.string.onb_done)) }
            }
        }
    }
}

@Composable
private fun Step(titleRes: Int, bodyRes: Int, granted: Boolean, useSettings: Boolean, onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(titleRes), color = Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(bodyRes), color = Navy.copy(alpha = 0.85f), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (granted) {
                Text("✓ " + stringResource(R.string.onb_granted), color = Granted, fontWeight = FontWeight.SemiBold)
            } else {
                OutlinedButton(onClick = onGrant) {
                    Text(stringResource(if (useSettings) R.string.onb_open_settings else R.string.onb_grant), color = Accent)
                }
            }
        }
    }
}

private fun Context.launch(intent: Intent) {
    try {
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Some builds don't have every settings screen; fall back to the app's details page.
        startActivity(PermissionIntents.appDetails(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
