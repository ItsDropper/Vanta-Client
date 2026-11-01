#version 330

uniform sampler2D Sample0Sampler;
uniform sampler2D Sample1Sampler;

layout(std140) uniform MotionBlurUniforms {
    vec4 BlendParams;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 currentFrame = texture(Sample0Sampler, texCoord);
    vec4 previousFrame = texture(Sample1Sampler, texCoord);
    float retention = clamp(BlendParams.x, 0.0, 0.95);
    float intensity = clamp(BlendParams.y, 0.0, 1.0);
    fragColor = vec4(mix(currentFrame.rgb, previousFrame.rgb, retention * intensity), currentFrame.a);
}
