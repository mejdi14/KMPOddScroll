package io.github.mejdi14.oddscroll.cardsphere

import io.github.mejdi14.oddscroll.internal.fibonacciPoint
import kotlin.math.sqrt

/** Camera distance in sphere radii: a card at the front is three times the one at the back. */
internal const val CAMERA = 2f

/** Slots offered to the small cards. Some are refused, to keep air round the heroes. */
internal const val SMALL_SLOTS = 64

/** Cards are cut to the plates' own proportions, so nothing is cropped twice. */
internal const val CARD_RATIO = 0.8f

/** How many cards are carried at hero size. */
internal const val HERO_COUNT = 8

internal class SphereCard(
    val w: Float,
    val h: Float,
    /** A degree or two of lie, so the field does not read as a grid. */
    val rz: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    val hero: Boolean,
    val flower: Int,
)

/** The eight heroes, at the sizes the reference carries them. */
private val HERO_SIZES = listOf(
    Triple(412f, 515f, -1.5f),
    Triple(420f, 528f, 0.5f),
    Triple(390f, 495f, -1f),
    Triple(415f, 516f, 1f),
    Triple(410f, 501f, -0.5f),
    Triple(420f, 525f, 1.5f),
    Triple(421f, 533f, -1f),
    Triple(420f, 525f, 0.8f),
)

/**
 * The same field every time.
 *
 * A seeded generator rather than a stored list: the layout is a few rules and a
 * sequence, and writing out seventy-odd positions by hand would bury them.
 *
 * Plates are dealt by a stride rather than at random. The slots are walked
 * along a golden-angle spiral, so consecutive slots land far apart on the
 * sphere; stepping the plate by a number coprime with the set size then gives
 * every plate an equal share and keeps neighbours in space from drawing the
 * same one, which a random pick does not.
 */
internal fun buildField(flowerCount: Int): List<SphereCard> {
    val count = flowerCount.coerceAtLeast(1)
    var seed = 7L
    fun rnd(): Float {
        seed = (seed * 16807L) % 2147483647L
        return seed.toFloat() / 2147483647f
    }

    val point = FloatArray(3)
    val cards = ArrayList<SphereCard>(HERO_COUNT + SMALL_SLOTS)

    for (i in 0 until HERO_COUNT) {
        fibonacciPoint(HERO_COUNT, i, 1.1f, point)
        val (w, h, rz) = HERO_SIZES[i]
        cards.add(
            SphereCard(
                w = w, h = h, rz = rz,
                x = point[0], y = point[1], z = point[2],
                hero = true, flower = i % count,
            )
        )
    }

    var dealt = 0
    for (i in 0 until SMALL_SLOTS) {
        fibonacciPoint(SMALL_SLOTS, i, 0.37f, point)
        val px = point[0] + (rnd() - 0.5f) * 0.08f
        val py = point[1] + (rnd() - 0.5f) * 0.08f
        val pz = point[2] + (rnd() - 0.5f) * 0.08f
        val length = sqrt(px * px + py * py + pz * pz).coerceAtLeast(1e-6f)
        val qx = px / length
        val qy = py / length
        val qz = pz / length

        // Slots too near a hero are given up rather than moved: a hero needs
        // room around it to read, and nudging the small card would only pile it
        // onto its neighbour.
        if (cards.take(HERO_COUNT).any { it.x * qx + it.y * qy + it.z * qz > 0.9f }) continue

        val w = 110f + rnd() * 120f
        cards.add(
            SphereCard(
                w = w, h = w / CARD_RATIO, rz = (rnd() - 0.5f) * 8f,
                x = qx, y = qy, z = qz,
                hero = false, flower = (dealt * 5 + 3) % count,
            )
        )
        dealt++
    }

    return cards
}
