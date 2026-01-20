package org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets;

import net.minecraft.resources.ResourceLocation;

public final class ShipWaterPocketShaderInjector {

    private ShipWaterPocketShaderInjector() {}

    private static final String INJECT_MARKER = "ValkyrienAir_CullEnabled";

    private static final String EMBEDDIUM_VERTEX_DECLS =
        "\n" +
            "out vec3 valkyrienair_CamRelPos;\n";

    private static final String EMBEDDIUM_FRAGMENT_DECLS =
        "\n" +
            "in vec3 valkyrienair_CamRelPos;\n" +
            "\n" +
            "uniform float ValkyrienAir_CullEnabled;\n" +
            "uniform float ValkyrienAir_IsShipPass;\n" +
            "uniform vec3 ValkyrienAir_CameraWorldPos;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_WaterStillUv;\n" +
            "uniform vec4 ValkyrienAir_WaterFlowUv;\n" +
            "uniform vec4 ValkyrienAir_WaterOverlayUv;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin0;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax0;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos0;\n" +
            "uniform vec4 ValkyrienAir_GridMin0;\n" +
            "uniform vec4 ValkyrienAir_GridSize0;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip0;\n" +
            "uniform usampler2D ValkyrienAir_AirMask0;\n" +
            "uniform usampler2D ValkyrienAir_OccMask0;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin1;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax1;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos1;\n" +
            "uniform vec4 ValkyrienAir_GridMin1;\n" +
            "uniform vec4 ValkyrienAir_GridSize1;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip1;\n" +
            "uniform usampler2D ValkyrienAir_AirMask1;\n" +
            "uniform usampler2D ValkyrienAir_OccMask1;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin2;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax2;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos2;\n" +
            "uniform vec4 ValkyrienAir_GridMin2;\n" +
            "uniform vec4 ValkyrienAir_GridSize2;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip2;\n" +
            "uniform usampler2D ValkyrienAir_AirMask2;\n" +
            "uniform usampler2D ValkyrienAir_OccMask2;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin3;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax3;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos3;\n" +
            "uniform vec4 ValkyrienAir_GridMin3;\n" +
            "uniform vec4 ValkyrienAir_GridSize3;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip3;\n" +
            "uniform usampler2D ValkyrienAir_AirMask3;\n" +
            "uniform usampler2D ValkyrienAir_OccMask3;\n" +
            "\n" +
            "const int VA_MASK_TEX_WIDTH_SHIFT = 12;\n" +
            "const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;\n" +
            "\n" +
            "const int VA_SUB = 8;\n" +
            "const int VA_OCC_WORDS_PER_VOXEL = 16;\n" +
            "\n" +
            "bool va_inUv(vec2 uv, vec4 bounds) {\n" +
            "    return uv.x >= bounds.x && uv.x <= bounds.z && uv.y >= bounds.y && uv.y <= bounds.w;\n" +
            "}\n" +
            "\n" +
            "bool va_isWaterUv(vec2 uv) {\n" +
            "    return va_inUv(uv, ValkyrienAir_WaterStillUv) ||\n" +
            "        va_inUv(uv, ValkyrienAir_WaterFlowUv) ||\n" +
            "        va_inUv(uv, ValkyrienAir_WaterOverlayUv);\n" +
            "}\n" +
            "\n" +
            "uint va_fetchWord(usampler2D tex, int wordIndex) {\n" +
            "    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);\n" +
            "    return texelFetch(tex, coord, 0).r;\n" +
            "}\n" +
            "\n" +
            "bool va_testAir(usampler2D airMask, int voxelIdx) {\n" +
            "    int wordIndex = voxelIdx >> 5;\n" +
            "    int bit = voxelIdx & 31;\n" +
            "    uint word = va_fetchWord(airMask, wordIndex);\n" +
            "    return ((word >> uint(bit)) & 1u) != 0u;\n" +
            "}\n" +
            "\n" +
            "bool va_testOcc(usampler2D occMask, int voxelIdx, int subIdx) {\n" +
            "    int wordIndex = voxelIdx * VA_OCC_WORDS_PER_VOXEL + (subIdx >> 5);\n" +
            "    int bit = subIdx & 31;\n" +
            "    uint word = va_fetchWord(occMask, wordIndex);\n" +
            "    return ((word >> uint(bit)) & 1u) != 0u;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip0(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize0.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin0.x || worldPos.x > ValkyrienAir_ShipAabbMax0.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin0.y || worldPos.y > ValkyrienAir_ShipAabbMax0.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin0.z || worldPos.z > ValkyrienAir_ShipAabbMax0.z) return false;\n" +
            "\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip0 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos0;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin0.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize0.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask0, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask0, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip1(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize1.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin1.x || worldPos.x > ValkyrienAir_ShipAabbMax1.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin1.y || worldPos.y > ValkyrienAir_ShipAabbMax1.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin1.z || worldPos.z > ValkyrienAir_ShipAabbMax1.z) return false;\n" +
            "\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip1 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos1;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin1.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize1.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask1, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask1, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip2(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize2.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin2.x || worldPos.x > ValkyrienAir_ShipAabbMax2.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin2.y || worldPos.y > ValkyrienAir_ShipAabbMax2.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin2.z || worldPos.z > ValkyrienAir_ShipAabbMax2.z) return false;\n" +
            "\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip2 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos2;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin2.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize2.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask2, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask2, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip3(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize3.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin3.x || worldPos.x > ValkyrienAir_ShipAabbMax3.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin3.y || worldPos.y > ValkyrienAir_ShipAabbMax3.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin3.z || worldPos.z > ValkyrienAir_ShipAabbMax3.z) return false;\n" +
            "\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip3 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos3;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin3.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize3.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask3, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask3, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n";

