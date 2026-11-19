package com.silentcalendrer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentcalendrer.R
import com.silentcalendrer.calendar.CalendarRepository.CalendarInfo
import com.silentcalendrer.core.UserSettings
import kotlin.math.roundToInt

private val Navy = Color(0xFF0B2A5B)
private val Accent = Color(0xFF0A5BC4)

/** One setting for all meetings: minutes before / after, and which calendars count. */
@Composable
fun SettingsScreen(
    settings: UserSettings,
    calendars: List<CalendarInfo>,
    onLeadChange: (Int) -> Unit,
    onTailChange: (Int) -> Unit,
    onReset: () -> Unit,
    onCalendarToggle: (Long, Boolean) -> Unit,
    onPermissions: () -> Unit,
    onDone: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC021F45))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("settings"),
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
                Text(stringResource(R.string.settings_title), color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                Section(stringResource(R.string.settings_timing)) {
                    MinutesSlider(stringResource(R.string.settings_lead), settings.leadMinutes, "lead", onLeadChange)
                    MinutesSlider(stringResource(R.string.settings_tail), settings.tailMinutes, "tail", onTailChange)
                    val isDefault = settings.leadMinutes == UserSettings.DEFAULT_LEAD_MINUTES &&
                        settings.tailMinutes == UserSettings.DEFAULT_TAIL_MINUTES
                    if (!isDefault) {
                        TextButton(onClick = onReset) { Text(stringResource(R.string.settings_reset), color = Accent) }
                    }
                }

                Section(stringResource(R.string.settings_calendars)) {
                    if (calendars.isEmpty()) {
                        Text(stringResource(R.string.settings_calendars_none), color = Navy.copy(alpha = 0.8f), fontSize = 14.sp)
                    }
                    var lastAccount: String? = null
                    calendars.forEach { cal ->
                        if (cal.account != lastAccount) {
                            lastAccount = cal.account
                            Text(cal.account, color = Navy.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        val included = cal.id !in settings.excludedCalendarIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onCalendarToggle(cal.id, !included) }
                                .testTag("calendar-${cal.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = included,
                                onCheckedChange = { onCalendarToggle(cal.id, it) },
                                colors = CheckboxDefaults.colors(checkedColor = Accent),
                            )
                            Box(Modifier.size(12.dp).background(Color(cal.color or 0xFF000000.toInt()), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(cal.name, color = Navy, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onPermissions, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_permissions), color = Accent)
                    }
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f).testTag("settings-done"),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) { Text(stringResource(R.string.settings_done)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun StepButton(label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.width(44.dp).testTag(tag)) {
        Text(label, color = if (enabled) Accent else Accent.copy(alpha = 0.3f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** 0–60 minutes. Saves when the finger lifts, so the schedule isn't recomputed on every step. */
@Composable
private fun MinutesSlider(label: String, value: Int, tag: String, onChange: (Int) -> Unit) {
    var dragging by remember { mutableFloatStateOf(value.toFloat()) }
    // The value we last asked to save. Older saved values arriving in between are ignored, so
    // quick taps aren't undone by a save that is still catching up.
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(value) {
        if (pending == null || pending == value) {
            dragging = value.toFloat()
            pending = null
        }
    }
    fun save(next: Int) {
        pending = next
        onChange(next)
    }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Navy, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.settings_minutes, dragging.roundToInt()),
                color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Count from the value on screen, so quick repeated taps add up before saving catches up.
            fun step(delta: Int) {
                val next = (dragging.roundToInt() + delta).coerceIn(0, UserSettings.MAX_MINUTES)
                dragging = next.toFloat()
                save(next)
            }
            StepButton("−", "minus-$tag", enabled = dragging.roundToInt() > 0) { step(-1) }
            Slider(
                value = dragging,
                onValueChange = { dragging = it },
                onValueChangeFinished = { save(dragging.roundToInt()) },
                valueRange = 0f..UserSettings.MAX_MINUTES.toFloat(),
                steps = UserSettings.MAX_MINUTES - 1,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Accent.copy(alpha = 0.18f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).testTag("slider-$tag").height(36.dp),
            )
            StepButton("+", "plus-$tag", enabled = dragging.roundToInt() < UserSettings.MAX_MINUTES) { step(1) }
        }
    }
}
