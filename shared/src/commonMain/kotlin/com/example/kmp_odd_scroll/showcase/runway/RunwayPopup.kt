package com.example.kmp_odd_scroll.showcase.runway

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.kmp_odd_scroll.showcase.internal.PI_F
import com.example.kmp_odd_scroll.showcase.internal.clamped
import com.example.kmp_odd_scroll.showcase.internal.smoothstep
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One flat illustrated papercraft cutout standee hinged along its bottom edge
 * on a diorama stop of [RunwayScroll].
 */
data class PopupCutout(
    val image: ImageBitmap,
    /** Lateral offset in runway widths from platform centre (roughly `-0.36 .. +0.36`). */
    val u: Float,
    /** Longitudinal offset in runway px from platform centre (negative = further back). */
    val localS: Float,
    /** Standing height in runway px; width follows the bitmap's aspect ratio. */
    val heightPx: Float,
    /** Mirrors the artwork horizontally so paired pieces read as distinct standees. */
    val mirror: Boolean = false,
)

/**
 * One themed pop-up diorama stop along the runway: its band and platform
 * paper colours plus its layered cutout standees.
 */
data class PopupScene(
    val groundColor: Color,
    val platformColor: Color,
    val cutouts: List<PopupCutout>,
)

/*
 * Center-driven pop-up curve in runway px measured from the focus row.
 * Only the stop sitting at the centre of the frame stands upright:
 * - inside FOLD_UP_PX of the focus row: standing full upright (90 deg)
 * - between FOLD_UP_PX and FOLD_FLAT_PX: smoothly hinging up/down as distance changes
 * - past FOLD_FLAT_PX (every neighbouring stop above and below): flat on the floor (0 deg)
 */
private const val FOLD_UP_PX = 270f
private const val FOLD_FLAT_PX = 680f

/**
 * Foreshortening of a cutout lying completely flat on the platform surface (0 deg)
 * relative to its full upright standing height (90 deg), plus perspective taper of
 * its far edge into the floor plane.
 */
private const val FLAT_HEIGHT_RATIO = 0.10f
private const val FLAT_TOP_TAPER = 0.22f

/** Maximum stand-up angle allowed during spring overshoot (~105 deg) so paper never folds forward. */
private const val MAX_ANGLE = 1.84f
private const val RIGHT_ANGLE = PI_F * 0.5f

/** Snappy cardstock hinge spring: tracks slow drags closely and pops cleanly on flicks/steps. */
private const val RISE_OMEGA = 24f
private const val RISE_ZETA = 0.52f
private const val FALL_OMEGA = 22f
private const val FALL_ZETA = 0.78f
private const val SPRING_MAX_STEP = 0.004f

/** Maximum distinct `(stop, cutout)` spring slots tracked without allocation. */
private const val MAX_CUTOUTS_PER_STOP = 16
private const val MAX_STOPS = 48
private const val MAX_SLOTS = MAX_STOPS * MAX_CUTOUTS_PER_STOP

/** Silhouette shadow tint cast onto the platform surface. */
private val SHADOW_FILTER = ColorFilter.tint(Color(0xFF0B0912), BlendMode.SrcIn)

/**
 * Pre-allocated brightness steps (`0.70 .. 1.00`) so cutouts look pressed onto the
 * shaded floor when flat and brighten into the light as they flip upright.
 */
private val SHADE_FILTERS: Array<ColorFilter?> = Array(17) { step ->
    if (step == 16) {
        null
    } else {
        val k = 0.70f + 0.30f * (step / 16f)
        ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(k, k, k, 1f) })
    }
}

/**
 * Renders layered pop-up paper cutouts on each runway stop. Keeps all spring
 * state and scratch buffers in flat arrays so a frame allocates nothing.
 */
internal class PaperPopupStage {
    private val angles = FloatArray(MAX_SLOTS) { -1f }
    private val angVels = FloatArray(MAX_SLOTS)
    private val order = IntArray(MAX_CUTOUTS_PER_STOP)
    private val corners = FloatArray(8)
    private val matrix = Matrix()

