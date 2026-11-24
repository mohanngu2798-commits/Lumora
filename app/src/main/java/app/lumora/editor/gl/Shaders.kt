package app.lumora.editor.gl

internal object Shaders {
    const val VERTEX = """#version 300 es
in vec2 aPos;
uniform int uQuarterTurns;
uniform float uFlipH;
uniform float uFlipOutputY;   // 1.0 when rendering for glReadPixels (rows bottom-up)
uniform vec4 uCrop;           // x, y, w, h in normalized source space
out vec2 vUv;                 // where to sample the source
out vec2 vOut;                // 0..1 position in the output image, top-left origin

void main() {
    vec2 p = aPos * 0.5 + 0.5;
    vOut = vec2(p.x, 1.0 - p.y);
    if (uFlipOutputY > 0.5) vOut.y = 1.0 - vOut.y;

    vec2 q = vOut;
    if (uFlipH > 0.5) q.x = 1.0 - q.x;
    // Map an output position back to the un-rotated crop (clockwise rotation).
    if (uQuarterTurns == 1) q = vec2(q.y, 1.0 - q.x);
    else if (uQuarterTurns == 2) q = vec2(1.0 - q.x, 1.0 - q.y);
    else if (uQuarterTurns == 3) q = vec2(1.0 - q.y, q.x);

    vUv = uCrop.xy + q * uCrop.zw;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;
in vec2 vUv;
in vec2 vOut;
out vec4 fragColor;

uniform sampler2D uImage;
uniform sampler2D uCurve;
uniform vec2 uTexel;
uniform vec2 uOutSize;
uniform float uExposure, uContrast, uHighlights, uShadows;
uniform float uSaturation, uVibrance, uWarmth, uTint;
uniform float uFade, uSharpen, uVignette, uGrain;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec3 c = texture(uImage, vUv).rgb;

    // Detail: simple unsharp mask from four neighbours.
    if (uSharpen > 0.0) {
        vec3 blur = (texture(uImage, vUv + vec2(uTexel.x, 0.0)).rgb +
                     texture(uImage, vUv - vec2(uTexel.x, 0.0)).rgb +
                     texture(uImage, vUv + vec2(0.0, uTexel.y)).rgb +
                     texture(uImage, vUv - vec2(0.0, uTexel.y)).rgb) * 0.25;
        c = clamp(c + (c - blur) * uSharpen * 1.5, 0.0, 1.0);
    }

    // Exposure and white balance in (approximately) linear light.
    vec3 lin = pow(c, vec3(2.2));
    lin *= exp2(uExposure);
    lin *= vec3(1.0 + 0.25 * uWarmth + 0.08 * uTint, 1.0 - 0.15 * uTint, 1.0 - 0.25 * uWarmth + 0.08 * uTint);
    c = pow(max(lin, 0.0), vec3(1.0 / 2.2));

    // Shadows / highlights recovery with smooth luminance masks.
    float l = luma(c);
    float sMask = 1.0 - smoothstep(0.0, 0.55, l);
    float hMask = smoothstep(0.45, 1.0, l);
    c += uShadows * 0.35 * sMask * (uShadows > 0.0 ? (1.0 - c) : c);
    c += uHighlights * 0.35 * hMask * (uHighlights > 0.0 ? (1.0 - c) : c);

    // Contrast around mid grey.
    c = (c - 0.5) * (1.0 + uContrast) + 0.5;
    c = clamp(c, 0.0, 1.0);

    // Tone curves (master folded into each channel on the CPU).
    c = vec3(texture(uCurve, vec2(c.r, 0.5)).r,
             texture(uCurve, vec2(c.g, 0.5)).g,
             texture(uCurve, vec2(c.b, 0.5)).b);

    // Colour.
    l = luma(c);
    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(l), c, 1.0 + uVibrance * (1.0 - sat));
    c = mix(vec3(l), c, 1.0 + uSaturation);

    // Fade lifts blacks and softens whites.
    c = mix(c, c * 0.82 + 0.12, uFade);

    // Vignette, measured in output space so it follows the crop.
    float d = distance(vOut, vec2(0.5)) * 1.414;
    float v = smoothstep(0.35, 1.0, d);
    if (uVignette > 0.0) c *= 1.0 - v * uVignette * 0.85;
    else c = mix(c, vec3(1.0), v * -uVignette * 0.6);

    // Film grain, stable per output pixel.
    c += (hash(floor(vOut * uOutSize)) - 0.5) * uGrain * 0.18;

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
}
