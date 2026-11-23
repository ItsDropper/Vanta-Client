#version 330

uniform sampler2D InSampler;

layout(std140) uniform LobbyGradeUniforms {
    vec4 GradeParams;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(InSampler, texCoord);
    float strength = clamp(GradeParams.x, 0.0, 1.0);
    float saturation = max(0.0, GradeParams.y);
    float contrast = max(0.0, 1.0 + GradeParams.z * strength);
    float vignetteStrength = clamp(GradeParams.w * strength, 0.0, 0.5);

    float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 graded = mix(vec3(luminance), source.rgb, mix(1.0, saturation, strength));
    graded = (graded - 0.5) * contrast + 0.5;
    graded += vec3(0.014, 0.006, -0.006) * strength;

    vec2 centered = (texCoord - vec2(0.5)) * vec2(1.0, 0.78);
    float edge = smoothstep(0.34, 0.92, length(centered));
    graded *= 1.0 - edge * vignetteStrength;

    fragColor = vec4(clamp(graded, 0.0, 1.0), source.a);
}
