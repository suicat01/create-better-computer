#version 150

// 枪炮手的瞄准组件：色彩修复附魔后的滤镜
// 保留完整色彩，只保留胶片颗粒与扫描线。

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;

    // 色彩修复：不做去色、压暗与暖调，直接保留原本色彩

    // 胶片颗粒：按屏幕像素打散
    float grain = fract(sin(dot(floor(texCoord * InSize), vec2(12.9898, 78.233)) + Time * 37.0) * 43758.5453);
    color += (grain - 0.5) * 0.03;

    // 扫描线：每 3 行压暗一行
    float scanline = mod(floor(texCoord.y * InSize.y), 3.0) < 1.0 ? 0.97 : 1.0;
    color *= scanline;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