    private static final String EMBEDDIUM_FRAGMENT_MAIN_INJECT =
        "\n" +
            "    if (ValkyrienAir_CullEnabled > 0.5 && ValkyrienAir_IsShipPass < 0.5 && va_isWaterUv(v_TexCoord)) {\n" +
            "        vec3 worldPos = valkyrienair_CamRelPos + ValkyrienAir_CameraWorldPos;\n" +
            "        if (va_shouldDiscardForShip0(worldPos) || va_shouldDiscardForShip1(worldPos) ||\n" +
            "            va_shouldDiscardForShip2(worldPos) || va_shouldDiscardForShip3(worldPos)) {\n" +
            "            discard;\n" +
            "        }\n" +
            "    }\n";

    private static final String IRIS_FRAGMENT_DECLS =
        "\n" +
            "uniform float ValkyrienAir_CullEnabled;\n" +
            "uniform float ValkyrienAir_IsShipPass;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin0;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax0;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos0;\n" +
            "uniform vec4 ValkyrienAir_GridMin0;\n" +
            "uniform vec4 ValkyrienAir_GridSize0;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip0;\n" +
            "uniform usampler2D ValkyrienAir_AirMask0;\n" +
            "uniform usampler2D ValkyrienAir_OccMask0;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin1;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax1;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos1;\n" +
            "uniform vec4 ValkyrienAir_GridMin1;\n" +
            "uniform vec4 ValkyrienAir_GridSize1;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip1;\n" +
            "uniform usampler2D ValkyrienAir_AirMask1;\n" +
            "uniform usampler2D ValkyrienAir_OccMask1;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin2;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax2;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos2;\n" +
            "uniform vec4 ValkyrienAir_GridMin2;\n" +
            "uniform vec4 ValkyrienAir_GridSize2;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip2;\n" +
            "uniform usampler2D ValkyrienAir_AirMask2;\n" +
            "uniform usampler2D ValkyrienAir_OccMask2;\n" +
            "\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMin3;\n" +
            "uniform vec4 ValkyrienAir_ShipAabbMax3;\n" +
            "uniform vec3 ValkyrienAir_CameraShipPos3;\n" +
            "uniform vec4 ValkyrienAir_GridMin3;\n" +
            "uniform vec4 ValkyrienAir_GridSize3;\n" +
            "uniform mat4 ValkyrienAir_WorldToShip3;\n" +
            "uniform usampler2D ValkyrienAir_AirMask3;\n" +
            "uniform usampler2D ValkyrienAir_OccMask3;\n" +
            "\n" +
            "const int VA_MASK_TEX_WIDTH_SHIFT = 12;\n" +
            "const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;\n" +
            "\n" +
            "const int VA_SUB = 8;\n" +
            "const int VA_OCC_WORDS_PER_VOXEL = 16;\n" +
            "\n" +
            "uint va_fetchWord(usampler2D tex, int wordIndex) {\n" +
            "    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);\n" +
            "    return texelFetch(tex, coord, 0).r;\n" +
            "}\n" +
            "\n" +
            "bool va_testAir(usampler2D airMask, int voxelIdx) {\n" +
            "    int wordIndex = voxelIdx >> 5;\n" +
            "    int bit = voxelIdx & 31;\n" +
            "    uint word = va_fetchWord(airMask, wordIndex);\n" +
            "    return ((word >> uint(bit)) & 1u) != 0u;\n" +
            "}\n" +
            "\n" +
            "bool va_testOcc(usampler2D occMask, int voxelIdx, int subIdx) {\n" +
            "    int wordIndex = voxelIdx * VA_OCC_WORDS_PER_VOXEL + (subIdx >> 5);\n" +
            "    int bit = subIdx & 31;\n" +
            "    uint word = va_fetchWord(occMask, wordIndex);\n" +
            "    return ((word >> uint(bit)) & 1u) != 0u;\n" +
            "}\n" +
            "\n" +
            "vec3 va_worldPosFromDepth() {\n" +
            "    vec2 ndcXY = (gl_FragCoord.xy / vec2(viewWidth, viewHeight)) * 2.0 - 1.0;\n" +
            "    float ndcZ = gl_FragCoord.z * 2.0 - 1.0;\n" +
            "    vec4 viewPos = gbufferProjectionInverse * vec4(ndcXY, ndcZ, 1.0);\n" +
            "    viewPos /= viewPos.w;\n" +
            "    vec4 worldPos = gbufferModelViewInverse * viewPos;\n" +
            "    return worldPos.xyz + cameraPosition;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip0(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize0.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin0.x || worldPos.x > ValkyrienAir_ShipAabbMax0.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin0.y || worldPos.y > ValkyrienAir_ShipAabbMax0.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin0.z || worldPos.z > ValkyrienAir_ShipAabbMax0.z) return false;\n" +
            "\n" +
            "    vec3 camRelPos = worldPos - cameraPosition;\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip0 * vec4(camRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos0;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin0.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize0.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask0, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask0, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip1(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize1.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin1.x || worldPos.x > ValkyrienAir_ShipAabbMax1.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin1.y || worldPos.y > ValkyrienAir_ShipAabbMax1.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin1.z || worldPos.z > ValkyrienAir_ShipAabbMax1.z) return false;\n" +
            "\n" +
            "    vec3 camRelPos = worldPos - cameraPosition;\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip1 * vec4(camRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos1;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin1.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize1.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask1, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask1, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip2(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize2.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin2.x || worldPos.x > ValkyrienAir_ShipAabbMax2.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin2.y || worldPos.y > ValkyrienAir_ShipAabbMax2.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin2.z || worldPos.z > ValkyrienAir_ShipAabbMax2.z) return false;\n" +
            "\n" +
            "    vec3 camRelPos = worldPos - cameraPosition;\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip2 * vec4(camRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos2;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin2.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize2.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask2, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask2, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n" +
            "\n" +
            "bool va_shouldDiscardForShip3(vec3 worldPos) {\n" +
            "    if (ValkyrienAir_GridSize3.x <= 0.0) return false;\n" +
            "    if (worldPos.x < ValkyrienAir_ShipAabbMin3.x || worldPos.x > ValkyrienAir_ShipAabbMax3.x) return false;\n" +
            "    if (worldPos.y < ValkyrienAir_ShipAabbMin3.y || worldPos.y > ValkyrienAir_ShipAabbMax3.y) return false;\n" +
            "    if (worldPos.z < ValkyrienAir_ShipAabbMin3.z || worldPos.z > ValkyrienAir_ShipAabbMax3.z) return false;\n" +
            "\n" +
            "    vec3 camRelPos = worldPos - cameraPosition;\n" +
            "    vec3 shipPos = (ValkyrienAir_WorldToShip3 * vec4(camRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos3;\n" +
            "    vec3 localPos = shipPos - ValkyrienAir_GridMin3.xyz;\n" +
            "    vec3 size = ValkyrienAir_GridSize3.xyz;\n" +
            "    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n" +
            "    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n" +
            "\n" +
            "    ivec3 v = ivec3(floor(localPos));\n" +
            "    ivec3 isize = ivec3(size);\n" +
            "    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n" +
            "\n" +
            "    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n" +
            "    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n" +
            "    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n" +
            "\n" +
            "    if (va_testOcc(ValkyrienAir_OccMask3, voxelIdx, subIdx)) return true;\n" +
            "    if (va_testAir(ValkyrienAir_AirMask3, voxelIdx)) return true;\n" +
            "    return false;\n" +
            "}\n";

