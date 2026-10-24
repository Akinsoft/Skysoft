#version 330

// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

#ifdef SKYSOFT_RENDERPEARL
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#define SKYSOFT_LOCATION(index) layout(location = index)
#else
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#define SKYSOFT_LOCATION(index)
#endif

SKYSOFT_LOCATION(0) in vec3 Position;
SKYSOFT_LOCATION(1) in vec4 Color;
SKYSOFT_LOCATION(2) in vec4 RoundedParams0;
SKYSOFT_LOCATION(3) in vec4 RoundedParams1;

SKYSOFT_LOCATION(0) out vec4 tint;
SKYSOFT_LOCATION(1) out vec4 shapeData;
SKYSOFT_LOCATION(2) out vec4 arcData;

void main() {
    vec4 worldPosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * worldPosition;
    tint = Color;
    shapeData = RoundedParams0;
    arcData = RoundedParams1;
}
