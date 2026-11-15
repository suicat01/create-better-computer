#version 150

// 枪炮手的瞄准组件：老式摄影机滤镜
// 保持原版分辨率，只做去色（偏灰）、降低对比度、轻微暖调、胶片颗粒与扫描线。

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;

    // 去色：只保留一点点原有色彩
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(gray), color, 0.12);

    // 压低对比度并整体提亮，让画面发灰
    color = color * 0.82 + 0.12;

    // 轻微暖调，模拟老胶片
    color *= vec3(1.03, 1.0, 0.95);

    // 胶片颗粒：按屏幕像素打散
    float grain = fract(sin(dot(floor(texCoord * InSize), vec2(12.9898, 78.233)) + Time * 37.0) * 43758.5453);
    color += (grain - 0.5) * 0.03;

    // 扫描线：每 3 行压暗一行
    float scanline = mod(floor(texCoord.y * InSize.y), 3.0) < 1.0 ? 0.97 : 1.0;
    color *= scanline;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
