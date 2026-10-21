package io.github.mejdi14.oddscroll.gallerywall

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The glass, a pixel at a time. Everything here is in card heights across the
 * pane, so it reads the same from across the room as up close.
 *
 * - The room it throws back runs between the three amounts worked out for the
 *   piece, a touch more of it towards the top, where the ceiling is lit, and
 *   less towards the floor.
 * - The doorway is a soft-cornered opening rather than a blob, brighter at its
 *   head where the daylight comes in, with a faint bloom round it.
 * - On the glass itself: soft smudges, the arcs a cloth leaves, and dust. They
 *   only show where light catches them — in the doorway, and where the glass
 *   is turned away and throwing back most of the room — which is what makes
 *   them read as on the surface rather than in the painting.
 * - Along the sight edge the moulding shows in the glass as a darker band, and
 *   the pane's own edge as a hairline of bottle green.
 *
 * Dust and the cloth's arcs are finer than a pixel from across the room, so
 * they fade in with the pixels a card height gets rather than sparkle.
 */
private const val SOURCE = """
uniform float2 origin;
uniform float2 pane;
uniform float unit;
uniform float span;
uniform float3 fresnel;
uniform float4 wall;
uniform float4 door;
uniform float glow;
uniform float seed;

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx + seed * float3(17.31, 9.73, 13.19)) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
        mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(float2 p) {
    return 0.5 * noise(p) + 0.3 * noise(p * 2.03 + 5.1) + 0.2 * noise(p * 4.01 + 1.7);
}

half4 main(float2 xy) {
    float2 g = (xy - origin) / unit;
    float edge = min(min(g.x, pane.x - g.x), min(g.y, pane.y - g.y));

    float t = clamp(xy.x / span, 0.0, 1.0);
    float room = t < 0.5 ? mix(fresnel.x, fresnel.y, t * 2.0) : mix(fresnel.y, fresnel.z, t * 2.0 - 1.0);
    room *= 1.15 - 0.3 * clamp(g.y / pane.y, 0.0, 1.0);

    float lit = 0.0;
    if (glow > 0.0) {
        float2 d = abs(g - door.xy) - door.zw;
        float sd = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
        float inside = 1.0 - smoothstep(-0.03, 0.03, sd);
        float head = clamp((g.y - (door.y - door.w)) / (2.0 * door.w), 0.0, 1.0);
        lit = glow * (inside * mix(1.0, 0.5, head) + 0.3 * exp(-max(sd, 0.0) / 0.08));
    }

    float catchLight = room * 2.5 + lit * 3.0;
    float fine = smoothstep(250.0, 700.0, unit);
    float smudge = smoothstep(0.55, 0.85, fbm(g * 5.0)) * 0.06;
    float2 hub = float2(hash(float2(1.0, 2.0)), hash(float2(3.0, 4.0))) * pane;
    float swirl = pow(abs(sin(length(g - hub) * 120.0 + fbm(g * 3.0) * 6.0)), 12.0)
        * smoothstep(0.45, 0.75, fbm(g * 2.2 + 11.0)) * 0.05 * fine;
    float2 cell = floor(g * 160.0);
    float2 at = float2(hash(cell + 7.1), hash(cell + 3.3)) * 0.6 + 0.2;
    float speck = step(0.992, hash(cell))
        * (1.0 - smoothstep(0.08, 0.08 + 160.0 / unit, length(fract(g * 160.0) - at)))
        * 0.4 * fine;
    float marks = (smudge + swirl + speck) * catchLight;

    float rim = (1.0 - smoothstep(0.0, 0.014, edge)) * 0.22;
    float green = (1.0 - smoothstep(0.0, 0.0025 + 1.0 / unit, edge)) * 0.35;

    // Built up back to front, premultiplied.
    float a = clamp(room, 0.0, 1.0);
    float3 color = wall.rgb * a;
    float l = clamp(lit + marks, 0.0, 1.0);
    color = color * (1.0 - l) + float3(1.0, 0.985, 0.95) * l;
    a += l * (1.0 - a);
    color *= 1.0 - rim;
    a += rim * (1.0 - a);
    color = color * (1.0 - green) + float3(0.16, 0.26, 0.22) * green;
    a += green * (1.0 - a);
    return half4(color, a);
}
"""

/**
 * AGSL arrived in 33; below that, and if the shader fails to compile at all,
 * the glass is drawn plainly in common code.
 */
internal actual fun glassShader(): GlassShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { AgslGlass() }.getOrNull()
}

/**
 * One shader for every pane. Setting a uniform drops the shader's native
 * instance, so each draw takes its own snapshot and the pieces drawn before it
 * keep theirs.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslGlass : GlassShader {
    private val shader = RuntimeShader(SOURCE)
    private val brush = ShaderBrush(shader)

    override fun brush(
        originX: Float,
        originY: Float,
        paneWidth: Float,
        paneHeight: Float,
        unit: Float,
        span: Float,
        near: Float,
        middle: Float,
        far: Float,
        wall: Color,
        doorX: Float,
        doorY: Float,
        doorWidth: Float,
        doorHeight: Float,
        glow: Float,
        seed: Int,
    ): Brush {
        shader.setFloatUniform("origin", originX, originY)
        shader.setFloatUniform("pane", paneWidth, paneHeight)
        shader.setFloatUniform("unit", unit)
        shader.setFloatUniform("span", span)
        shader.setFloatUniform("fresnel", near, middle, far)
        shader.setFloatUniform("wall", wall.red, wall.green, wall.blue, 1f)
        shader.setFloatUniform("door", doorX, doorY, doorWidth, doorHeight)
        shader.setFloatUniform("glow", glow)
        shader.setFloatUniform("seed", seed.toFloat())
        return brush
    }
}
