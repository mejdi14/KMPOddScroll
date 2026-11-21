package io.github.mejdi14.oddscroll.cdrack

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * AGSL arrived in 33; below that, and if either shader fails to compile, the
 * case is drawn from its baked look in common code.
 */
internal actual fun caseShader(): CaseShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { AgslCase() }.getOrNull()
}

/** Bitmaps as shaders the case can read, kept while they are in use. */
private const val MAPS_KEPT = 12

/**
 * One shader for every layer, one for the disc. Setting a uniform drops the
 * shader's native instance, so each draw takes its own snapshot and the layers
 * drawn before it keep theirs.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslCase : CaseShader {
    private val glass = RuntimeShader(CASE_GLASS)
    private val glassBrush = ShaderBrush(glass)
    private val disc = RuntimeShader(CASE_DISC)
    private val discBrush = ShaderBrush(disc)
    private val maps = HashMap<ImageBitmap, BitmapShader>()

    override fun heightMap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

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
        glass.setInputShader("height", map(height, Shader.TileMode.DECAL))
        glass.setFloatUniform("frame", left, top, mmPerPx, z)
        glass.setFloatUniform("eye", eye[0], eye[1], eye[2])
        glass.setFloatUniform("toWorld", toWorld)
        glass.setFloatUniform("thickness", thickness)
        glass.setFloatUniform("overDisc", if (overDisc) 1f else 0f)
        return glassBrush
    }

    override fun disc(art: ImageBitmap, eye: FloatArray, toWorld: FloatArray): Brush {
        disc.setInputShader("art", map(art, Shader.TileMode.CLAMP))
        disc.setFloatUniform("artSize", art.width.toFloat())
        disc.setFloatUniform("eye", eye[0], eye[1], eye[2])
        disc.setFloatUniform("toWorld", toWorld)
        return discBrush
    }

    private fun map(image: ImageBitmap, tile: Shader.TileMode): BitmapShader = maps.getOrPut(image) {
        if (maps.size >= MAPS_KEPT) maps.clear()
        BitmapShader(image.asAndroidBitmap(), tile, tile).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
    }
}
