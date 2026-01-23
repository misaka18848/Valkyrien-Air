package org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injects external-world water culling support into third-party chunk shaders (Embeddium).
 *
 * <p>Vanilla uses an overridden {@code rendertype_translucent} shader via resources. Embeddium uses its own chunk
 * shaders, so we patch the GLSL source at load time.
 */
public final class ShipWaterPocketShaderInjector {

    private ShipWaterPocketShaderInjector() {}

    private static final Logger LOGGER = LogManager.getLogger("ValkyrienAir ShipWaterCull");
    private static final boolean DEBUG_LOG = Boolean.getBoolean("valkyrienair.debugShipWaterCull");
    private static final boolean DUMP_PATCHED_SHADERS = Boolean.getBoolean("valkyrienair.dumpShipWaterCullShaders");
    private static final boolean DEBUG_DISABLE_WATER_UV_FILTER = Boolean.getBoolean("valkyrienair.debugDisableWaterUvFilter");
    private static final boolean DEBUG_TINT_CULL_TEST = Boolean.getBoolean("valkyrienair.debugTintCullTest");
    private static final boolean DEBUG_CULL_STAGE = Boolean.getBoolean("valkyrienair.debugCullStage");
    private static final boolean DEBUG_IGNORE_OCC_MASK = Boolean.getBoolean("valkyrienair.debugIgnoreOccMask");

    static {
        if (DEBUG_DISABLE_WATER_UV_FILTER) {
            LOGGER.info("VA: debugDisableWaterUvFilter enabled — culling applies to all fragments (no water UV gating)");
        }
        if (DEBUG_CULL_STAGE) {
            LOGGER.info("VA: debugCullStage enabled — culling is replaced by per-fragment stage visualization");
        }
        if (DEBUG_IGNORE_OCC_MASK) {
            LOGGER.info("VA: debugIgnoreOccMask enabled — occ mask is ignored (culling uses air mask only)");
        }
    }

    private static final String INJECT_MARKER = "valkyrienair:ship_water_pocket_cull";
    private static final String INJECT_MARKER_VERTEX = "valkyrienair:ship_water_pocket_cull_vertex";
    private static final String VA_PATCH_APPLIED_MARKER = "VA_PATCH_APPLIED";

    private static final String EMBEDDIUM_VERTEX_OUT_DECL = "\nout vec3 valkyrienair_WorldPos;\n";

    private static final String EMBEDDIUM_VERTEX_MAIN_INJECT = "\n    valkyrienair_WorldPos = position;\n";
    private static final String EMBEDDIUM_VERTEX_MAIN_INJECT_FALLBACK = "\n    valkyrienair_WorldPos = _vert_position + translation;\n";
    private static final String EMBEDDIUM_VERTEX_UNIFORM_CHUNK_WORLD_ORIGIN_DECL = "\nuniform vec3 ValkyrienAir_ChunkWorldOrigin;\n";

    private static final String EMBEDDIUM_FRAGMENT_IN_DECL = "\nin vec3 valkyrienair_WorldPos;\n";

