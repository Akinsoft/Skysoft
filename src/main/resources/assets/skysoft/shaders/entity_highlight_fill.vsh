#version 330

#ifdef SKYSOFT_RENDERPEARL
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#define SKYSOFT_LOCATION(index) layout(location = index)
#else
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#define SKYSOFT_LOCATION(index)
#endif

SKYSOFT_LOCATION(0) in vec3 Position;
SKYSOFT_LOCATION(1) in vec4 Color;
SKYSOFT_LOCATION(2) in vec2 UV0;

SKYSOFT_LOCATION(0) out float sphericalVertexDistance;
SKYSOFT_LOCATION(1) out float cylindricalVertexDistance;
SKYSOFT_LOCATION(2) out vec4 vertexColor;
SKYSOFT_LOCATION(3) out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
    texCoord0 = UV0;
}