    private static final String IRIS_FRAGMENT_MAIN_INJECT =
        "\n" +
            "    if (ValkyrienAir_CullEnabled > 0.5 && ValkyrienAir_IsShipPass < 0.5) {\n" +
            "        vec3 worldPos = va_worldPosFromDepth();\n" +
            "        if (va_shouldDiscardForShip0(worldPos) || va_shouldDiscardForShip1(worldPos) ||\n" +
            "            va_shouldDiscardForShip2(worldPos) || va_shouldDiscardForShip3(worldPos)) {\n" +
            "            discard;\n" +
            "        }\n" +
            "    }\n";

    public static String injectSodiumShader(final ResourceLocation id, final String source) {
        if (source == null) return null;
        if (source.contains(INJECT_MARKER)) return source;
        if (id == null) return source;

        final String path = id.getPath();
        if (path == null || !path.startsWith("shaders/blocks/")) return source;

        if (path.endsWith(".vsh")) {
            return injectEmbeddiumVertexShader(source);
        }
        if (path.endsWith(".fsh")) {
            return injectEmbeddiumFragmentShader(source);
        }
        return source;
    }

    public static String injectIrisFragmentShader(final String programName, final String source) {
        if (source == null) return null;
        if (source.contains(INJECT_MARKER)) return source;
        if (!isIrisWaterProgram(programName)) return source;
        if (!source.contains("void main")) return source;
        if (!irisHasStandardUniforms(source)) return source;
        if (!irisSupportsIntegerSamplers(source)) return source;

        String out = insertAfterHeader(source, IRIS_FRAGMENT_DECLS);
        out = injectIntoMain(out, IRIS_FRAGMENT_MAIN_INJECT);
        return out;
    }

