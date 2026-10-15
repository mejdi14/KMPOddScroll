package io.github.mejdi14.oddscroll.cardsphere

import androidx.compose.ui.graphics.Color
import io.github.mejdi14.oddscroll.internal.fibonacciPoint
import kotlin.math.sqrt

/* The palette, flat throughout. Nothing in this style is a gradient, a photo or
   a shadow — the depth is entirely in the projection. */
internal val Lime = Color(0xFFD5FE4F)
internal val Ink = Color(0xFF0D0D0D)
internal val CardDark = Color(0xFF1D1D1D)
internal val Paper = Color(0xFFF2F2F0)
internal val Grey = Color(0xFFD9D9D6)

/** Camera distance in sphere radii: a card at the front is three times the one at the back. */
internal const val CAMERA = 2f

/** Slots offered to the small cards. Some are refused, to keep air round the heroes. */
internal const val SMALL_SLOTS = 64

internal enum class CardKind {
    Lime, Icon, Chip, Boxed, Dots, Typeface, Shape, Target, Swatch, Stripe, Poster, Words,
}

internal class SphereCard(
    val kind: CardKind,
    val w: Float,
    val h: Float,
    /** A degree or two of lie, so the field does not read as a grid. */
    val rz: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    val hero: Boolean = false,
    val icon: String = "search",
    val morph: Boolean = false,
    val dotColor: Color = Lime,
    val seed: Int = 3,
    val background: Color? = null,
    val foreground: Color = Ink,
    val shape: String = "circle",
    val swatch: List<Color> = listOf(Lime, Ink, Paper),
)

private class HeroSpec(
    val kind: CardKind,
    val w: Float,
    val h: Float,
    val rz: Float,
    val icon: String = "search",
    val morph: Boolean = false,
    val dotColor: Color = Lime,
    val seed: Int = 3,
)

private val HEROES = listOf(
    HeroSpec(CardKind.Lime, 412f, 515f, -1.5f),
    HeroSpec(CardKind.Dots, 420f, 528f, 0.5f, dotColor = Lime, seed = 5),
    HeroSpec(CardKind.Typeface, 390f, 495f, -1f),
    HeroSpec(CardKind.Icon, 415f, 516f, 1f, icon = "search", morph = true),
    HeroSpec(CardKind.Target, 410f, 501f, -0.5f),
    HeroSpec(CardKind.Poster, 420f, 525f, 1.5f),
    HeroSpec(CardKind.Boxed, 421f, 533f, -1f),
    HeroSpec(CardKind.Words, 420f, 525f, 0.8f),
)

private val SMALL_KINDS = listOf(
    CardKind.Lime, CardKind.Lime, CardKind.Lime,
    CardKind.Icon, CardKind.Icon, CardKind.Icon,
    CardKind.Dots, CardKind.Dots,
    CardKind.Shape, CardKind.Shape, CardKind.Shape,
    CardKind.Swatch, CardKind.Stripe, CardKind.Typeface, CardKind.Chip, CardKind.Target,
)

private val SMALL_ICONS = listOf("layers", "basket", "person", "search", "ring", "x", "flower")

internal val SHAPE_NAMES = listOf("circle", "arch", "quarter", "halves", "plus", "stack")

private val SWATCH_SETS = listOf(
    listOf(Lime, Ink, Paper),
    listOf(Ink, Lime, Grey),
    listOf(Paper, Grey, Ink),
)

private val SHAPE_PAIRS = listOf(
    Paper to Ink,
    Lime to Ink,
    CardDark to Lime,
    CardDark to Paper,
)

/**
 * The same field every time.
 *
 * A seeded generator rather than a stored list: the layout is a few rules and a
 * sequence, and writing out seventy-odd positions by hand would bury them. The
 * draws on that sequence have to be taken in exactly this order, including the
 * three spent on jitter before a slot can be refused, or the field comes out
 * differently.
 */
internal fun buildField(): List<SphereCard> {
    var seed = 7L
    fun rnd(): Float {
        seed = (seed * 16807L) % 2147483647L
        return seed.toFloat() / 2147483647f
    }

    val point = FloatArray(3)
    val heroes = HEROES.mapIndexed { i, spec ->
        fibonacciPoint(HEROES.size, i, 1.1f, point)
        SphereCard(
            kind = spec.kind, w = spec.w, h = spec.h, rz = spec.rz,
            x = point[0], y = point[1], z = point[2],
            hero = true, icon = spec.icon, morph = spec.morph,
            dotColor = spec.dotColor, seed = spec.seed,
        )
    }

    val small = ArrayList<SphereCard>(SMALL_SLOTS)
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
        if (heroes.any { it.x * qx + it.y * qy + it.z * qz > 0.9f }) continue

        var kind = SMALL_KINDS[(rnd() * SMALL_KINDS.size).toInt().coerceAtMost(SMALL_KINDS.lastIndex)]
        val w = if (kind == CardKind.Chip) 130f + rnd() * 60f else 110f + rnd() * 120f
        val h = if (kind == CardKind.Chip) w * 0.55f else w * (1.05f + rnd() * 0.3f)
        val icon = SMALL_ICONS[(rnd() * SMALL_ICONS.size).toInt().coerceAtMost(SMALL_ICONS.lastIndex)]
        val rz = (rnd() - 0.5f) * 8f
        val dotSeed = (rnd() * 999f).toInt()

        var background: Color? = null
        var foreground = Ink
        var shape = "circle"
        var dotColor = Lime
        var swatch = SWATCH_SETS[0]

        if (kind == CardKind.Shape) {
            val pair = SHAPE_PAIRS[(rnd() * 4f).toInt().coerceAtMost(3)]
            background = pair.first
            foreground = pair.second
            shape = SHAPE_NAMES[(rnd() * SHAPE_NAMES.size).toInt().coerceAtMost(SHAPE_NAMES.lastIndex)]
        }
        if (kind == CardKind.Dots && rnd() < 0.4f) {
            background = Lime
            dotColor = Ink
        }
        if (kind == CardKind.Lime) {
            // A small lime card carries the dot mark rather than the wordmark,
            // which would be illegible at this size.
            kind = CardKind.Dots
            background = Lime
            dotColor = Ink
        }
        if (kind == CardKind.Stripe && rnd() < 0.5f) background = Paper
        if (kind == CardKind.Swatch) {
            swatch = SWATCH_SETS[(rnd() * 3f).toInt().coerceAtMost(2)]
        }

        small.add(
            SphereCard(
                kind = kind, w = w, h = h, rz = rz,
                x = qx, y = qy, z = qz,
                icon = icon, dotColor = dotColor, seed = dotSeed,
                background = background, foreground = foreground,
                shape = shape, swatch = swatch,
            )
        )
    }

    return heroes + small
}

/** Index of the hero cards within the field, which are always first. */
internal val HERO_COUNT = HEROES.size