    private static final Pattern GLSL_VERSION_LINE =
        Pattern.compile("(?m)^\\s*#version\\b.*$");
    private static final Pattern GLSL_OUT_ANY_LINE =
        Pattern.compile("(?m)^\\s*out\\s+\\w+\\s+\\w+\\s*;\\s*(?://.*)?$");
    private static final Pattern GLSL_IN_ANY_LINE =
        Pattern.compile("(?m)^\\s*in\\s+\\w+\\s+\\w+\\s*;\\s*(?://.*)?$");
    private static final Pattern GLSL_OUT_FRAGCOLOR_ANY_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec4\\s+\\w+\\s*;\\s*(?://.*)?$");
    private static final Pattern GLSL_GL_POSITION_LINE =
        Pattern.compile("(?m)^\\s*gl_Position\\s*=.*;\\s*$");
    private static final Pattern GLSL_GL_POSITION_VEC4_ARG =
        Pattern.compile("(?m)^\\s*gl_Position\\s*=.*\\bvec4\\s*\\(\\s*([^,\\)]+?)\\s*,\\s*1\\.0\\s*\\)\\s*;\\s*$");

    private static final Pattern EMBEDDIUM_VSH_OUT_TEXCOORD_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec2\\s+v_TexCoord\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_VSH_OUT_COLOR_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec4\\s+v_Color\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_VSH_POSITION_LINE =
        Pattern.compile("(?m)^\\s*vec3\\s+position\\s*=\\s*_vert_position\\s*\\+\\s*translation\\s*;\\s*$");
    private static final Pattern EMBEDDIUM_VSH_POSITION_WORLD_LINE =
        Pattern.compile("(?m)^\\s*vec3\\s+position\\s*=\\s*_vert_position\\s*\\+\\s*translation\\s*;\\s*$");
    private static final Pattern EMBEDDIUM_VSH_TRANSLATION_LINE =
        Pattern.compile("(?m)^\\s*vec3\\s+translation\\s*=\\s*.*;\\s*$");
    private static final Pattern EMBEDDIUM_VSH_TRANSLATION_RHS =
        Pattern.compile("(?m)^\\s*vec3\\s+translation\\s*=\\s*(.*?);\\s*$");
    private static final Pattern EMBEDDIUM_VSH_UNIFORM_REGION_OFFSET =
        Pattern.compile("(?m)^\\s*uniform\\s+\\w+\\s+u_RegionOffset\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_VSH_VERT_INIT_LINE =
        Pattern.compile("(?m)^\\s*_vert_init\\s*\\(\\s*\\)\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_FSH_IN_TEXCOORD_LINE =
        Pattern.compile("(?m)^\\s*in\\s+vec2\\s+v_TexCoord\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_FSH_IN_COLOR_LINE =
        Pattern.compile("(?m)^\\s*in\\s+vec4\\s+v_Color\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_FSH_OUT_FRAGCOLOR_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec4\\s+fragColor\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_MAIN_SIGNATURE =
        Pattern.compile("(?s)\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");

    private static final String EMBEDDIUM_FRAGMENT_DECLS = """

        // %s
        // %s
        const bool VA_DEBUG_IGNORE_OCC_MASK = %s;
        uniform float ValkyrienAir_CullEnabled;
        uniform float ValkyrienAir_IsShipPass;
        uniform vec3 ValkyrienAir_CameraWorldPos;

        uniform vec4 ValkyrienAir_WaterStillUv;
        uniform vec4 ValkyrienAir_WaterFlowUv;
        uniform vec4 ValkyrienAir_WaterOverlayUv;

        uniform vec4 ValkyrienAir_ShipAabbMin0;
        uniform vec4 ValkyrienAir_ShipAabbMax0;
        uniform vec3 ValkyrienAir_CameraShipPos0;
        uniform vec4 ValkyrienAir_GridMin0;
        uniform vec4 ValkyrienAir_GridSize0;
        uniform mat4 ValkyrienAir_WorldToShip0;
        uniform usampler2D ValkyrienAir_AirMask0;
        uniform usampler2D ValkyrienAir_OccMask0;

        uniform vec4 ValkyrienAir_ShipAabbMin1;
        uniform vec4 ValkyrienAir_ShipAabbMax1;
        uniform vec3 ValkyrienAir_CameraShipPos1;
        uniform vec4 ValkyrienAir_GridMin1;
        uniform vec4 ValkyrienAir_GridSize1;
        uniform mat4 ValkyrienAir_WorldToShip1;
        uniform usampler2D ValkyrienAir_AirMask1;
        uniform usampler2D ValkyrienAir_OccMask1;

        uniform vec4 ValkyrienAir_ShipAabbMin2;
        uniform vec4 ValkyrienAir_ShipAabbMax2;
        uniform vec3 ValkyrienAir_CameraShipPos2;
        uniform vec4 ValkyrienAir_GridMin2;
        uniform vec4 ValkyrienAir_GridSize2;
        uniform mat4 ValkyrienAir_WorldToShip2;
        uniform usampler2D ValkyrienAir_AirMask2;
        uniform usampler2D ValkyrienAir_OccMask2;

        uniform vec4 ValkyrienAir_ShipAabbMin3;
        uniform vec4 ValkyrienAir_ShipAabbMax3;
        uniform vec3 ValkyrienAir_CameraShipPos3;
        uniform vec4 ValkyrienAir_GridMin3;
        uniform vec4 ValkyrienAir_GridSize3;
        uniform mat4 ValkyrienAir_WorldToShip3;
        uniform usampler2D ValkyrienAir_AirMask3;
        uniform usampler2D ValkyrienAir_OccMask3;

        const int VA_MASK_TEX_WIDTH_SHIFT = 12;
        const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;

        const int VA_SUB = 8;
        const int VA_OCC_WORDS_PER_VOXEL = 16;
        const float VA_WORLD_SAMPLE_EPS = 0.0001;

        bool va_inUv(vec2 uv, vec4 bounds) {
            return uv.x >= bounds.x && uv.x <= bounds.z && uv.y >= bounds.y && uv.y <= bounds.w;
        }

        bool va_isWaterUv(vec2 uv) {
            return va_inUv(uv, ValkyrienAir_WaterStillUv) ||
                va_inUv(uv, ValkyrienAir_WaterFlowUv) ||
                va_inUv(uv, ValkyrienAir_WaterOverlayUv);
        }

        uint va_fetchWord(usampler2D tex, int wordIndex) {
            ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);
            return texelFetch(tex, coord, 0).r;
        }

        bool va_testAir(usampler2D airMask, int voxelIdx) {
            int wordIndex = voxelIdx >> 5;
            int bit = voxelIdx & 31;
            uint word = va_fetchWord(airMask, wordIndex);
            return ((word >> uint(bit)) & 1u) != 0u;
        }

        bool va_testOcc(usampler2D occMask, int voxelIdx, int subIdx) {
            int wordIndex = voxelIdx * VA_OCC_WORDS_PER_VOXEL + (subIdx >> 5);
            int bit = subIdx & 31;
            uint word = va_fetchWord(occMask, wordIndex);
            return ((word >> uint(bit)) & 1u) != 0u;
        }

        bool va_shouldDiscardForShip0(vec3 worldPos) {
            if (ValkyrienAir_GridSize0.x <= 0.0) return false;
            if (worldPos.x < ValkyrienAir_ShipAabbMin0.x || worldPos.x > ValkyrienAir_ShipAabbMax0.x) return false;
            if (worldPos.y < ValkyrienAir_ShipAabbMin0.y || worldPos.y > ValkyrienAir_ShipAabbMax0.y) return false;
            if (worldPos.z < ValkyrienAir_ShipAabbMin0.z || worldPos.z > ValkyrienAir_ShipAabbMax0.z) return false;

            vec3 shipPos = (ValkyrienAir_WorldToShip0 * vec4(worldPos, 1.0)).xyz;
            vec3 localPos = shipPos - ValkyrienAir_GridMin0.xyz;
            vec3 size = ValkyrienAir_GridSize0.xyz;
            if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
            if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

            ivec3 v = ivec3(floor(localPos));
            ivec3 isize = ivec3(size);
            int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

            ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
            sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
            int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

            if (!VA_DEBUG_IGNORE_OCC_MASK && va_testOcc(ValkyrienAir_OccMask0, voxelIdx, subIdx)) return true;
            if (va_testAir(ValkyrienAir_AirMask0, voxelIdx)) return true;
            return false;
        }

        bool va_shouldDiscardForShip1(vec3 worldPos) {
            if (ValkyrienAir_GridSize1.x <= 0.0) return false;
            if (worldPos.x < ValkyrienAir_ShipAabbMin1.x || worldPos.x > ValkyrienAir_ShipAabbMax1.x) return false;
            if (worldPos.y < ValkyrienAir_ShipAabbMin1.y || worldPos.y > ValkyrienAir_ShipAabbMax1.y) return false;
            if (worldPos.z < ValkyrienAir_ShipAabbMin1.z || worldPos.z > ValkyrienAir_ShipAabbMax1.z) return false;

            vec3 shipPos = (ValkyrienAir_WorldToShip1 * vec4(worldPos, 1.0)).xyz;
            vec3 localPos = shipPos - ValkyrienAir_GridMin1.xyz;
            vec3 size = ValkyrienAir_GridSize1.xyz;
            if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
            if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

            ivec3 v = ivec3(floor(localPos));
            ivec3 isize = ivec3(size);
            int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

            ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
            sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
            int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

            if (!VA_DEBUG_IGNORE_OCC_MASK && va_testOcc(ValkyrienAir_OccMask1, voxelIdx, subIdx)) return true;
            if (va_testAir(ValkyrienAir_AirMask1, voxelIdx)) return true;
            return false;
        }

        bool va_shouldDiscardForShip2(vec3 worldPos) {
            if (ValkyrienAir_GridSize2.x <= 0.0) return false;
            if (worldPos.x < ValkyrienAir_ShipAabbMin2.x || worldPos.x > ValkyrienAir_ShipAabbMax2.x) return false;
            if (worldPos.y < ValkyrienAir_ShipAabbMin2.y || worldPos.y > ValkyrienAir_ShipAabbMax2.y) return false;
            if (worldPos.z < ValkyrienAir_ShipAabbMin2.z || worldPos.z > ValkyrienAir_ShipAabbMax2.z) return false;

            vec3 shipPos = (ValkyrienAir_WorldToShip2 * vec4(worldPos, 1.0)).xyz;
            vec3 localPos = shipPos - ValkyrienAir_GridMin2.xyz;
            vec3 size = ValkyrienAir_GridSize2.xyz;
            if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
            if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

            ivec3 v = ivec3(floor(localPos));
            ivec3 isize = ivec3(size);
            int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

            ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
            sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
            int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

            if (!VA_DEBUG_IGNORE_OCC_MASK && va_testOcc(ValkyrienAir_OccMask2, voxelIdx, subIdx)) return true;
            if (va_testAir(ValkyrienAir_AirMask2, voxelIdx)) return true;
            return false;
        }

        bool va_shouldDiscardForShip3(vec3 worldPos) {
            if (ValkyrienAir_GridSize3.x <= 0.0) return false;
            if (worldPos.x < ValkyrienAir_ShipAabbMin3.x || worldPos.x > ValkyrienAir_ShipAabbMax3.x) return false;
            if (worldPos.y < ValkyrienAir_ShipAabbMin3.y || worldPos.y > ValkyrienAir_ShipAabbMax3.y) return false;
            if (worldPos.z < ValkyrienAir_ShipAabbMin3.z || worldPos.z > ValkyrienAir_ShipAabbMax3.z) return false;

            vec3 shipPos = (ValkyrienAir_WorldToShip3 * vec4(worldPos, 1.0)).xyz;
            vec3 localPos = shipPos - ValkyrienAir_GridMin3.xyz;
            vec3 size = ValkyrienAir_GridSize3.xyz;
            if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
            if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

            ivec3 v = ivec3(floor(localPos));
            ivec3 isize = ivec3(size);
            int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

            ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
            sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
            int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

            if (!VA_DEBUG_IGNORE_OCC_MASK && va_testOcc(ValkyrienAir_OccMask3, voxelIdx, subIdx)) return true;
            if (va_testAir(ValkyrienAir_AirMask3, voxelIdx)) return true;
            return false;
        }
        """.formatted(INJECT_MARKER, VA_PATCH_APPLIED_MARKER, DEBUG_IGNORE_OCC_MASK ? "true" : "false");

    private static final String EMBEDDIUM_FRAGMENT_MAIN_INJECT = """
            if (ValkyrienAir_CullEnabled > 0.5 && ValkyrienAir_IsShipPass < 0.5) {
                vec3 worldPos = valkyrienair_WorldPos + ValkyrienAir_CameraWorldPos + vec3(0.0, -VA_WORLD_SAMPLE_EPS, 0.0);

                if (%s) {
                    fragColor = vec4(1.0, 0.0, 1.0, 1.0);
                    return;
                }

                if (%s) {
                    // Keep the debug visualization narrowly scoped to the same fragments we'd normally consider for
                    // water culling, so we don't tint unrelated translucent/opaque geometry.
                    if (%s || va_isWaterUv(v_TexCoord)) {
                        // Restrict visualization to fragments that are inside ship 0's world AABB.
                        // This also ensures these uniforms are kept live by the GLSL optimizer in debug mode.
                        if (worldPos.x < ValkyrienAir_ShipAabbMin0.x || worldPos.x > ValkyrienAir_ShipAabbMax0.x ||
                            worldPos.y < ValkyrienAir_ShipAabbMin0.y || worldPos.y > ValkyrienAir_ShipAabbMax0.y ||
                            worldPos.z < ValkyrienAir_ShipAabbMin0.z || worldPos.z > ValkyrienAir_ShipAabbMax0.z) {
                            // Outside ship 0 AABB: fall through to normal shader output (no debug override).
                        } else {
                            vec3 shipPos_dbg = (ValkyrienAir_WorldToShip0 * vec4(worldPos, 1.0)).xyz;
                            if (any(isnan(shipPos_dbg)) || any(isinf(shipPos_dbg))) {
                                fragColor = vec4(1.0, 0.5, 0.0, 1.0);
                                return;
                            }
                            vec3 localPos_dbg = shipPos_dbg - ValkyrienAir_GridMin0.xyz;
                            if (any(isnan(localPos_dbg)) || any(isinf(localPos_dbg))) {
                                fragColor = vec4(1.0, 0.5, 0.0, 1.0);
                                return;
                            }
                            if (localPos_dbg.x < 0.0 || localPos_dbg.y < 0.0 || localPos_dbg.z < 0.0 ||
                                localPos_dbg.x >= ValkyrienAir_GridSize0.x || localPos_dbg.y >= ValkyrienAir_GridSize0.y || localPos_dbg.z >= ValkyrienAir_GridSize0.z) {
                                fragColor = vec4(1.0, 1.0, 0.0, 1.0);
                                return;
                            }

                            ivec2 airSize_dbg = textureSize(ValkyrienAir_AirMask0, 0);
                            ivec2 occSize_dbg = textureSize(ValkyrienAir_OccMask0, 0);
                            if (airSize_dbg.x <= 0 || airSize_dbg.y <= 0 || occSize_dbg.x <= 0 || occSize_dbg.y <= 0) {
                                fragColor = vec4(0.0, 1.0, 1.0, 1.0);
                                return;
                            }

                            int airWords_dbg = max(1, airSize_dbg.x * airSize_dbg.y);
                            int occWords_dbg = max(1, occSize_dbg.x * occSize_dbg.y);
                            uint airAny_dbg = va_fetchWord(ValkyrienAir_AirMask0, 0) |
                                va_fetchWord(ValkyrienAir_AirMask0, airWords_dbg / 2) |
                                va_fetchWord(ValkyrienAir_AirMask0, airWords_dbg - 1);
                            uint occAny_dbg = va_fetchWord(ValkyrienAir_OccMask0, 0) |
                                va_fetchWord(ValkyrienAir_OccMask0, occWords_dbg / 2) |
                                va_fetchWord(ValkyrienAir_OccMask0, occWords_dbg - 1);
                            if ((airAny_dbg | occAny_dbg) == 0u) {
                                fragColor = vec4(0.0, 1.0, 1.0, 1.0);
                                return;
                            }

                            ivec3 v_dbg = ivec3(floor(localPos_dbg));
                            ivec3 isize_dbg = ivec3(ValkyrienAir_GridSize0.xyz);
                            int voxelIdx_dbg = v_dbg.x + isize_dbg.x * (v_dbg.y + isize_dbg.y * v_dbg.z);

                            int airBit_dbg = voxelIdx_dbg & 31;
                            uint airWord_dbg = va_fetchWord(ValkyrienAir_AirMask0, voxelIdx_dbg >> 5);
                            bool airHit_dbg = ((airWord_dbg >> uint(airBit_dbg)) & 1u) != 0u;

                            bool occHit_dbg = false;
                            if (!VA_DEBUG_IGNORE_OCC_MASK) {
                                ivec3 sv_dbg = ivec3(floor(fract(shipPos_dbg) * float(VA_SUB)));
                                sv_dbg = clamp(sv_dbg, ivec3(0), ivec3(VA_SUB - 1));
                                int subIdx_dbg = sv_dbg.x + VA_SUB * (sv_dbg.y + VA_SUB * sv_dbg.z);
                                int occBit_dbg = subIdx_dbg & 31;
                                int occWordIndex_dbg = voxelIdx_dbg * VA_OCC_WORDS_PER_VOXEL + (subIdx_dbg >> 5);
                                uint occWord_dbg = va_fetchWord(ValkyrienAir_OccMask0, occWordIndex_dbg);
                                occHit_dbg = ((occWord_dbg >> uint(occBit_dbg)) & 1u) != 0u;
                            }

                            if (airHit_dbg) {
                                fragColor = vec4(0.0, 1.0, 0.0, 1.0);
                                return;
                            }

                            // If the sample point falls inside a water-blocking block voxel, approximate the CPU-side
                            // "find open neighbor cell" behavior by stepping to the nearest voxel face and testing
                            // the air-pocket mask there.
                            bool airHitResolved_dbg = false;
                            if (occHit_dbg) {
                                // More robust debug-only resolve: search the 3x3x3 neighborhood for any air-pocket voxel.
                                // This helps distinguish "we're close but sampling the wrong voxel" vs "there is no air
                                // pocket anywhere near this point".
                                for (int dz = -1; dz <= 1 && !airHitResolved_dbg; dz++) {
                                    for (int dy = -1; dy <= 1 && !airHitResolved_dbg; dy++) {
                                        for (int dx = -1; dx <= 1 && !airHitResolved_dbg; dx++) {
                                            if (dx == 0 && dy == 0 && dz == 0) continue;
                                            ivec3 v2_dbg = v_dbg + ivec3(dx, dy, dz);
                                            if (v2_dbg.x < 0 || v2_dbg.y < 0 || v2_dbg.z < 0 ||
                                                v2_dbg.x >= isize_dbg.x || v2_dbg.y >= isize_dbg.y || v2_dbg.z >= isize_dbg.z) {
                                                continue;
                                            }
                                            int voxelIdx2_dbg = v2_dbg.x + isize_dbg.x * (v2_dbg.y + isize_dbg.y * v2_dbg.z);
                                            int airBit2_dbg = voxelIdx2_dbg & 31;
                                            uint airWord2_dbg = va_fetchWord(ValkyrienAir_AirMask0, voxelIdx2_dbg >> 5);
                                            airHitResolved_dbg = ((airWord2_dbg >> uint(airBit2_dbg)) & 1u) != 0u;
                                        }
                                    }
                                }
                            }
                            if (airHitResolved_dbg) {
                                fragColor = vec4(0.0, 0.0, 1.0, 1.0);
                                return;
                            }
                            if (occHit_dbg) {
                                fragColor = vec4(1.0, 0.0, 0.0, 1.0);
                                return;
                            }
                            fragColor = vec4(1.0, 1.0, 1.0, 1.0);
                            return;
                        }
                    }
                }

                if (%s || va_isWaterUv(v_TexCoord)) {
                    if (va_shouldDiscardForShip0(worldPos) || va_shouldDiscardForShip1(worldPos) ||
                        va_shouldDiscardForShip2(worldPos) || va_shouldDiscardForShip3(worldPos)) {
                        discard;
                    }
                }
            }

        """.formatted(
            DEBUG_TINT_CULL_TEST ? "true" : "false",
            DEBUG_CULL_STAGE ? "true" : "false",
            DEBUG_DISABLE_WATER_UV_FILTER ? "true" : "false",
            DEBUG_DISABLE_WATER_UV_FILTER ? "true" : "false"
        );

    private static boolean loggedEmbeddiumVertexPatch = false;
    private static boolean loggedEmbeddiumFragmentPatch = false;
    private static boolean loggedEmbeddiumVertexPatchFailed = false;
    private static boolean loggedEmbeddiumFragmentPatchFailed = false;

    private static boolean isEmbeddiumBlockLayerShader(final String identifierPath, final String extension) {
        if (identifierPath == null) return false;
        if (!identifierPath.endsWith(extension)) return false;

        // Normalize older "shaders/blocks/..." identifiers to "blocks/...".
        String path = identifierPath;
        final int shadersIdx = path.indexOf("shaders/");
        if (shadersIdx >= 0) {
            path = path.substring(shadersIdx + "shaders/".length());
        }
        // Keep existing block_layer_* coverage, plus a small allow-list for known Embeddium chunk-shader families.
        // (We also log matching identifiers in injectSodiumShader() so we can expand safely if needed.)
        return path.startsWith("blocks/block_layer_") || path.startsWith("blocks/fluid_layer_");
    }

    public static String injectSodiumShader(final ResourceLocation identifier, final String source) {
        if (source == null) return null;
        if (source.contains(INJECT_MARKER) || source.contains("ValkyrienAir_CullEnabled") || source.contains("valkyrienair_WorldPos")) {
            return source;
        }
        if (identifier == null) return source;

        final String path = identifier.getPath();
        if (path == null) return source;

        // Accept both modern ("blocks/...") and older ("shaders/blocks/...") Embeddium shader identifiers.
        if (isEmbeddiumBlockLayerShader(path, ".vsh")) {
            if (DEBUG_LOG) {
                LOGGER.info("Embeddium shader matched for VA patch (vsh): {}", identifier);
            }
            final String patched = injectEmbeddiumVertexShader(source);
            if (DEBUG_LOG && !loggedEmbeddiumVertexPatch && !patched.equals(source)) {
                loggedEmbeddiumVertexPatch = true;
                LOGGER.info("Patched Embeddium vertex shader for ship water culling: {}", identifier);
            }
            if (DEBUG_LOG && patched.contains(VA_PATCH_APPLIED_MARKER) && path.contains("translucent") && !patched.equals(source)) {
                LOGGER.info("Patched Embeddium translucent vertex shader for ship water culling: {}", identifier);
            }
            maybeDumpPatchedShader(identifier, patched, DEBUG_TINT_CULL_TEST);
            return patched;
        }
        if (isEmbeddiumBlockLayerShader(path, ".fsh")) {
            if (DEBUG_LOG) {
                LOGGER.info("Embeddium shader matched for VA patch (fsh): {}", identifier);
            }
            final String patched = injectEmbeddiumFragmentShader(source);
            if (DEBUG_LOG && !loggedEmbeddiumFragmentPatch && !patched.equals(source)) {
                loggedEmbeddiumFragmentPatch = true;
                LOGGER.info("Patched Embeddium fragment shader for ship water culling: {}", identifier);
            }
            if (DEBUG_LOG && patched.contains(VA_PATCH_APPLIED_MARKER) && path.contains("translucent") && !patched.equals(source)) {
                LOGGER.info("Patched Embeddium translucent fragment shader for ship water culling: {}", identifier);
            }
            maybeDumpPatchedShader(identifier, patched, DEBUG_TINT_CULL_TEST || DEBUG_CULL_STAGE);
            return patched;
        }

        return source;
    }

    private static String injectEmbeddiumVertexShader(final String source) {
        String out = source;

        if (!out.contains(VA_PATCH_APPLIED_MARKER)) {
            out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, "\n// " + VA_PATCH_APPLIED_MARKER + "\n");
        }

        // The injection points here must tolerate indentation and line-ending differences across Embeddium builds.
        out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_OUT_TEXCOORD_LINE, EMBEDDIUM_VERTEX_OUT_DECL);
        if (!out.contains("out vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_OUT_COLOR_LINE, EMBEDDIUM_VERTEX_OUT_DECL);
        }
        if (!out.contains("out vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, GLSL_OUT_ANY_LINE, EMBEDDIUM_VERTEX_OUT_DECL);
        }
        if (!out.contains("out vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, "\n// " + INJECT_MARKER_VERTEX + "\n" + EMBEDDIUM_VERTEX_OUT_DECL);
        }

        if (!out.contains("valkyrienair_WorldPos =")) {
            final boolean hasRegionOffsetUniform = EMBEDDIUM_VSH_UNIFORM_REGION_OFFSET.matcher(out).find();
            if (hasRegionOffsetUniform) {
                final String translationRhs = findFirstRegexLineGroup(out, EMBEDDIUM_VSH_TRANSLATION_RHS, 1);
                final boolean translationIncludesRegionOffset =
                    translationRhs != null && translationRhs.contains("u_RegionOffset");

                final String injection;
                if (translationIncludesRegionOffset) {
                    injection = "\n    // VA_WORLDPOS_BRANCH: vert_position_plus_translation\n" +
                        "    valkyrienair_WorldPos = _vert_position + translation;\n";
                } else {
                    injection = "\n    // VA_WORLDPOS_BRANCH: position_plus_regionOffset\n" +
                        "    valkyrienair_WorldPos = position + vec3(u_RegionOffset);\n";
                }
                out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_POSITION_LINE, injection);
            }
        }

        if (!out.contains("valkyrienair_WorldPos =")) {
            // Fallback: use translation if present (may be world or camera-relative depending on shader variant).
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_TRANSLATION_LINE,
                "\n    // VA_WORLDPOS_BRANCH: vert_position_plus_translation_fallback\n" +
                    "    valkyrienair_WorldPos = _vert_position + translation;\n");
        }

        if (!out.contains("valkyrienair_WorldPos =")) {
            // Last resort: introduce an explicit world-origin addend we can upload from Java.
            if (!out.contains("uniform vec3 ValkyrienAir_ChunkWorldOrigin;")) {
                out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, EMBEDDIUM_VERTEX_UNIFORM_CHUNK_WORLD_ORIGIN_DECL);
            }
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_VERT_INIT_LINE,
                "\n    // VA_WORLDPOS_BRANCH: chunk_world_origin_uniform\n" +
                    "    valkyrienair_WorldPos = _vert_position + ValkyrienAir_ChunkWorldOrigin;\n");
            if (!out.contains("valkyrienair_WorldPos =")) {
                out = insertAfterFirstRegexLine(out, EMBEDDIUM_MAIN_SIGNATURE,
                    "\n    // VA_WORLDPOS_BRANCH: chunk_world_origin_uniform\n" +
                        "    valkyrienair_WorldPos = _vert_position + ValkyrienAir_ChunkWorldOrigin;\n");
            }
        }

        if (!out.contains("out vec3 valkyrienair_WorldPos;") || !out.contains("valkyrienair_WorldPos =")) {
            if (!loggedEmbeddiumVertexPatchFailed) {
                loggedEmbeddiumVertexPatchFailed = true;
                LOGGER.warn("Failed to fully patch Embeddium vertex shader for ship water culling; WorldPos output may be invalid");
            }
        }

        return out;
    }

    private static String injectEmbeddiumFragmentShader(final String source) {
        String out = source;

        if (!out.contains(VA_PATCH_APPLIED_MARKER)) {
            out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, "\n// " + VA_PATCH_APPLIED_MARKER + "\n");
        }

        out = insertAfterFirstRegexLine(out, EMBEDDIUM_FSH_IN_TEXCOORD_LINE, EMBEDDIUM_FRAGMENT_IN_DECL);
        if (!out.contains("in vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_FSH_IN_COLOR_LINE, EMBEDDIUM_FRAGMENT_IN_DECL);
        }
        if (!out.contains("in vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, GLSL_IN_ANY_LINE, EMBEDDIUM_FRAGMENT_IN_DECL);
        }

        // Insert declarations/helpers before main.
        out = insertAfterFirstRegexLine(out, EMBEDDIUM_FSH_OUT_FRAGCOLOR_LINE, "\n" + EMBEDDIUM_FRAGMENT_DECLS + "\n");
        if (!out.contains(INJECT_MARKER)) {
            out = insertAfterFirstRegexLine(out, GLSL_OUT_FRAGCOLOR_ANY_LINE, "\n" + EMBEDDIUM_FRAGMENT_DECLS + "\n");
        }
        if (!out.contains(INJECT_MARKER)) {
            // Last resort: insert just before main().
            out = insertBeforeFirstRegex(out, EMBEDDIUM_MAIN_SIGNATURE, "\n" + EMBEDDIUM_FRAGMENT_DECLS + "\n");
        }

        // Inject cull check at the top of main().
        out = insertAfterFirstRegex(out, EMBEDDIUM_MAIN_SIGNATURE, EMBEDDIUM_FRAGMENT_MAIN_INJECT);

        if (!out.contains(INJECT_MARKER) || !out.contains("in vec3 valkyrienair_WorldPos;")) {
            if (!loggedEmbeddiumFragmentPatchFailed) {
                loggedEmbeddiumFragmentPatchFailed = true;
                LOGGER.warn("Failed to fully patch Embeddium fragment shader for ship water culling; culling may be inactive");
            }
        }

        return out;
    }

    private static String insertAfterFirstLine(final String source, final String lineStart, final String insert) {
        if (source == null || insert == null) return source;
        final int idx = source.indexOf(lineStart);
        if (idx < 0) return source;
        final int end = source.indexOf('\n', idx);
        if (end < 0) return source;
        return source.substring(0, end + 1) + insert + source.substring(end + 1);
    }

    private static String insertAfterFirst(final String source, final String needle, final String insert) {
        if (source == null || needle == null || needle.isEmpty() || insert == null) return source;
        final int idx = source.indexOf(needle);
        if (idx < 0) return source;
        return source.substring(0, idx + needle.length()) + insert + source.substring(idx + needle.length());
    }

    private static String insertAfterFirstRegexLine(final String source, final Pattern linePattern, final String insert) {
        if (source == null || linePattern == null || insert == null) return source;
        final Matcher m = linePattern.matcher(source);
        if (!m.find()) return source;

        int pos = m.end();
        if (pos < source.length()) {
            final char c = source.charAt(pos);
            if (c == '\r') {
                pos++;
                if (pos < source.length() && source.charAt(pos) == '\n') pos++;
            } else if (c == '\n') {
                pos++;
            }
        }

        return source.substring(0, pos) + insert + source.substring(pos);
    }

    private static String insertAfterFirstRegex(final String source, final Pattern pattern, final String insert) {
        if (source == null || pattern == null || insert == null) return source;
        final Matcher m = pattern.matcher(source);
        if (!m.find()) return source;
        final int pos = m.end();
        return source.substring(0, pos) + insert + source.substring(pos);
    }

    private static String insertBeforeFirstRegex(final String source, final Pattern pattern, final String insert) {
        if (source == null || pattern == null || insert == null) return source;
        final Matcher m = pattern.matcher(source);
        if (!m.find()) return source;
        final int pos = m.start();
        return source.substring(0, pos) + insert + source.substring(pos);
    }

    private static String findFirstRegexLineGroup(final String source, final Pattern linePattern, final int group) {
        if (source == null || linePattern == null) return null;
        final Matcher m = linePattern.matcher(source);
        if (!m.find()) return null;
        try {
            return m.group(group);
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static void maybeDumpPatchedShader(final ResourceLocation identifier, final String patchedSource) {
        maybeDumpPatchedShader(identifier, patchedSource, false);
    }

    private static void maybeDumpPatchedShader(final ResourceLocation identifier, final String patchedSource, final boolean force) {
        if (!force && !DUMP_PATCHED_SHADERS) return;
        if (identifier == null || patchedSource == null) return;

        try {
            // Relative to the MC working directory at runtime.
            final Path root = Path.of("valkyrienair_shader_dumps");
            final Path outPath = root.resolve(identifier.getNamespace()).resolve(identifier.getPath());
            Files.createDirectories(outPath.getParent());
            Files.writeString(outPath, patchedSource, StandardCharsets.UTF_8);
        } catch (final Throwable t) {
            // Don't crash shader load; just report once.
            if (DEBUG_LOG) {
                LOGGER.warn("Failed to dump patched shader source for {}", identifier, t);
            }
        }
    }
}