    /**
     * Advances the per-cutout hinge springs for [stopIndex] and draws cast
     * ground shadows, hinge crease lines, and perspective-projected standees
     * sorted cleanly back-to-front.
     */
    fun drawStop(
        scope: DrawScope,
        stopIndex: Int,
        scene: PopupScene,
        midS: Float,
        scrollPx: Float,
        focusXi: Float,
        dtSec: Float,
        cx: Float,
        h: Float,
        runwayScale: Float,
        popupLift: Float,
        projector: RunwayProjector,
    ) {
        val cutouts = scene.cutouts
        val count = min(cutouts.size, MAX_CUTOUTS_PER_STOP)
        if (count == 0) return

        val baseSlot = (stopIndex % MAX_STOPS) * MAX_CUTOUTS_PER_STOP
        val dt = dtSec.clamped(0.0005f, 0.033f)

        // 1. Update each cutout's hinge angle spring from its distance to the centre focus row.
        for (i in 0 until count) {
            order[i] = i
            val c = cutouts[i]
            val xiHinge = midS + c.localS - scrollPx
            val distFromCenter = abs(xiHinge - focusXi)
            val targetAngle = (1f - smoothstep(FOLD_UP_PX, FOLD_FLAT_PX, distFromCenter)) * RIGHT_ANGLE

            val slot = baseSlot + i
            var angle = angles[slot]
            if (angle < 0f) {
                // First frame seen: settle immediately at the rest pose so nothing jumps on entry.
                angles[slot] = targetAngle
                angVels[slot] = 0f
            } else {
                var vel = angVels[slot]
                val rising = targetAngle >= angle
                val omega = if (rising) RISE_OMEGA else FALL_OMEGA
                val zeta = if (rising) RISE_ZETA else FALL_ZETA
                val steps = (dt / SPRING_MAX_STEP).toInt().coerceIn(1, 10)
                val stepDt = dt / steps
                repeat(steps) {
                    val acc = -omega * omega * (angle - targetAngle) - 2f * zeta * omega * vel
                    vel += acc * stepDt
                    angle += vel * stepDt
                    if (angle < 0f) {
                        angle = 0f
                        if (vel < 0f) vel = 0f
                    } else if (angle > MAX_ANGLE) {
                        angle = MAX_ANGLE
                        if (vel > 0f) vel = 0f
                    }
                }
                if (abs(angle - targetAngle) < 0.0008f && abs(vel) < 0.004f) {
                    angle = targetAngle
                    vel = 0f
                }
                angles[slot] = angle
                angVels[slot] = vel
            }
        }

        // Insertion sort by hinge depth (smallest localS = farthest back -> drawn first).
        for (i in 1 until count) {
            val key = order[i]
            val keyS = cutouts[key].localS
            var j = i - 1
            while (j >= 0 && cutouts[order[j]].localS > keyS) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = key
        }

        with(scope) {
            // Pass 0: Cast silhouette shadows on the platform floor beneath rising/standing cutouts.
            for (k in 0 until count) {
                val idx = order[k]
                val c = cutouts[idx]
                val theta = angles[baseSlot + idx]
                val sSin = sin(theta).clamped(0f, 1.05f)
                if (sSin < 0.05f) continue

                val s0 = midS + c.localS
                val yn0 = projector.xiToYNormCard(s0 - scrollPx)
                val wf0 = projector.widthFraction(yn0 + PLINTH_THICKNESS_NORM)
                val imgW = c.image.width.toFloat()
                val imgH = c.image.height.toFloat()
                val halfU = (c.heightPx * (imgW / max(imgH, 1f)) * 0.5f) / PAGE_WIDTH_PX

                val standingH = c.heightPx * wf0 * popupLift
                val shadowH = standingH * 0.24f * sSin
                val shadowShiftX = -standingH * 0.26f * sSin
                if (shadowH < 2f) continue

                val yBot = yn0 * h
                val yTop = yBot - shadowH
                val xBL = cx + (c.u - halfU) * wf0 * runwayScale
                val xBR = cx + (c.u + halfU) * wf0 * runwayScale
                val topHalfW = (xBR - xBL) * 0.44f
                val topCx = 0.5f * (xBL + xBR) + shadowShiftX
                val xTL = topCx - topHalfW
                val xTR = topCx + topHalfW

                if (!c.mirror) {
                    corners[0] = xTL; corners[1] = yTop
                    corners[2] = xTR; corners[3] = yTop
                    corners[4] = xBR; corners[5] = yBot
                    corners[6] = xBL; corners[7] = yBot
                } else {
                    corners[0] = xTR; corners[1] = yTop
                    corners[2] = xTL; corners[3] = yTop
                    corners[4] = xBL; corners[5] = yBot
                    corners[6] = xBR; corners[7] = yBot
                }

                if (quadMatrix(corners, imgW, imgH, matrix)) {
                    withTransform({ transform(matrix) }) {
                        drawImage(
                            image = c.image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(c.image.width, c.image.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(c.image.width, c.image.height),
                            alpha = (0.24f * sSin).clamped(0f, 0.25f),
                            colorFilter = SHADOW_FILTER,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
            }

            // Pass 1: Cutouts currently lying nearly flat on the platform (always beneath standing pieces).
            for (k in 0 until count) {
                val idx = order[k]
                val theta = angles[baseSlot + idx]
                if (theta >= 0.08f) continue
                drawCutoutQuad(
                    c = cutouts[idx],
                    s0 = midS + cutouts[idx].localS,
                    scrollPx = scrollPx,
                    theta = theta,
                    cx = cx,
                    h = h,
                    runwayScale = runwayScale,
                    popupLift = popupLift,
                    projector = projector,
                )
            }

            // Pass 2: Rising and standing cutouts sorted back-to-front, with crisp base hinge slots.
            for (k in 0 until count) {
                val idx = order[k]
                val theta = angles[baseSlot + idx]
                if (theta < 0.08f) continue
                drawCutoutQuad(
                    c = cutouts[idx],
                    s0 = midS + cutouts[idx].localS,
                    scrollPx = scrollPx,
                    theta = theta,
                    cx = cx,
                    h = h,
                    runwayScale = runwayScale,
                    popupLift = popupLift,
                    projector = projector,
                )
            }
        }
    }

    private fun DrawScope.drawCutoutQuad(
        c: PopupCutout,
        s0: Float,
        scrollPx: Float,
        theta: Float,
        cx: Float,
        h: Float,
        runwayScale: Float,
        popupLift: Float,
        projector: RunwayProjector,
    ) {
        val yn0 = projector.xiToYNormCard(s0 - scrollPx)
        val wf0 = projector.widthFraction(yn0 + PLINTH_THICKNESS_NORM)
        val imgW = c.image.width.toFloat()
        val imgH = c.image.height.toFloat()
        val halfU = (c.heightPx * (imgW / max(imgH, 1f)) * 0.5f) / PAGE_WIDTH_PX

        val sinT = sin(theta)
        val cosT = cos(theta)
        val standingH = c.heightPx * wf0 * popupLift
        // Exact 3D tabletop hinge projection: FLAT_HEIGHT_RATIO when flat on floor (0 deg),
        // 1.0x exact bitmap aspect ratio when standing upright (90 deg).
        val visibleH = standingH * (sinT + FLAT_HEIGHT_RATIO * cosT)
        if (visibleH < 1.5f) return

        val yBot = yn0 * h
        val yTop = yBot - visibleH
        val baseXCenter = cx + c.u * wf0 * runwayScale
        val baseHalfW = halfU * wf0 * runwayScale
        val xBL = baseXCenter - baseHalfW
        val xBR = baseXCenter + baseHalfW

        // Top edge tapers inward toward the vanishing point when lying flat on the floor,
        // straightens to full width upright, and widens slightly on overshoot past 90 deg.
        val topScale = 1f - FLAT_TOP_TAPER * cosT
        val topHalfW = baseHalfW * topScale
        // Subtle inward perspective pull toward runway centre when lying flat on the floor.
        val topXCenter = cx + c.u * wf0 * runwayScale * topScale
        val xTL = topXCenter - topHalfW
        val xTR = topXCenter + topHalfW

        // Subtle dark paper-slot crease line along the bottom hinge while standing.
        val stand = sinT.clamped(0f, 1f)
        if (stand > 0.12f) {
            val inset = (xBR - xBL) * 0.14f
            drawLine(
                color = Color(0xFF14100C).copy(alpha = 0.36f * stand),
                start = Offset(xBL + inset, yBot + 1f),
                end = Offset(xBR - inset, yBot + 1f),
                strokeWidth = 2.2f,
            )
        }

        if (!c.mirror) {
            corners[0] = xTL; corners[1] = yTop
            corners[2] = xTR; corners[3] = yTop
            corners[4] = xBR; corners[5] = yBot
            corners[6] = xBL; corners[7] = yBot
        } else {
            corners[0] = xTR; corners[1] = yTop
            corners[2] = xTL; corners[3] = yTop
            corners[4] = xBL; corners[5] = yBot
            corners[6] = xBR; corners[7] = yBot
        }

        val shadeIdx = (stand * 16f).roundToInt().coerceIn(0, 16)
        if (quadMatrix(corners, imgW, imgH, matrix)) {
            withTransform({ transform(matrix) }) {
                drawImage(
                    image = c.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(c.image.width, c.image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(c.image.width, c.image.height),
                    colorFilter = SHADE_FILTERS[shadeIdx],
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}
