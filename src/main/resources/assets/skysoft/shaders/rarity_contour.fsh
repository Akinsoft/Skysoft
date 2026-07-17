#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec4 uvBounds;

out vec4 fragColor;

float itemAlpha(vec2 coordinate) {
    vec2 minimum = min(uvBounds.xy, uvBounds.zw);
    vec2 maximum = max(uvBounds.xy, uvBounds.zw);
    if (any(lessThanEqual(coordinate, minimum)) || any(greaterThanEqual(coordinate, maximum))) {
        return 0.0;
    }
    return texture(Sampler0, coordinate).a;
}

void main() {
    vec2 stepSize = abs(uvBounds.zw - uvBounds.xy) / 16.0;
    float neighboringAlpha = max(
        max(itemAlpha(texCoord0 + vec2(stepSize.x, 0.0)), itemAlpha(texCoord0 - vec2(stepSize.x, 0.0))),
        max(itemAlpha(texCoord0 + vec2(0.0, stepSize.y)), itemAlpha(texCoord0 - vec2(0.0, stepSize.y)))
    );
    neighboringAlpha = max(
        neighboringAlpha,
        max(
            max(itemAlpha(texCoord0 + stepSize), itemAlpha(texCoord0 - stepSize)),
            max(
                itemAlpha(texCoord0 + vec2(stepSize.x, -stepSize.y)),
                itemAlpha(texCoord0 + vec2(-stepSize.x, stepSize.y))
            )
        )
    );
    float contourAlpha = neighboringAlpha * (1.0 - itemAlpha(texCoord0));
    if (contourAlpha <= 0.0) {
        discard;
    }
    vec4 tint = vertexColor * ColorModulator;
    fragColor = vec4(tint.rgb, tint.a * contourAlpha);
}