    private static String injectEmbeddiumVertexShader(final String source) {
        if (source.contains("valkyrienair_CamRelPos")) return source;
        if (!source.contains("void main")) return source;
        if (!source.contains("_vert_position")) return source;
        String out = insertAfterHeader(source, EMBEDDIUM_VERTEX_DECLS);

        String injected = insertAfterLine(out, "vec3 position", "\n    valkyrienair_CamRelPos = position;\n");
        if (!injected.equals(out)) {
            return injected;
        }

        final String drawId =
            source.contains("_draw_id") ? "_draw_id" : (source.contains("_vert_mesh_id") ? "_vert_mesh_id" : null);
        final boolean hasRegionOffset = source.contains("u_RegionOffset");
        final boolean hasDrawTranslation = source.contains("_get_draw_translation");

        final StringBuilder assign = new StringBuilder("\n    valkyrienair_CamRelPos = _vert_position");
        if (hasDrawTranslation && drawId != null) {
            assign.append(" + _get_draw_translation(").append(drawId).append(")");
        }
        if (hasRegionOffset) {
            assign.append(" + u_RegionOffset");
        }
        assign.append(";\n");

        injected = insertAfterLine(out, "gl_Position", assign.toString());
        return injected;
    }

    private static String injectEmbeddiumFragmentShader(final String source) {
        if (source.contains(INJECT_MARKER)) return source;
        if (!source.contains("void main")) return source;
        if (!source.contains("v_TexCoord")) return source;
        String out = insertAfterHeader(source, EMBEDDIUM_FRAGMENT_DECLS);
        out = injectIntoMain(out, EMBEDDIUM_FRAGMENT_MAIN_INJECT);
        return out;
    }

