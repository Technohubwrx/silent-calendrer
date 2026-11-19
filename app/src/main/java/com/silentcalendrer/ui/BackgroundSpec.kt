package com.silentcalendrer.ui

import androidx.annotation.DrawableRes
import com.silentcalendrer.R

/** A rectangle in the pixel coordinates of a background image. */
data class ImgRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Text sizes in background-image pixels; scaled with the image at runtime. */
data class TextSizes(
    val mode: Float,
    val modeDetail: Float,
    val date: Float,
    val label: Float,
    val body: Float,
    val version: Float,
    val banner: Float,
    val trademark: Float,
)

/**
 * Where each piece of live data goes on a background image. The rectangles were measured from
 * the supplied assets (mobile bg.png 887x1774, tablet bg.png 1449x1086), so the text sits inside
 * the matching frame at any screen size.
 */
data class BackgroundSpec(
    @DrawableRes val res: Int,
    val width: Float,
    val height: Float,
    val mode: ImgRect,
    val date: ImgRect,
    val nowLabel: ImgRect,
    val nowBody: ImgRect,
    val earlierLabel: ImgRect,
    val earlierBody: ImgRect,
    val upcomingLabel: ImgRect,
    val upcomingBody: ImgRect,
    /** Just to the right of the "V" printed in the footer. */
    val version: ImgRect,
    /** Free space below the panel, used only when a problem must be shown. */
    val banner: ImgRect,
    /** Lower-left corner: the software house's trademark. */
    val trademark: ImgRect,
    /** The bell disc; silent / vibration variants of the same artwork are drawn over it. */
    val bell: ImgRect,
    @DrawableRes val bellSilent: Int,
    @DrawableRes val bellVibrate: Int,
    /** Settings button, top-right of the header. */
    val gear: ImgRect,
    val frameCornerRadius: Float,
    val text: TextSizes,
)

object BackgroundSpecs {
    val Mobile = BackgroundSpec(
        res = R.drawable.bg_mobile,
        width = 887f, height = 1774f,
        mode = ImgRect(300f, 285f, 795f, 418f),
        date = ImgRect(190f, 488f, 840f, 585f),
        nowLabel = ImgRect(168f, 641f, 465f, 689f),
        nowBody = ImgRect(60f, 706f, 828f, 856f),
        earlierLabel = ImgRect(168f, 917f, 465f, 965f),
        earlierBody = ImgRect(60f, 976f, 828f, 1174f),
        upcomingLabel = ImgRect(168f, 1232f, 465f, 1280f),
        upcomingBody = ImgRect(60f, 1291f, 828f, 1509f),
        version = ImgRect(414f, 1676f, 524f, 1718f),
        banner = ImgRect(40f, 1564f, 847f, 1654f),
        trademark = ImgRect(20f, 1722f, 380f, 1770f),
        bell = ImgRect(91f, 258f, 259f, 426f),
        bellSilent = R.drawable.bell_silent_mobile,
        bellVibrate = R.drawable.bell_vibrate_mobile,
        gear = ImgRect(814f, 8f, 878f, 72f),
        frameCornerRadius = 22f,
        text = TextSizes(mode = 62f, modeDetail = 30f, date = 42f, label = 31f, body = 33f, version = 30f, banner = 27f, trademark = 30f),
    )

    val Tablet = BackgroundSpec(
        res = R.drawable.bg_tablet,
        width = 1449f, height = 1086f,
        mode = ImgRect(560f, 192f, 1075f, 286f),
        date = ImgRect(220f, 336f, 1370f, 404f),
        nowLabel = ImgRect(208f, 447f, 535f, 484f),
        nowBody = ImgRect(114f, 492f, 1334f, 575f),
        earlierLabel = ImgRect(208f, 613f, 535f, 651f),
        earlierBody = ImgRect(114f, 658f, 1334f, 758f),
        upcomingLabel = ImgRect(208f, 793f, 535f, 831f),
        upcomingBody = ImgRect(114f, 838f, 1334f, 939f),
        version = ImgRect(726f, 1033f, 772f, 1067f),
        banner = ImgRect(260f, 986f, 1189f, 1028f),
        trademark = ImgRect(16f, 1040f, 360f, 1082f),
        bell = ImgRect(382f, 169f, 526f, 313f),
        bellSilent = R.drawable.bell_silent_tablet,
        bellVibrate = R.drawable.bell_vibrate_tablet,
        gear = ImgRect(1362f, 12f, 1436f, 86f),
        frameCornerRadius = 20f,
        text = TextSizes(mode = 52f, modeDetail = 24f, date = 36f, label = 25f, body = 21f, version = 24f, banner = 22f, trademark = 25f),
    )

    /** Portrait windows use the mobile artwork, landscape windows the tablet artwork. */
    fun forWindow(widthDp: Float, heightDp: Float): BackgroundSpec = if (widthDp > heightDp) Tablet else Mobile
}
