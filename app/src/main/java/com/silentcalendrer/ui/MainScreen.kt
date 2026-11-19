package com.silentcalendrer.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentcalendrer.BuildConfig
import com.silentcalendrer.R
import com.silentcalendrer.core.Meeting
import com.silentcalendrer.core.RingerMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

private val BrandDarkBlue = Color(0xFF024D9B)
private val BodyText = Color(0xFF0B2A5B)
private val TimeText = Color(0xFF1F4E8C)
private val EmptyText = Color(0xFF51657F)
private val BannerColor = Color(0xFFB3261E)

/** Maps background-image pixels to dp on screen. */
private class Geometry(val originX: Float, val originY: Float, val scale: Float, val fontScale: Float) {
    fun x(px: Float) = (originX + px * scale).dp
    fun y(px: Float) = (originY + px * scale).dp
    fun len(px: Float) = (px * scale).dp

    /**
     * Text size that follows the artwork. The user's font scale is honoured up to 1.3x so text
     * stays inside its frame; single-line fields also shrink to fit.
     */
    fun sp(px: Float): TextUnit = (px * scale * min(fontScale, 1.3f) / fontScale).sp
}

@Composable
fun MainScreen(
    ui: UiState,
    onIssueClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(BrandDarkBlue)) {
        val density = LocalDensity.current
        val dir = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing
        val insetL = insets.getLeft(density, dir) / density.density
        val insetT = insets.getTop(density) / density.density
        val insetR = insets.getRight(density, dir) / density.density
        val insetB = insets.getBottom(density) / density.density

        val fullW = maxWidth.value
        val fullH = maxHeight.value
        val availW = (fullW - insetL - insetR).coerceAtLeast(1f)
        val availH = (fullH - insetT - insetB).coerceAtLeast(1f)

        val spec = BackgroundSpecs.forWindow(fullW, fullH)
        val scale = min(availW / spec.width, availH / spec.height)
        val imgW = spec.width * scale
        val imgH = spec.height * scale
        val geo = Geometry(insetL + (availW - imgW) / 2f, insetT + (availH - imgH) / 2f, scale, density.fontScale)

        val bitmap = ImageBitmap.imageResource(spec.res)
        Canvas(Modifier.fillMaxSize()) {
            val d = density.density
            drawExtendedImage(
                bitmap,
                left = (geo.originX * d).roundToInt(), top = (geo.originY * d).roundToInt(),
                right = ((geo.originX + imgW) * d).roundToInt(), bottom = ((geo.originY + imgH) * d).roundToInt(),
            )
        }

        val context = LocalContext.current
        val text = spec.text

        // Bell that matches the mode: silent / vibration versions of the same artwork.
        when (ui.ringer) {
            RingerMode.SILENT -> spec.bellSilent
            RingerMode.VIBRATE -> spec.bellVibrate
            RingerMode.NORMAL -> null
        }?.let { res ->
            geo.Region(spec.bell, Modifier.testTag("bell-${ui.ringer.name.lowercase()}")) {
                Image(painterResource(res), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }

        // Settings, top-right of the header.
        geo.Region(spec.gear, Modifier.testTag("settings-button")) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings_open),
                    modifier = Modifier.fillMaxSize(0.72f),
                )
            }
        }

        // Ringer mode (and, while the app holds the phone silent, until when).
        val paused = ui.pausedUntil
        geo.Region(
            spec.mode,
            Modifier.testTag("mode").then(if (paused != null) Modifier.clickable(onClick = onResumeClick) else Modifier),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                FitText(modeLabel(ui.ringer), geo.sp(text.mode), Color.White, FontWeight.Bold, TextAlign.Center)
                val detail = when {
                    paused != null -> stringResource(R.string.paused_until, formatTime(context, paused, ui.now))
                    ui.silentUntil != null -> stringResource(R.string.until_time, formatTime(context, ui.silentUntil!!, ui.now))
                    else -> null
                }
                detail?.let {
                    FitText(it, geo.sp(text.modeDetail), Color.White.copy(alpha = 0.85f), FontWeight.Medium, TextAlign.Center)
                }
            }
        }

        // Today's date.
        geo.Region(spec.date, Modifier.testTag("date")) {
            FitText(
                DateUtils.formatDateTime(context, ui.now, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR),
                geo.sp(text.date), Color.White, FontWeight.Bold, TextAlign.Start, Modifier.align(Alignment.CenterStart),
            )
        }

        val emptyOverride = when (ui.calendarState) {
            CalendarState.NO_PERMISSION -> stringResource(R.string.empty_no_calendar_permission)
            CalendarState.NO_CALENDARS -> stringResource(R.string.empty_no_calendars)
            CalendarState.OK -> null
        }

        Section(
            geo, spec, spec.nowLabel, spec.nowBody, stringResource(R.string.section_now), "now",
            ui.schedule.current.map { it to formatTime(context, it.end, ui.now) },
            emptyOverride ?: stringResource(R.string.empty_now),
        )
        Section(
            geo, spec, spec.earlierLabel, spec.earlierBody, stringResource(R.string.section_earlier), "earlier",
            ui.schedule.past.map { it to formatRange(context, it, ui.now) },
            emptyOverride ?: stringResource(R.string.empty_earlier),
        )
        Section(
            geo, spec, spec.upcomingLabel, spec.upcomingBody, stringResource(R.string.section_upcoming), "upcoming",
            ui.schedule.upcoming.map { it to formatRange(context, it, ui.now) },
            emptyOverride ?: stringResource(R.string.empty_upcoming),
        )

        // Version, from the build configuration, next to the "V" printed in the artwork.
        geo.Region(spec.version, Modifier.testTag("version")) {
            FitText(BuildConfig.VERSION_NAME, geo.sp(text.version), Color.White, FontWeight.Bold, TextAlign.Start, Modifier.align(Alignment.CenterStart))
        }

        // Software house trademark, lower-left corner.
        geo.Region(spec.trademark, Modifier.testTag("trademark")) {
            FitText(
                stringResource(R.string.trademark), geo.sp(text.trademark), Color.White.copy(alpha = 0.85f),
                FontWeight.SemiBold, TextAlign.Start, Modifier.align(Alignment.CenterStart),
            )
        }

        ui.issues.firstOrNull()?.let { issue ->
            geo.Region(spec.banner, Modifier.testTag("banner")) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(BannerColor.copy(alpha = 0.93f))
                        .clickable(onClick = onIssueClick)
                        .padding(horizontal = geo.len(30f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        issueText(issue) + "  " + stringResource(R.string.issue_tap_to_fix) + " ›",
                        style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        maxLines = 2,
                        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = geo.sp(text.banner).coerceAtLeast(7.sp), stepSize = 0.25.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Geometry.Region(r: ImgRect, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .offset(x(r.left), y(r.top))
            .size(len(r.width), len(r.height))
            .then(modifier),
        content = content,
    )
}

@Composable
private fun Section(
    geo: Geometry,
    spec: BackgroundSpec,
    labelRect: ImgRect,
    bodyRect: ImgRect,
    label: String,
    tag: String,
    rows: List<Pair<Meeting, String>>,
    emptyText: String,
) {
    geo.Region(labelRect, Modifier.testTag("$tag-label")) {
        FitText(label, geo.sp(spec.text.label), Color.White, FontWeight.Bold, TextAlign.Start, Modifier.align(Alignment.CenterStart))
    }
    geo.Region(bodyRect, Modifier.testTag("$tag-list").clip(RoundedCornerShape(geo.len(spec.frameCornerRadius)))) {
        val padH = geo.len(26f)
        if (rows.isEmpty()) {
            Text(
                emptyText,
                Modifier.align(Alignment.Center).padding(horizontal = padH),
                color = EmptyText, fontSize = geo.sp(spec.text.body * 0.9f), fontStyle = FontStyle.Italic,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        } else {
            // Row spacing follows the text size so short (tablet) frames still fit two rows.
            MeetingList(rows, geo.sp(spec.text.body), padH, geo.len(spec.text.body * 0.2f))
        }
    }
}

@Composable
private fun MeetingList(rows: List<Pair<Meeting, String>>, textSize: TextUnit, padH: Dp, rowPad: Dp) {
    val state = rememberLazyListState()
    val untitled = stringResource(R.string.untitled_meeting)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().scrollIndicator(state),
        contentPadding = PaddingValues(horizontal = padH, vertical = rowPad / 2),
    ) {
        itemsIndexed(rows, key = { _, (m, _) -> "${m.eventId}-${m.begin}" }) { index, (meeting, time) ->
            if (index > 0) HorizontalDivider(color = BodyText.copy(alpha = 0.12f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = rowPad).semantics { contentDescription = "${meeting.title} $time" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    meeting.title.ifBlank { untitled },
                    Modifier.weight(1f),
                    color = BodyText, fontSize = textSize, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(padH / 2))
                Text(time, color = TimeText, fontSize = textSize * 0.92f, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** A thin thumb on the right edge, shown only when the list can scroll. */
private fun Modifier.scrollIndicator(state: LazyListState): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || info.totalItemsCount == 0) return@drawWithContent
    val avgSize = visible.sumOf { it.size }.toFloat() / visible.size
    val contentHeight = avgSize * info.totalItemsCount
    val viewport = size.height
    if (contentHeight <= viewport + 1f) return@drawWithContent
    val scrolled = state.firstVisibleItemIndex * avgSize + state.firstVisibleItemScrollOffset
    val thumbH = (viewport * viewport / contentHeight).coerceAtLeast(12.dp.toPx())
    val thumbY = (scrolled / (contentHeight - viewport)).coerceIn(0f, 1f) * (viewport - thumbH)
    val w = 3.dp.toPx()
    drawRoundRect(
        color = BodyText.copy(alpha = 0.35f),
        topLeft = Offset(size.width - w - 3.dp.toPx(), thumbY),
        size = Size(w, thumbH),
        cornerRadius = CornerRadius(w / 2),
    )
}

/** Single line that shrinks to fit its frame. */
@Composable
private fun FitText(
    text: String,
    maxSize: TextUnit,
    color: Color,
    weight: FontWeight,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier.fillMaxWidth(),
        style = TextStyle(color = color, fontWeight = weight, textAlign = align),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = maxSize.coerceAtLeast(7.sp), stepSize = 0.25.sp),
    )
}

private fun TextUnit.coerceAtLeast(min: TextUnit): TextUnit = if (value < min.value) min else this

/**
 * Draws the artwork into [left, top, right, bottom] and fills any leftover window space by
 * stretching the image's outermost pixel rows and columns, so the bands blend with the artwork.
 */
private fun DrawScope.drawExtendedImage(img: ImageBitmap, left: Int, top: Int, right: Int, bottom: Int) {
    val w = img.width
    val h = img.height
    val fullW = size.width.roundToInt()
    val fullH = size.height.roundToInt()
    val dw = right - left
    val dh = bottom - top
    fun part(sx: Int, sy: Int, sw: Int, sh: Int, dx: Int, dy: Int, dW: Int, dH: Int) {
        if (dW > 0 && dH > 0) {
            drawImage(img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset(dx, dy), IntSize(dW, dH), filterQuality = FilterQuality.Low)
        }
    }
    part(0, 0, 1, h, 0, top, left, dh)                       // left band
    part(w - 1, 0, 1, h, right, top, fullW - right, dh)      // right band
    part(0, 0, w, 1, left, 0, dw, top)                       // top band
    part(0, h - 1, w, 1, left, bottom, dw, fullH - bottom)   // bottom band
    part(0, 0, 1, 1, 0, 0, left, top)
    part(w - 1, 0, 1, 1, right, 0, fullW - right, top)
    part(0, h - 1, 1, 1, 0, bottom, left, fullH - bottom)
    part(w - 1, h - 1, 1, 1, right, bottom, fullW - right, fullH - bottom)
    drawImage(img, IntOffset.Zero, IntSize(w, h), IntOffset(left, top), IntSize(dw, dh), filterQuality = FilterQuality.High)
}

@Composable
private fun modeLabel(mode: RingerMode): String = when (mode) {
    RingerMode.NORMAL -> stringResource(R.string.mode_ringing)
    RingerMode.SILENT -> stringResource(R.string.mode_silent)
    RingerMode.VIBRATE -> stringResource(R.string.mode_vibration)
}

@Composable
private fun issueText(issue: Issue): String = when (issue) {
    Issue.CalendarOff -> stringResource(R.string.issue_calendar)
    Issue.DndOff -> stringResource(R.string.issue_dnd)
    Issue.ExactAlarmOff -> stringResource(R.string.issue_exact_alarm)
    Issue.NotificationsOff -> stringResource(R.string.issue_notifications)
    is Issue.SwitchFailed -> stringResource(R.string.issue_switch_failed, modeLabel(issue.reported))
}

/** Time of day in the user's 12/24-hour format; adds the weekday when it isn't today. */
internal fun formatTime(context: Context, time: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val sameDay = Instant.ofEpochMilli(time).atZone(zone).toLocalDate() == LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
    var flags = DateUtils.FORMAT_SHOW_TIME
    if (!sameDay) flags = flags or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
    return DateUtils.formatDateTime(context, time, flags)
}

private fun formatRange(context: Context, meeting: Meeting, now: Long): String =
    context.getString(R.string.time_range, formatTime(context, meeting.begin, now), formatTime(context, meeting.end, now))