    private static boolean isIrisWaterProgram(final String programName) {
        if (programName == null) return false;
        final String lower = programName.toLowerCase();
        return lower.contains("gbuffers_water");
    }

    private static boolean irisHasStandardUniforms(final String source) {
        return source.contains("gbufferProjectionInverse") &&
            source.contains("gbufferModelViewInverse") &&
            source.contains("cameraPosition") &&
            source.contains("viewWidth") &&
            source.contains("viewHeight");
    }

    private static boolean irisSupportsIntegerSamplers(final String source) {
        final int version = parseGlslVersion(source);
        if (version >= 130) return true;
        if (version < 0) return false;
        return source.contains("GL_EXT_gpu_shader4") || source.contains("GL_EXT_texture_integer");
    }

    private static int parseGlslVersion(final String source) {
        int cursor = 0;
        while (cursor >= 0 && cursor < source.length()) {
            int lineEnd = source.indexOf('\n', cursor);
            if (lineEnd < 0) lineEnd = source.length();
            final String line = source.substring(cursor, lineEnd).trim();
            if (line.startsWith("#version")) {
                final String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    try {
                        return Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                        return -1;
                    }
                }
                return -1;
            }
            cursor = lineEnd + 1;
        }
        return -1;
    }

    private static String insertAfterHeader(final String source, final String inject) {
        int insertPos = 0;
        int cursor = 0;
        boolean sawVersion = false;
        while (cursor >= 0 && cursor < source.length()) {
            int lineEnd = source.indexOf('\n', cursor);
            if (lineEnd < 0) lineEnd = source.length();
            final String line = source.substring(cursor, lineEnd).trim();
            if (line.startsWith("#version")) {
                sawVersion = true;
                insertPos = lineEnd + 1;
            } else if (sawVersion && line.startsWith("#import")) {
                insertPos = lineEnd + 1;
            } else if (sawVersion) {
                break;
            }
            cursor = lineEnd + 1;
        }
        if (insertPos < 0 || insertPos > source.length()) insertPos = 0;
        return source.substring(0, insertPos) + inject + source.substring(insertPos);
    }

    private static String insertAfterLine(final String source, final String needle, final String inject) {
        int cursor = 0;
        while (cursor >= 0 && cursor < source.length()) {
            int lineEnd = source.indexOf('\n', cursor);
            if (lineEnd < 0) lineEnd = source.length();
            final String line = source.substring(cursor, lineEnd);
            if (line.contains(needle)) {
                int insertPos = lineEnd + 1;
                if (insertPos < 0 || insertPos > source.length()) insertPos = lineEnd;
                return source.substring(0, insertPos) + inject + source.substring(insertPos);
            }
            cursor = lineEnd + 1;
        }
        return source;
    }

    private static String injectIntoMain(final String source, final String inject) {
        final int mainIdx = source.indexOf("void main");
        if (mainIdx < 0) return source;
        final int braceIdx = source.indexOf('{', mainIdx);
        if (braceIdx < 0) return source;
        final int insertPos = braceIdx + 1;
        return source.substring(0, insertPos) + inject + source.substring(insertPos);
    }
}
