#version 330

// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

#ifdef SKYSOFT_RENDERPEARL
#extension GL_ARB_separate_shader_objects : require
#define SKYSOFT_LOCATION(index) layout(location = index)
#else
#define SKYSOFT_LOCATION(index)
#endif

SKYSOFT_LOCATION(0) in vec4 tint;
SKYSOFT_LOCATION(1) in vec4 shapeData;
SKYSOFT_LOCATION(2) in vec4 arcData;

SKYSOFT_LOCATION(0) out vec4 fragColor;

void main() {
    float feather = shapeData.y;
    float radius = min(shapeData.z, shapeData.w);
    vec2 fromCenter = gl_FragCoord.xy - arcData.xy;
    float distanceSquared = dot(fromCenter, fromCenter);
    float innerRadius = radius - feather;
    float edgeCoverage = 1.0 - smoothstep(innerRadius * innerRadius, radius * radius, distanceSquared);

    float angle = atan(fromCenter.y, fromCenter.x);
    float firstBoundary = arcData.z;
    float secondBoundary = arcData.w;
    bool crossesAngleWrap = firstBoundary > secondBoundary;
    bool insideArc = crossesAngleWrap
        ? angle <= firstBoundary && angle >= secondBoundary
        : angle <= firstBoundary || angle >= secondBoundary;
    float arcCoverage = insideArc ? 1.0 : 0.0;

    fragColor = vec4(tint.rgb, tint.a * edgeCoverage * arcCoverage);
}
