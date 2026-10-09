package io.github.mejdi14.oddscroll.internal

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Spiral geometry, in world units. Vertical spacing is set below the card
 * height so consecutive cards overlap and the column resolves as one continuous
 * band instead of a stack of separate tiles.
 */
internal const val CARD_H = 1.6f
internal const val RADIUS = 3.8f

/** Radius step per slot, or overlapping cards fight for the same depth. */
internal const val RADIUS_STEP = 0.055f
internal const val CAMERA_Z = 10f
internal const val FOV_DEGREES = 48f
internal const val NEAR = 0.1f

/**
 * Cards deform while the column rotates. The falloff runs vertically, so upper
 * and lower edges sweep around the axis while the waist stays put and the
 * surface curves away from the direction of travel. Taken over the height
 * rather than the width: each horizontal row is displaced as a unit and its
 * width is untouched, where a falloff over the width would stretch the card.
 */
internal const val BEND = 2.7f

/**
 * Where a point of a card lands on screen, and how far it sits from the camera.
 *
 * The projection is done by hand rather than through a `graphicsLayer` because
 * every card needs its own per-strip depth: a card is a section of a cylinder,
 * not a plane that happens to be turned.
 */
internal class HelixProjector {
    var width: Float = 0f
    var height: Float = 0f
    var count: Int = 1
    var twist: Float = 0.8f
    var rise: Float = 0.79f
    var cardRatio: Float = 2f

    /** 1 / tan(fov / 2) — the usual pinhole focal length in NDC units. */
    private val focal: Float = 1f / tan(FOV_DEGREES * PI_F / 360f)

    private val aspect: Float get() = width / if (height == 0f) 1f else height

    /** Position of [index] within the cycle, measured from the facing slot. */
    fun slotOf(index: Int, progress: Float): Float {
        val span = count.toFloat()
        val raw = ((index - progress) % span + span) % span
        return raw - floor(span * 0.5f)
    }

    /** Depth of the card's centre, used to order the draw back to front. */
    fun depthOf(slot: Float): Float {
        val r = RADIUS + slot * RADIUS_STEP
        return CAMERA_Z - cos(slot * twist) * r
    }

    /**
     * [ax] runs 0..1 across the card and [ay] 0..1 up it. [velocity] is the
     * smoothed rotation rate that drives the trailing deformation.
     */
    fun project(slot: Float, ax: Float, ay: Float, velocity: Float): Offset {
        val cardW = CARD_H * cardRatio
        val localX = (ax - 0.5f) * cardW
        val localY = (ay - 0.5f) * CARD_H

        val ty = (ay - 0.5f) * 2f
        val angle = slot * twist + velocity * BEND * ty * ty * twist * 0.5f

        val r = RADIUS + slot * RADIUS_STEP
        val theta = angle + localX / r   // distance across the card as rotation

        val px = sin(theta) * r
        val py = localY + slot * rise * CARD_H
        val pz = cos(theta) * r
        val away = (CAMERA_Z - pz).coerceAtLeast(NEAR)

        return Offset(
            (px * focal / aspect / away * 0.5f + 0.5f) * width,
            (0.5f - py * focal / away * 0.5f) * height,
        )
    }

}

internal const val PI_F = 3.1415927f
