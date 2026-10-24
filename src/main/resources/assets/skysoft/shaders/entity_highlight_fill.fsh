#version 330

#ifdef SKYSOFT_RENDERPEARL
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#define SKYSOFT_LOCATION(index) layout(location = index)
#else
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#define SKYSOFT_LOCATION(index)
#endif

uniform sampler2D Sampler0;

#ifndef OIT_ALPHA_ONLY
SKYSOFT_LOCATION(0) in float sphericalVertexDistance;
SKYSOFT_LOCATION(1) in float cylindricalVertexDistance;
#endif
SKYSOFT_LOCATION(2) in vec4 vertexColor;
SKYSOFT_LOCATION(3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
SKYSOFT_LOCATION(0) out vec4 fragColor;
#endif

void main() {
    vec4 color = vertexColor * ColorModulator;
    color.a *= texture(Sampler0, texCoord0).a;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif
    fragColor = apply_fog(
        color,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        fogColor
    );
#endif
}
