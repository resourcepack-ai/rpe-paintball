#version 330

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#endif

in vec4 vertexColor;
in vec2 texCoord0;

// rpai-merge:begin fragment-declarations v1
in vec4 rawColor;
in vec4 rpeScreen;
// rpai-merge:end fragment-declarations
// rpai-merge:begin fragment-vanilla v1
// #moj_import <minecraft:dynamictransforms.glsl>
// uniform sampler2D Sampler0;
// in vec4 vertexColor;
// in vec2 texCoord0;
// out vec4 fragColor;
// rpai-merge:end fragment-vanilla

out vec4 fragColor;

// Vanilla's own, untouched below this line. Called by our main when the
// gate says a fragment is not one of ours — which is nearly all of them.
void rpe_vanilla_main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif

#ifdef IS_SEE_THROUGH
    vec4 color = texColor * vertexColor;
#else
    vec4 color = texColor * vertexColor * ColorModulator;
#endif
    if (color.a < 0.1) {
        discard;
    }

#ifdef IS_SEE_THROUGH
    fragColor = color * ColorModulator;
#elif defined(IS_GUI)
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}

// rpai-merge:begin fragment-epilogue v1
// rpai-merge:renames main rpe_vanilla_main

const float SIG_R = 0.996078;
const float SIG_B = 0.003922;
const float TOL = 0.001961;

// --- primitives -------------------------------------------------------------

float sdCircle(vec2 p, float r) {
    return length(p) - r;
}

// Outer radius r, wall thickness t growing inward.
float sdRing(vec2 p, float r, float t) {
    float mid = r - t * 0.5;
    return abs(length(p) - mid) - t * 0.5;
}

float sdBox(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

// A box inset by the corner radius, then grown back out by it — which is what
// makes the corners round without a separate case.
float sdRoundedBox(vec2 p, vec2 b, float r) {
    r = min(r, min(b.x, b.y));
    return sdBox(p, b - vec2(r)) - r;
}

float sdRegularPolygon(vec2 p, float r, float n) {
    float an = 3.141592653589793 / n;
    vec2 acs = vec2(cos(an), sin(an));
    float bn = mod(atan(p.x, p.y), 2.0 * an) - an;
    p = length(p) * vec2(cos(bn), abs(sin(bn)));
    p -= r * acs;
    p.y += clamp(-p.y, 0.0, r * acs.y);
    return length(p) * sign(p.x);
}

// --- operators --------------------------------------------------------------

float opUnion(float a, float b) { return min(a, b); }
float opSubtract(float a, float b) { return max(a, -b); }
float opIntersect(float a, float b) { return max(a, b); }

// The polynomial smooth-min. `h` is also what the caller blends colour by, so
// the seam between two shapes carries a blend of their two colours rather than
// a hard switch at the midpoint.
float opSmoothUnion(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.0001), 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float smoothUnionBlend(float a, float b, float k) {
    return clamp(0.5 + 0.5 * (b - a) / max(k, 0.0001), 0.0, 1.0);
}

vec2 rotate2d(vec2 p, float degrees) {
    float a = radians(degrees);
    float s = sin(a);
    float c = cos(a);
    return vec2(p.x * c - p.y * s, p.x * s + p.y * c);
}


// ---- Paintball HUD (signature 0, full screen) ----
// Drawn at rpeScreen: the fragment's own position in GUI pixels, which the
// vertex shader interpolates across the glyph it stretched over the screen.
// No texel lookup and no canvas to clip to.

// d: distance to the composed surface. col: its colour at this fragment.
void scene_0(vec2 p, vec2 screen, out float d, out vec3 col, out float shapeAlpha) {
    vec2 stretchK = screen / vec2(640.0, 360.0);
    float stretchS = min(stretchK.x, stretchK.y);
    d = 1e6;
    col = vec3(0.0);
    shapeAlpha = 0.0;

    // box #1 (union)
    float d0 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(-48.0, 14.0))), (vec2(25.0, 10.0)));
    col = mix(col, vec3(1.0, 0.2745, 0.3333), step(d0, d));
    shapeAlpha = mix(shapeAlpha, 0.92, step(d0, d));
    d = opUnion(d, d0);

    // box #2 (union)
    float d1 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(-48.0, 23.5))), (vec2(25.0, 0.5)));
    col = mix(col, vec3(0.6392, 0.1255, 0.1725), step(d1, d));
    shapeAlpha = mix(shapeAlpha, 0.95, step(d1, d));
    d = opUnion(d, d1);

    // box #3 (union)
    float d2 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(48.0, 14.0))), (vec2(25.0, 10.0)));
    col = mix(col, vec3(0.1804, 0.6078, 1.0), step(d2, d));
    shapeAlpha = mix(shapeAlpha, 0.92, step(d2, d));
    d = opUnion(d, d2);

    // box #4 (union)
    float d3 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(48.0, 23.5))), (vec2(25.0, 0.5)));
    col = mix(col, vec3(0.0824, 0.3686, 0.6196), step(d3, d));
    shapeAlpha = mix(shapeAlpha, 0.95, step(d3, d));
    d = opUnion(d, d3);

    // roundedBox #5 (union)
    float d4 = sdRoundedBox((p - (vec2(0.5, 0.0) * screen + vec2(0.0, 14.0))), (vec2(21.0, 10.0)), (2.0));
    col = mix(col, vec3(0.051, 0.0667, 0.0902), step(d4, d));
    shapeAlpha = mix(shapeAlpha, 0.88, step(d4, d));
    d = opUnion(d, d4);
}

