package io.github.mejdi14.oddscroll

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins down the convention every strip transform is built on: Compose's
 * [Matrix] is row-vector times matrix, so `m[inputAxis, outputAxis]`. Getting
 * this backwards transposes every card, which is easy to misread as a bad
 * projection rather than a bad matrix.
 */
class StripMatrixTest {

    @Test
    fun mapsSourceRectOntoParallelogram() {
        val srcW = 20f
        val srcH = 50f
        val origin = Offset(100f, 30f)
        val col = Offset(12f, 3f)    // screen vector across the strip
        val row = Offset(-4f, 40f)   // screen vector down the strip

        val m = Matrix()
        m[0, 0] = col.x / srcW
        m[0, 1] = col.y / srcW
        m[1, 0] = row.x / srcH
        m[1, 1] = row.y / srcH
        m[3, 0] = origin.x
        m[3, 1] = origin.y

        assertNear(origin, m.map(Offset(0f, 0f)))
        assertNear(origin + col, m.map(Offset(srcW, 0f)))
        assertNear(origin + row, m.map(Offset(0f, srcH)))
        assertNear(origin + col + row, m.map(Offset(srcW, srcH)))
    }

    private fun assertNear(expected: Offset, actual: Offset) {
        assertTrue(
            abs(expected.x - actual.x) < 0.01f && abs(expected.y - actual.y) < 0.01f,
            "expected $expected but was $actual",
        )
    }
}
