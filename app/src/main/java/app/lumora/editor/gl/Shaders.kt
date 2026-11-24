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
uniform sampler2D uBlurM;     // medium blur of the source (small texture, upsampled)
uniform sampler2D uBlurL;     // large blur of the source
uniform vec2 uBlurMTexel, uBlurLTexel;
uniform float uClarity, uDehaze, uHdr, uGlow, uLensBlur, uLeak;
uniform int uBlurShape;       // 0 = radial, 1 = linear band
uniform vec3 uLeakA, uLeakB;
uniform float uOutAspect;     // output width / height

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

// 9-tap tent filter over a small blurred texture, so upsampling it stays smooth.
vec3 soft(sampler2D t, vec2 uv, vec2 texel) {
    vec3 s = texture(t, uv).rgb * 4.0;
    s += (texture(t, uv + vec2(texel.x, 0.0)).rgb + texture(t, uv - vec2(texel.x, 0.0)).rgb +
          texture(t, uv + vec2(0.0, texel.y)).rgb + texture(t, uv - vec2(0.0, texel.y)).rgb) * 2.0;
    s += texture(t, uv + texel).rgb + texture(t, uv - texel).rgb +
         texture(t, uv + vec2(texel.x, -texel.y)).rgb + texture(t, uv - vec2(texel.x, -texel.y)).rgb;
    return s / 16.0;
}
vec3 screen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - b); }

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

    bool needM = uClarity != 0.0 || uGlow > 0.0 || uLensBlur > 0.0;
    bool needL = uDehaze != 0.0 || uHdr > 0.0 || uLensBlur > 0.0;
    vec3 bm = needM ? soft(uBlurM, vUv, uBlurMTexel) : c;
    vec3 bl = needL ? soft(uBlurL, vUv, uBlurLTexel) : c;

    // Lens blur: keep a circle or a horizontal band sharp, blur the rest.
    if (uLensBlur > 0.0) {
        vec2 o = vOut - 0.5;
        float d = uBlurShape == 0 ? length(vec2(o.x * uOutAspect, o.y)) / max(uOutAspect, 1.0) * 1.6 : abs(o.y) * 2.0;
        float m = smoothstep(0.25, 0.85, d) * uLensBlur;
        c = mix(c, bm, clamp(m * 2.0, 0.0, 1.0));
        c = mix(c, bl, clamp(m * 2.0 - 1.0, 0.0, 1.0));
        bm = mix(bm, bl, m);
    }

    // Clarity: midtone-weighted local contrast.
    if (uClarity != 0.0) {
        float lc = luma(c);
        float w = 1.0 - pow(abs(lc - 0.5) * 2.0, 2.0);
        c += (lc - luma(bm)) * uClarity * 1.4 * (0.35 + 0.65 * w);
        c = clamp(c, 0.0, 1.0);
    }

    // Dehaze from a dark-channel estimate of the haze.
    if (uDehaze > 0.0) {
        float dark = min(bl.r, min(bl.g, bl.b));
        float t = max(1.0 - uDehaze * 0.85 * dark, 0.3);
        c = clamp((c - (1.0 - t) * 0.9) / t, 0.0, 1.0);
        c = mix(vec3(luma(c)), c, 1.0 + uDehaze * 0.25);
    } else if (uDehaze < 0.0) {
        c = mix(c, vec3(0.82, 0.84, 0.86), -uDehaze * 0.45);
    }

    // HDR / drama: big-radius local contrast plus shadow lift.
    if (uHdr > 0.0) {
        c += (c - bl) * uHdr * 1.6;
        float lh = clamp(luma(c), 0.0, 1.0);
        c = mix(c, sqrt(max(c, 0.0)), uHdr * 0.3 * (1.0 - lh));
        c = clamp(c, 0.0, 1.0);
    }

    // Glow: soft bloom from the bright parts.
    if (uGlow > 0.0) {
        float bright = smoothstep(0.35, 1.0, luma(bm));
        c = screen(c, bm * bright * uGlow * 0.9);
        c = mix(c, bm, uGlow * 0.12);
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

    // Light leak: two warm colours washing in from the corners.
    if (uLeak > 0.0) {
        float a = smoothstep(0.95, 0.0, distance(vOut, vec2(1.05, -0.05)));
        float b = smoothstep(0.85, 0.0, distance(vOut, vec2(-0.05, 1.0)));
        vec3 leak = uLeakA * a + uLeakB * b * 0.8;
        c = screen(c, leak * uLeak);
    }

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
