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
 * Pop-up hinge zone along normalized screen y (0 = top horizon, 1 = bottom edge).
 * Cutouts enter from the bottom lying flat on their platform (angle = 0), swing
 * upright across ENTER_FLAT_YN -> ENTER_UP_YN with a cardstock spring overshoot,
 * stand tall across the middle of the frame, and fold back flat as they climb
 * past EXIT_UP_YN -> EXIT_FLAT_YN toward the horizon.
 */
private const val ENTER_FLAT_YN = 0.95f
private const val ENTER_UP_YN = 0.78f
private const val EXIT_UP_YN = 0.31f
private const val EXIT_FLAT_YN = 0.14f

/** Maximum stand-up angle allowed during spring overshoot (~110 deg) so paper never folds forward to the floor. */
private const val MAX_ANGLE = 1.92f
private const val RIGHT_ANGLE = PI_F * 0.5f

/** Underdamped cardstock hinge spring when flipping upright, and gentler settle when folding down. */
private const val RISE_OMEGA = 19.5f
private const val RISE_ZETA = 0.45f
private const val FALL_OMEGA = 15.5f
private const val FALL_ZETA = 0.74f
private const val SPRING_MAX_STEP = 0.004f

/** Maximum distinct `(stop, cutout)` spring slots tracked without allocation. */
private const val MAX_CUTOUTS_PER_STOP = 16
private const val MAX_STOPS = 48
private const val MAX_SLOTS = MAX_STOPS * MAX_CUTOUTS_PER_STOP

/** Silhouette shadow tint cast onto the platform surface. */
private val SHADOW_FILTER = ColorFilter.tint(Color(0xFF0B0912), BlendMode.SrcIn)

/**
 * Pre-allocated brightness steps (`0.76 .. 1.00`) so cutouts darken subtly when
 * lying flat and catch warm light as they hinge upright, with zero per-frame allocation.
 */
private val SHADE_FILTERS: Array<ColorFilter?> = Array(17) { step ->
    if (step == 16) {
        null
    } else {
        val k = 0.76f + 0.24f * (step / 16f)
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

        // 1. Update each cutout's hinge angle spring and prepare back-to-front sort order.
        for (i in 0 until count) {
            order[i] = i
            val c = cutouts[i]
            val xiHinge = midS + c.localS - scrollPx
            val ynHinge = projector.xiToYNormCard(xiHinge)

            val enterT = ((ENTER_FLAT_YN - ynHinge) / (ENTER_FLAT_YN - ENTER_UP_YN)).clamped(0f, 1f)
            val exitT = ((ynHinge - EXIT_FLAT_YN) / (EXIT_UP_YN - EXIT_FLAT_YN)).clamped(0f, 1f)
            val targetAngle = smoothstep(0f, 1f, min(enterT, exitT)) * RIGHT_ANGLE

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

        fun projX(u: Float, s: Float): Float {
            val yn = projector.xiToYNormCard(s - scrollPx)
            val wf = projector.widthFraction(yn + PLINTH_THICKNESS_NORM)
            return cx + u * wf * runwayScale
        }

        fun projY(s: Float, z: Float): Float {
            val yn = projector.xiToYNormCard(s - scrollPx)
            val wf = projector.widthFraction(yn + PLINTH_THICKNESS_NORM)
            return yn * h - z * wf * popupLift
        }

        with(scope) {
            // Pass 0: Cast silhouette shadows on the platform floor beneath every rising/standing cutout.
            for (k in 0 until count) {
                val idx = order[k]
                val c = cutouts[idx]
                val theta = angles[baseSlot + idx]
                val sSin = sin(theta).clamped(0f, 1.05f)
                val hSin = c.heightPx * sSin
                if (hSin < 10f) continue

                val s0 = midS + c.localS
                val imgW = c.image.width.toFloat()
                val imgH = c.image.height.toFloat()
                val halfU = (c.heightPx * (imgW / max(imgH, 1f)) * 0.5f) / PAGE_WIDTH_PX

                val sTop = s0 - hSin * 0.55f
                val uShift = -hSin * 0.28f / PAGE_WIDTH_PX
                val uL = c.u - halfU
                val uR = c.u + halfU
                val uTopL = uL + uShift
                val uTopR = uR + uShift

                val yTop = projY(sTop, 0f)
                val yBot = projY(s0, 0f)
                if (abs(yBot - yTop) < 1.5f) continue

                if (!c.mirror) {
                    corners[0] = projX(uTopL, sTop); corners[1] = yTop
                    corners[2] = projX(uTopR, sTop); corners[3] = yTop
                    corners[4] = projX(uR, s0); corners[5] = yBot
                    corners[6] = projX(uL, s0); corners[7] = yBot
                } else {
                    corners[0] = projX(uTopR, sTop); corners[1] = yTop
                    corners[2] = projX(uTopL, sTop); corners[3] = yTop
                    corners[4] = projX(uL, s0); corners[5] = yBot
                    corners[6] = projX(uR, s0); corners[7] = yBot
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
                    theta = theta,
                    projX = ::projX,
                    projY = ::projY,
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
                    theta = theta,
                    projX = ::projX,
                    projY = ::projY,
                )
            }
        }
    }

    private fun DrawScope.drawCutoutQuad(
        c: PopupCutout,
        s0: Float,
        theta: Float,
        projX: (Float, Float) -> Float,
        projY: (Float, Float) -> Float,
    ) {
        val imgW = c.image.width.toFloat()
        val imgH = c.image.height.toFloat()
        val halfU = (c.heightPx * (imgW / max(imgH, 1f)) * 0.5f) / PAGE_WIDTH_PX

        val sTop = s0 - c.heightPx * cos(theta)
        val zTop = c.heightPx * sin(theta)

        val yTop = projY(sTop, zTop)
        val yBot = projY(s0, 0f)
        if (yBot - yTop < 1.5f) return

        val uL = c.u - halfU
        val uR = c.u + halfU
        val xBL = projX(uL, s0)
        val xBR = projX(uR, s0)

        // Subtle dark paper-slot crease line along the bottom hinge while standing.
        val stand = sin(theta).clamped(0f, 1f)
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
            corners[0] = projX(uL, sTop); corners[1] = yTop
            corners[2] = projX(uR, sTop); corners[3] = yTop
            corners[4] = xBR; corners[5] = yBot
            corners[6] = xBL; corners[7] = yBot
        } else {
            corners[0] = projX(uR, sTop); corners[1] = yTop
            corners[2] = projX(uL, sTop); corners[3] = yTop
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