// ---- Paintball HUD (free for all) (signature 1, full screen) ----
// Drawn at rpeScreen: the fragment's own position in GUI pixels, which the
// vertex shader interpolates across the glyph it stretched over the screen.
// No texel lookup and no canvas to clip to.

// d: distance to the composed surface. col: its colour at this fragment.
void scene_1(vec2 p, vec2 screen, out float d, out vec3 col, out float shapeAlpha) {
    vec2 stretchK = screen / vec2(640.0, 360.0);
    float stretchS = min(stretchK.x, stretchK.y);
    d = 1e6;
    col = vec3(0.0);
    shapeAlpha = 0.0;

    // box #1 (union)
    float d0 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(-48.0, 14.0))), (vec2(25.0, 10.0)));
    col = mix(col, vec3(0.2275, 0.2627, 0.3137), step(d0, d));
    shapeAlpha = mix(shapeAlpha, 0.92, step(d0, d));
    d = opUnion(d, d0);

    // box #2 (union)
    float d1 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(-48.0, 23.5))), (vec2(25.0, 0.5)));
    col = mix(col, vec3(0.9098, 0.9176, 0.9294), step(d1, d));
    shapeAlpha = mix(shapeAlpha, 0.95, step(d1, d));
    d = opUnion(d, d1);

    // box #3 (union)
    float d2 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(48.0, 14.0))), (vec2(25.0, 10.0)));
    col = mix(col, vec3(1.0, 0.8235, 0.2471), step(d2, d));
    shapeAlpha = mix(shapeAlpha, 0.92, step(d2, d));
    d = opUnion(d, d2);

    // box #4 (union)
    float d3 = sdBox((p - (vec2(0.5, 0.0) * screen + vec2(48.0, 23.5))), (vec2(25.0, 0.5)));
    col = mix(col, vec3(0.7216, 0.5647, 0.1098), step(d3, d));
    shapeAlpha = mix(shapeAlpha, 0.95, step(d3, d));
    d = opUnion(d, d3);

    // roundedBox #5 (union)
    float d4 = sdRoundedBox((p - (vec2(0.5, 0.0) * screen + vec2(0.0, 14.0))), (vec2(21.0, 10.0)), (2.0));
    col = mix(col, vec3(0.051, 0.0667, 0.0902), step(d4, d));
    shapeAlpha = mix(shapeAlpha, 0.88, step(d4, d));
    d = opUnion(d, d4);
}

// ---- Go Karts HUD (signature 2, full screen) ----
// Drawn at rpeScreen: the fragment's own position in GUI pixels, which the
// vertex shader interpolates across the glyph it stretched over the screen.
// No texel lookup and no canvas to clip to.

// d: distance to the composed surface. col: its colour at this fragment.
void scene_2(vec2 p, vec2 screen, out float d, out vec3 col, out float shapeAlpha) {
    vec2 stretchK = screen / vec2(640.0, 360.0);
    float stretchS = min(stretchK.x, stretchK.y);
    d = 1e6;
    col = vec3(0.0);
    shapeAlpha = 0.0;

    // roundedBox #1 (union)
    float d0 = sdRoundedBox((p - (vec2(1.0, 0.0) * screen + vec2(-40.0, 55.0))), (vec2(36.0, 12.0)), (4.0));
    col = mix(col, vec3(0.0471, 0.0627, 0.0863), step(d0, d));
    shapeAlpha = mix(shapeAlpha, 0.55, step(d0, d));
    d = opUnion(d, d0);

    // roundedBox #2 (union)
    float d1 = sdRoundedBox((p - (vec2(1.0, 0.5) * screen + vec2(-56.0, -2.0))), (vec2(52.0, 46.0)), (4.0));
    col = mix(col, vec3(0.0471, 0.0627, 0.0863), step(d1, d));
    shapeAlpha = mix(shapeAlpha, 0.45, step(d1, d));
    d = opUnion(d, d1);
}

