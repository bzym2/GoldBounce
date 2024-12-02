#version 130

uniform float offset;
uniform vec2 strength;
uniform float speed;
uniform int maxColors;
uniform vec4 colors[9];

// TODO: Fix Random Stutter
void main() {
    vec2 pos = gl_FragCoord.xy * strength;
    float param = mod(pos.x + pos.y + offset * speed, 1.0);

    // Divide the range [0, 1] based on maxColors
    float segment = 1.0 / maxColors;
    float index = param / segment;
    float frac = fract(index);

    int idx1 = int(index) % maxColors;
    int idx2 = (idx1 + 1) % maxColors;

    vec4 gradientColor = mix(colors[idx1], colors[idx2], smoothstep(0.0, 1.0, frac));

    gl_FragColor = gradientColor;
}