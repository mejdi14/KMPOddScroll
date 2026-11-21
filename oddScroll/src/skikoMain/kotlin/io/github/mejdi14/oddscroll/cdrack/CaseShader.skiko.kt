package io.github.mejdi14.oddscroll.cdrack

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

/**
 * Compose on desktop, iOS and the web draws through Skia, whose SkSL takes
 * the AGSL source as it is, so the case is lit the way it is on Android. If
 * either shader fails to compile, it is drawn from its baked look in common
 * code.
 */
internal actual fun caseShader(): CaseShader? = runCatching { SkslCase() }.getOrNull()

/** Images as shaders the case can read, kept while they are in use. */
private const val MAPS_KEPT = 12

/**
 * A builder per shader. A Skia shader is fixed once it is made, so each draw
 * sets the uniforms and makes its own, and the layers drawn before it keep
 * theirs.
 */
private class SkslCase : CaseShader {
    private val glass = RuntimeShaderBuilder(RuntimeEffect.makeForShader(CASE_GLASS))
    private val disc = RuntimeShaderBuilder(RuntimeEffect.makeForShader(CASE_DISC))
    private val maps = HashMap<ImageBitmap, Shader>()

    override fun heightMap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
        // ARGB ints, unpremultiplied as Android's Bitmap.createBitmap takes
        // them, laid out as BGRA bytes.
        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val c = pixels[i]
            val o = i * 4
            bytes[o] = c.toByte()
            bytes[o + 1] = (c shr 8).toByte()
            bytes[o + 2] = (c shr 16).toByte()
            bytes[o + 3] = (c ushr 24).toByte()
        }
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
        bitmap.installPixels(bytes)
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }

    override fun glass(
        height: ImageBitmap,
        left: Float,
        top: Float,
        mmPerPx: Float,
        z: Float,
        eye: FloatArray,
        toWorld: FloatArray,
        thickness: Float,
        overDisc: Boolean,
    ): Brush {
        glass.child("height", map(height, FilterTileMode.DECAL))
        glass.uniform("frame", left, top, mmPerPx, z)
        glass.uniform("eye", eye[0], eye[1], eye[2])
        glass.uniform("toWorld", toWorld)
        glass.uniform("thickness", thickness)
        glass.uniform("overDisc", if (overDisc) 1f else 0f)
        return ShaderBrush(glass.makeShader().asComposeShader())
    }

    override fun disc(art: ImageBitmap, eye: FloatArray, toWorld: FloatArray): Brush {
        disc.child("art", map(art, FilterTileMode.CLAMP))
        disc.uniform("artSize", art.width.toFloat())
        disc.uniform("eye", eye[0], eye[1], eye[2])
        disc.uniform("toWorld", toWorld)
        return ShaderBrush(disc.makeShader().asComposeShader())
    }

    private fun map(image: ImageBitmap, tile: FilterTileMode): Shader = maps.getOrPut(image) {
        if (maps.size >= MAPS_KEPT) maps.clear()
        Image.makeFromBitmap(image.asSkiaBitmap()).makeShader(tile, tile, SamplingMode.LINEAR, null)
    }
}