void dispatch(int id, vec2 cell, vec2 sub, out float d, out vec3 col, out float a) {
    d = 1e6;
    col = vec3(0.0);
    a = 0.0;
    if (id == 0) {
        vec2 screen = rpeScreen.zw;
        vec2 stretchK = screen / vec2(640.0, 360.0);
        float stretchS = min(stretchK.x, stretchK.y);
        scene_0(rpeScreen.xy, screen, d, col, a);
        float feather = max(max(max(max(max(1.0, 1.0), 1.0), 1.0), 1.0), 1.0);
        a *= 1.0 - smoothstep(-feather * 0.5, feather * 0.5, d);
        return;
    }
    if (id == 1) {
        vec2 screen = rpeScreen.zw;
        vec2 stretchK = screen / vec2(640.0, 360.0);
        float stretchS = min(stretchK.x, stretchK.y);
        scene_1(rpeScreen.xy, screen, d, col, a);
        float feather = max(max(max(max(max(1.0, 1.0), 1.0), 1.0), 1.0), 1.0);
        a *= 1.0 - smoothstep(-feather * 0.5, feather * 0.5, d);
        return;
    }
    if (id == 2) {
        vec2 screen = rpeScreen.zw;
        vec2 stretchK = screen / vec2(640.0, 360.0);
        float stretchS = min(stretchK.x, stretchK.y);
        scene_2(rpeScreen.xy, screen, d, col, a);
        float feather = max(max(1.0, 1.0), 1.0);
        a *= 1.0 - smoothstep(-feather * 0.5, feather * 0.5, d);
        return;
    }
}

void main() {
    // THE GATE. Anything that is not one of our overlays leaves here through
    // vanilla's own code, which is why that code is kept rather than rewritten.
    if (abs(rawColor.r - SIG_R) > TOL || abs(rawColor.b - SIG_B) > TOL) {
        rpe_vanilla_main();
        return;
    }

    // Ours. The green channel is which object.
    int id = int(rawColor.g * 255.0 + 0.5);

    // WHERE IN THE CANVAS THIS FRAGMENT IS, in two halves.
    //
    // texCoord0 is NOT 0..1 over the glyph. Every glyph the game draws is
    // stitched into a shared font atlas page, so texCoord0 is a small
    // sub-rectangle of that page at an address assigned at runtime — nothing
    // the pack can know. Reading it as a canvas UV puts every shape in a corner
    // a few pixels across, which renders as an overlay that draws nothing at
    // all.
    //
    // So the COARSE half is read out of the canvas glyph's own texels: the
    // texture is painted with its own texel coordinates, red across and green
    // down, and one texture read gives back the texel this fragment landed on
    // wherever the atlas put it.
    //
    // The FINE half is where inside that texel we are, which is exactly the
    // fractional part of the atlas coordinate — the atlas's own size is the
    // only thing needed to recover it, and the shader can ask for that. This is
    // what keeps the edge a true anti-aliased one at any GUI scale rather than
    // a staircase at texel resolution.
    //
    // The alternative, gl_FragCoord against ScreenSize, needs the GUI scale
    // inferred — exact only for the default "Auto" setting — and would still
    // not know where on the screen the engine chose to draw the line.
    vec2 atlas = vec2(textureSize(Sampler0, 0));
    vec4 canvasTexel = texture(Sampler0, texCoord0);
    vec2 cell = floor(canvasTexel.rg * 255.0 + 0.5);
    vec2 sub = fract(texCoord0 * atlas);

    float d;
    vec3 shapeCol;
    float shapeAlpha;
    dispatch(id, cell, sub, d, shapeCol, shapeAlpha);

    // The canvas glyph's own COLOUR is not drawn: those texels are a coordinate
    // rather than a picture, and they are near-transparent so that the shadow
    // pass — which fails the gate above and goes through vanilla's own code —
    // discards them. The scene IS the output.
    //
    // An object's TEXT is not drawn through here — the engine sends it as
    // ordinary text in an ordinary colour, which fails the gate above.
    if (shapeAlpha < 0.01) {
        discard;
    }
    fragColor = vec4(shapeCol, shapeAlpha);
}
// rpai-merge:end fragment-epilogue
