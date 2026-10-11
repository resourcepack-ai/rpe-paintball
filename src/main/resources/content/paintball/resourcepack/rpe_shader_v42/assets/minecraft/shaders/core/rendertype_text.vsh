#version 150

#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

out vec4 rawColor;
out vec4 rpeScreen;
uniform sampler2D Sampler0;

void rpe_vertex_main() {
    // Un-multiplied, for the gate. See patch.ts.
    rawColor = Color;
    // Zero unless a full-screen glyph's own branch sets it.
    rpeScreen = vec4(0.0);
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}

void main() {
    rpe_vertex_main();

    // Perspective text (signs/nameplates) must never become a HUD.
    if (abs(ProjMat[3][3] - 1.0) < 0.001) {
      ivec3 mark = ivec3(floor(Color.rgb * 255.0 + 0.5));
      vec2 gui = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
      {
      vec2 delta = vec2(0.0);
      bool matched = false;
      if (mark.r == 254 && mark.g == 0 && mark.b == 1) {
        vec2 corner = step(3.5, floor(textureLod(Sampler0, UV0, 0.0).rg * 255.0 + 0.5));
        gl_Position = vec4((corner.x * 2.0 - 1.0) * gl_Position.w, (1.0 - corner.y * 2.0) * gl_Position.w, gl_Position.z, gl_Position.w);
        rpeScreen = vec4(corner * gui, gui);
      }
      if (mark.r == 240 && mark.g == 0 && mark.b == 8) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.275, 0.333, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 2 || mark.b == 1)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.069, 0.083, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 16) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        vertexColor = vec4(0.180, 0.608, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 4 || mark.b == 3)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.045, 0.152, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 24) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 6 || mark.b == 5)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 32) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.420, 0.467, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 8 || mark.b == 7)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.105, 0.117, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 40) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        vertexColor = vec4(0.361, 0.702, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 10 || mark.b == 9)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.090, 0.175, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 48) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.420, 0.467, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 12 || mark.b == 11)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.105, 0.117, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 56) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        vertexColor = vec4(0.361, 0.702, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 14 || mark.b == 13)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 16.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.090, 0.175, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 64) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 16 || mark.b == 15)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 72) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.420, 0.467, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 18 || mark.b == 17)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.105, 0.117, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 80) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        vertexColor = vec4(0.361, 0.702, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 20 || mark.b == 19)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.090, 0.175, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 88) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.420, 0.467, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 22 || mark.b == 21)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.105, 0.117, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 96) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        vertexColor = vec4(0.361, 0.702, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 24 || mark.b == 23)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 31.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.090, 0.175, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 104) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 26 || mark.b == 25)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 112) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.420, 0.467, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 28 || mark.b == 27)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.105, 0.117, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 120) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        vertexColor = vec4(0.361, 0.702, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 30 || mark.b == 29)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.090, 0.175, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 128) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.420, 0.467, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 32 || mark.b == 31)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.105, 0.117, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 136) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        vertexColor = vec4(0.361, 0.702, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 34 || mark.b == 33)) {
        delta = vec2((0.0 * gui.x + 6.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 46.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.090, 0.175, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 144) {
        delta = vec2((0.5 * gui.x + -48.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 19.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 36 || mark.b == 35)) {
        delta = vec2((0.5 * gui.x + -48.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 19.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 152) {
        delta = vec2((0.5 * gui.x + 48.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 19.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 38 || mark.b == 37)) {
        delta = vec2((0.5 * gui.x + 48.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 19.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 160) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 19.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 40 || mark.b == 39)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 19.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 168) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 38.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 42 || mark.b == 41)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.0 * gui.y + 38.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 176) {
        delta = vec2((1.0 * gui.x + -6.0) - (floor(gui.x / 2.0) - 4.0), (1.0 * gui.y + -35.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 44 || mark.b == 43)) {
        delta = vec2((1.0 * gui.x + -6.0) - (floor(gui.x / 2.0) - 4.0), (1.0 * gui.y + -35.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 184) {
        delta = vec2((1.0 * gui.x + -6.0) - (floor(gui.x / 2.0) - 4.0), (1.0 * gui.y + -38.0) - (gui.y + -64.0));
        vertexColor = vec4(0.604, 0.627, 0.651, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 46 || mark.b == 45)) {
        delta = vec2((1.0 * gui.x + -6.0) - (floor(gui.x / 2.0) - 4.0), (1.0 * gui.y + -38.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.151, 0.157, 0.163, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 192) {
        delta = vec2((1.0 * gui.x + -6.0) - (floor(gui.x / 2.0) - 4.0), (1.0 * gui.y + -38.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.824, 0.247, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 48 || mark.b == 47)) {
        delta = vec2((1.0 * gui.x + -6.0) - (floor(gui.x / 2.0) - 4.0), (1.0 * gui.y + -38.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.206, 0.062, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 200) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 50 || mark.b == 49)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 208) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 52 || mark.b == 51)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 216) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 54 || mark.b == 53)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 4.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 224) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 48.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 1.000, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 56 || mark.b == 55)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 48.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.250, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 232) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 61.0) - (gui.y + -64.0));
        vertexColor = vec4(1.000, 0.420, 0.467, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 58 || mark.b == 57)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 61.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.250, 0.105, 0.117, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
if (mark.r == 240 && mark.g == 0 && mark.b == 240) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 61.0) - (gui.y + -64.0));
        vertexColor = vec4(0.361, 0.702, 1.000, Color.a);
        rawColor = vec4(0.0); // Text always takes the vanilla fragment path.
        matched = true;
      } else if ((mark.r == 60 || mark.r == 59) && (mark.g == 0 || mark.g == -1) && (mark.b == 60 || mark.b == 59)) {
        delta = vec2((0.5 * gui.x + 0.0) - (floor(gui.x / 2.0) - 4.0), (0.5 * gui.y + 61.0) - (gui.y + -64.0));
        // A quarter of the authored colour, which is what a shadow is. Restored
        // rather than left as the dimmed MARK, or the ghost would be a shadow of
        // the address instead of a shadow of the label.
        vertexColor = vec4(0.090, 0.175, 0.250, Color.a);
        rawColor = vec4(0.0);
        matched = true;
      }
      if (matched) gl_Position.xy += (ProjMat * vec4(delta, 0.0, 0.0)).xy;
    }
    }
}
