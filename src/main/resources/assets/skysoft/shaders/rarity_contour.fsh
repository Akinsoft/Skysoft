#version 330

#ifdef SKYSOFT_RENDERPEARL
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#define SKYSOFT_LOCATION(index) layout(location = index)
#else
#moj_import <minecraft:dynamictransforms.glsl>
#define SKYSOFT_LOCATION(index)
#endif

uniform sampler2D Sampler0;

SKYSOFT_LOCATION(0) in vec2 texCoord0;
SKYSOFT_LOCATION(1) in vec4 vertexColor;
SKYSOFT_LOCATION(2) in vec4 uvBounds;

SKYSOFT_LOCATION(0) out vec4 fragColor;

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
