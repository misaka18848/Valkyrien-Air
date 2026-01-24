package org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BiomeColors
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.Fluids
import org.joml.Matrix4f
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.valkyrienair.config.ValkyrienAirConfig
import org.valkyrienskies.valkyrienair.feature.ship_water_pockets.ShipWaterPocketManager
import kotlin.math.abs

/**
 * Renders a "fake" water surface for pressurized ship openings.
 *
 * World water has no surface quads deep underwater (it's a continuous water column), so even when an air pocket is
 * rendered as air via shader culling, the boundary at a pressurized opening needs custom geometry.
 */
object ShipWaterPocketFakeWaterSurfaceRenderer {

    private val WATER_STILL_SPRITE_ID = ResourceLocation("minecraft", "block/water_still")
    private val FAKE_SURFACE_RENDER_TYPE = RenderType.translucent()
    private const val WORLD_SURFACE_CONTACT_EPS = 1e-3
    private const val FLUID_HEIGHT_EPS = 1e-6

    private data class UpDir(
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val incWorldY: Double,
    )

    private data class CachedSurfaces(
        val builtTick: Long,
        val geometryRevision: Long,
        val upDir: UpDir,
        val surfaceWaterCells: LongArray,
    )

    private var cachedLevel: Level? = null
    private val cacheByShipId = HashMap<Long, CachedSurfaces>()

    private val tmpShipPos = Vector3d()
    private val tmpWorldPos = Vector3d()
    private val tmpCameraShipPos = Vector3d()
    private val tmpWorldBlockPos = BlockPos.MutableBlockPos()
    private val tmpWorldBlockPos2 = BlockPos.MutableBlockPos()

    private var cachedWaterSprite: TextureAtlasSprite? = null

    @JvmStatic
    fun onRenderShip(event: VSGameEvents.ShipRenderEvent) {
        if (!ValkyrienAirConfig.enableShipWaterPockets) return
        if (event.renderType !== RenderType.translucent()) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        val ship = event.ship
        val snapshot = ShipWaterPocketManager.getClientWaterReachableSnapshot(level, ship.id) ?: return
        if (snapshot.waterReachable.isEmpty) return

        val shipTransform = ship.renderTransform
        val surfaces = getOrBuildSurfaces(level, shipTransform, ship.id, snapshot) ?: return
        if (surfaces.surfaceWaterCells.isEmpty()) return

        val sprite = getWaterSprite(mc) ?: return

        // Render immediately using the projection/model-view matrices for this pass.
        //
        // MultiBufferSource flush uses RenderSystem matrices (not the chunk-layer ShaderInstance uniforms), so set them
        // explicitly for correctness under both vanilla and Embeddium.
        tmpWorldPos.set(event.camX, event.camY, event.camZ)
        shipTransform.worldToShip.transformPosition(tmpWorldPos, tmpCameraShipPos)

        val prevProj = Matrix4f(RenderSystem.getProjectionMatrix())
        val modelViewStack = RenderSystem.getModelViewStack()

        modelViewStack.pushPose()
        try {
            RenderSystem.setProjectionMatrix(Matrix4f(event.projectionMatrix), VertexSorting.DISTANCE_TO_ORIGIN)
            modelViewStack.last().pose().set(Matrix4f(event.poseStack.last().pose()))
            RenderSystem.applyModelViewMatrix()

            val bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().builder)
            val vc = bufferSource.getBuffer(FAKE_SURFACE_RENDER_TYPE)

            renderSurfacesShipSpaceCameraRelative(
                level = level,
                shipTransform = shipTransform,
                sprite = sprite,
                upDir = surfaces.upDir,
                surfaceWaterCells = surfaces.surfaceWaterCells,
                cameraShipPos = tmpCameraShipPos,
                vc = vc,
            )

            bufferSource.endBatch(FAKE_SURFACE_RENDER_TYPE)
        } finally {
            // Restore GL matrix state.
            RenderSystem.setProjectionMatrix(prevProj, VertexSorting.DISTANCE_TO_ORIGIN)
            modelViewStack.popPose()
            RenderSystem.applyModelViewMatrix()
        }
    }

    /**
     * Called from [org.valkyrienskies.valkyrienair.client.ValkyrienAirModClient] for the Sodium/Embeddium ship renderer.
     *
     * This is reflection-based so the class can load in "vanilla" environments where Embeddium isn't present.
     */
    @JvmStatic
    fun onPostRenderShipSodium(event: Any) {
        if (!ValkyrienAirConfig.enableShipWaterPockets) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        val ship = getEventShip(event) ?: return
        val passLayer = getSodiumPassLayer(event) ?: return
        if (passLayer !== RenderType.translucent()) return

        val matrices = getEventMatrices(event) ?: return
        val proj = getSodiumProjection(matrices) ?: return
        val mv = getSodiumModelView(matrices) ?: return

        val snapshot = ShipWaterPocketManager.getClientWaterReachableSnapshot(level, ship.id) ?: return
        if (snapshot.waterReachable.isEmpty) return

        val shipTransform = ship.renderTransform
        val surfaces = getOrBuildSurfaces(level, shipTransform, ship.id, snapshot) ?: return
        if (surfaces.surfaceWaterCells.isEmpty()) return

        val sprite = getWaterSprite(mc) ?: return

        tmpWorldPos.set(getEventCamX(event) ?: return, getEventCamY(event) ?: return, getEventCamZ(event) ?: return)
        shipTransform.worldToShip.transformPosition(tmpWorldPos, tmpCameraShipPos)

        // Build the ship model-view matrix for this ship/pass. This matches the transform used by VS2's Sodium ship
        // renderer (see SodiumCompat#vsRenderLayer).
        val shipMv = org.joml.Matrix4d(mv)
            .translate(-tmpWorldPos.x, -tmpWorldPos.y, -tmpWorldPos.z)
            .mul(shipTransform.shipToWorld)
            .translate(tmpCameraShipPos)

        // Render immediately using the current projection/model-view matrices for this frame.
        val bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().builder)
        val vc = bufferSource.getBuffer(FAKE_SURFACE_RENDER_TYPE)

        val prevProj = Matrix4f(RenderSystem.getProjectionMatrix())
        val modelViewStack = RenderSystem.getModelViewStack()

        modelViewStack.pushPose()
        try {
            RenderSystem.setProjectionMatrix(Matrix4f(proj), VertexSorting.DISTANCE_TO_ORIGIN)
            modelViewStack.last().pose().set(Matrix4f(shipMv))
            RenderSystem.applyModelViewMatrix()

            renderSurfacesShipSpaceCameraRelative(
                level = level,
                shipTransform = shipTransform,
                sprite = sprite,
                upDir = surfaces.upDir,
                surfaceWaterCells = surfaces.surfaceWaterCells,
                cameraShipPos = tmpCameraShipPos,
                vc = vc,
            )

            bufferSource.endBatch(FAKE_SURFACE_RENDER_TYPE)
        } finally {
            // Restore GL matrix state.
            RenderSystem.setProjectionMatrix(prevProj, VertexSorting.DISTANCE_TO_ORIGIN)
            modelViewStack.popPose()
            RenderSystem.applyModelViewMatrix()
        }
    }

    private fun getWaterSprite(mc: Minecraft): TextureAtlasSprite? {
        val cached = cachedWaterSprite
        if (cached != null) return cached

        val atlas = mc.modelManager.getAtlas(InventoryMenu.BLOCK_ATLAS)
        val sprite = atlas.getSprite(WATER_STILL_SPRITE_ID)
        cachedWaterSprite = sprite
        return sprite
    }

    private fun clearCacheIfLevelChanged(level: Level) {
        if (cachedLevel !== level) {
            cachedLevel = level
            cacheByShipId.clear()
        }
    }

    private fun getOrBuildSurfaces(
        level: Level,
        shipTransform: ShipTransform,
        shipId: Long,
        snapshot: ShipWaterPocketManager.ClientWaterReachableSnapshot,
    ): CachedSurfaces? {
        clearCacheIfLevelChanged(level)

        val tick = level.gameTime
        val upDir = computeUpDir(shipTransform)
        val cached = cacheByShipId[shipId]
        if (cached != null &&
            cached.builtTick == tick &&
            cached.geometryRevision == snapshot.geometryRevision &&
            cached.upDir == upDir
        ) {
            return cached
        }

        val water = snapshot.waterReachable
        val open = snapshot.open
        val sizeX = snapshot.sizeX
        val sizeY = snapshot.sizeY
        val sizeZ = snapshot.sizeZ
        val volume = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
        if (volume <= 0L) return null

        val strideY = sizeX
        val strideZ = sizeX * sizeY

        fun hasAirAbove(idx: Int, lx: Int, ly: Int, lz: Int): Boolean {
            val dx = upDir.dx
            val dy = upDir.dy
            val dz = upDir.dz
            if (dx != 0) {
                if (dx > 0) {
                    if (lx + 1 >= sizeX) return false
                    val above = idx + 1
                    return open.get(above) && !water.get(above)
                } else {
                    if (lx <= 0) return false
                    val above = idx - 1
                    return open.get(above) && !water.get(above)
                }
            }
            if (dy != 0) {
                if (dy > 0) {
                    if (ly + 1 >= sizeY) return false
                    val above = idx + strideY
                    return open.get(above) && !water.get(above)
                } else {
                    if (ly <= 0) return false
                    val above = idx - strideY
                    return open.get(above) && !water.get(above)
                }
            }
            if (dz != 0) {
                if (dz > 0) {
                    if (lz + 1 >= sizeZ) return false
                    val above = idx + strideZ
                    return open.get(above) && !water.get(above)
                } else {
                    if (lz <= 0) return false
                    val above = idx - strideZ
                    return open.get(above) && !water.get(above)
                }
            }
            return false
        }

        // Collect water-reachable cells that have an air pocket directly "above" them (relative to world up).
        var out = LongArray(64)
        var count = 0

        var idx = water.nextSetBit(0)
        while (idx >= 0) {
            val lx = idx % sizeX
            val t = idx / sizeX
            val ly = t % sizeY
            val lz = t / sizeY

            // Only render fake surfaces inside the ship AABB (exclude the 1-block padded boundary layer).
            // This prevents drawing surfaces in the exterior water around the ship.
            if (lx == 0 || lx + 1 == sizeX || ly == 0 || ly + 1 == sizeY || lz == 0 || lz + 1 == sizeZ) {
                idx = water.nextSetBit(idx + 1)
                continue
            }

            if (hasAirAbove(idx, lx, ly, lz)) {
                val x = snapshot.minX + lx
                val y = snapshot.minY + ly
                val z = snapshot.minZ + lz
                if (count >= out.size) out = out.copyOf(out.size * 2)
                out[count++] = BlockPos.asLong(x, y, z)
            }

            idx = water.nextSetBit(idx + 1)
        }

        val surfaces = if (count == 0) LongArray(0) else out.copyOf(count)
        val built = CachedSurfaces(
            builtTick = tick,
            geometryRevision = snapshot.geometryRevision,
            upDir = upDir,
            surfaceWaterCells = surfaces,
        )
        cacheByShipId[shipId] = built
        return built
    }

    private fun computeUpDir(shipTransform: ShipTransform): UpDir {
        val shipToWorld = shipTransform.shipToWorld

        tmpShipPos.set(0.0, 0.0, 0.0)
        shipToWorld.transformPosition(tmpShipPos, tmpWorldPos)
        val baseY = tmpWorldPos.y

        tmpShipPos.set(1.0, 0.0, 0.0)
        shipToWorld.transformPosition(tmpShipPos, tmpWorldPos)
        val incX = tmpWorldPos.y - baseY

        tmpShipPos.set(0.0, 1.0, 0.0)
        shipToWorld.transformPosition(tmpShipPos, tmpWorldPos)
        val incY = tmpWorldPos.y - baseY

        tmpShipPos.set(0.0, 0.0, 1.0)
        shipToWorld.transformPosition(tmpShipPos, tmpWorldPos)
        val incZ = tmpWorldPos.y - baseY

        val ax = abs(incX)
        val ay = abs(incY)
        val az = abs(incZ)

        return if (ay >= ax && ay >= az) {
            val dy = if (incY >= 0.0) 1 else -1
            UpDir(dx = 0, dy = dy, dz = 0, incWorldY = abs(incY))
        } else if (ax >= az) {
            val dx = if (incX >= 0.0) 1 else -1
            UpDir(dx = dx, dy = 0, dz = 0, incWorldY = abs(incX))
        } else {
            val dz = if (incZ >= 0.0) 1 else -1
            UpDir(dx = 0, dy = 0, dz = dz, incWorldY = abs(incZ))
        }
    }

    private fun renderSurfacesShipSpaceCameraRelative(
        level: Level,
        shipTransform: ShipTransform,
        sprite: TextureAtlasSprite,
        upDir: UpDir,
        surfaceWaterCells: LongArray,
        cameraShipPos: Vector3d,
        vc: com.mojang.blaze3d.vertex.VertexConsumer,
    ) {
        val u0 = sprite.u0
        val u1 = sprite.u1
        val v0 = sprite.v0
        val v1 = sprite.v1

        for (packedPos in surfaceWaterCells) {
            val x = BlockPos.getX(packedPos)
            val y = BlockPos.getY(packedPos)
            val z = BlockPos.getZ(packedPos)

            val shift = computeClampShiftShipUnits(level, shipTransform, upDir, x, y, z)
            // If this surface is at the true world water surface, let vanilla render it and avoid z-fighting/color
            // differences between "real" and "fake" water.
            if (!isWorldPointInWater(level, tmpWorldPos.x, tmpWorldPos.y + WORLD_SURFACE_CONTACT_EPS, tmpWorldPos.z)) {
                continue
            }

            val color = BiomeColors.getAverageWaterColor(level, tmpWorldBlockPos)
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val light = LevelRenderer.getLightColor(level, tmpWorldBlockPos)

            withFaceVerticesShipSpace(upDir, x.toDouble(), y.toDouble(), z.toDouble(), shift) { x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3 ->
                // Only emit one face. The translucent RenderType typically renders with culling disabled, and emitting
                // both sides causes Z-fighting and makes the surface too opaque/dark (double-blending).
                emitQuadCameraRelativeShipSpace(
                    vc,
                    cameraShipPos,
                    x0, y0, z0,
                    x1, y1, z1,
                    x2, y2, z2,
                    x3, y3, z3,
                    u0, v0, u1, v1,
                    upDir.dx.toFloat(), upDir.dy.toFloat(), upDir.dz.toFloat(),
                    r, g, b, 0xFF,
                    light,
                )
            }
        }
    }

    private fun emitQuadCameraRelativeShipSpace(
        vc: com.mojang.blaze3d.vertex.VertexConsumer,
        cameraShipPos: Vector3d,
        sx0: Double, sy0: Double, sz0: Double,
        sx1: Double, sy1: Double, sz1: Double,
        sx2: Double, sy2: Double, sz2: Double,
        sx3: Double, sy3: Double, sz3: Double,
        u0: Float, v0: Float, u1: Float, v1: Float,
        nx: Float, ny: Float, nz: Float,
        r: Int, g: Int, b: Int, a: Int,
        light: Int,
    ) {
        fun v(sx: Double, sy: Double, sz: Double, u: Float, v: Float) {
            vc.vertex(sx - cameraShipPos.x, sy - cameraShipPos.y, sz - cameraShipPos.z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(nx, ny, nz)
                .endVertex()
        }

        v(sx0, sy0, sz0, u0, v0)
        v(sx1, sy1, sz1, u0, v1)
        v(sx2, sy2, sz2, u1, v1)
        v(sx3, sy3, sz3, u1, v0)
    }

    /**
     * Computes how far to shift the surface plane downward (in ship units along [upDir]) to ensure the fake surface
     * never exceeds the local world water surface height.
     *
     * Also updates [tmpWorldBlockPos] to a representative world block position near the surface for lighting/tint.
     */
    private fun computeClampShiftShipUnits(
        level: Level,
        shipTransform: ShipTransform,
        upDir: UpDir,
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val shipToWorld = shipTransform.shipToWorld

        // Sample at the center of the (unshifted) face.
        val cx = x + 0.5 + upDir.dx * 0.5
        val cy = y + 0.5 + upDir.dy * 0.5
        val cz = z + 0.5 + upDir.dz * 0.5

        tmpShipPos.set(cx, cy, cz)
        shipToWorld.transformPosition(tmpShipPos, tmpWorldPos)
        tmpWorldBlockPos.set(Mth.floor(tmpWorldPos.x), Mth.floor(tmpWorldPos.y), Mth.floor(tmpWorldPos.z))

        val waterSurfaceY = findWorldWaterSurfaceY(level, tmpWorldPos.x, tmpWorldPos.y, tmpWorldPos.z)
            ?: return 0.0

        val deltaY = waterSurfaceY - tmpWorldPos.y
        if (deltaY >= 0.0) return 0.0
        if (upDir.incWorldY <= 1e-12) return 0.0

        // Convert world-space vertical shift to ship-space units along the chosen "up" axis.
        return deltaY / upDir.incWorldY
    }

    private fun findWorldWaterSurfaceY(level: Level, x: Double, y: Double, z: Double): Double? {
        // Fast path: if we're already inside water, use the local fluid height in this block.
        tmpWorldBlockPos2.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))
        var fs = level.getFluidState(tmpWorldBlockPos2)
        if (!fs.isEmpty && fs.`is`(Fluids.WATER)) {
            val height = fs.getHeight(level, tmpWorldBlockPos2).toDouble()
            return tmpWorldBlockPos2.y.toDouble() + height
        }

        // Otherwise, scan down a short distance to find the nearest water surface below.
        val maxScan = 64
        val baseX = Mth.floor(x)
        val baseY = Mth.floor(y)
        val baseZ = Mth.floor(z)
        var dy = 0
        while (dy <= maxScan) {
            tmpWorldBlockPos2.set(baseX, baseY - dy, baseZ)
            fs = level.getFluidState(tmpWorldBlockPos2)
            if (!fs.isEmpty && fs.`is`(Fluids.WATER)) {
                val height = fs.getHeight(level, tmpWorldBlockPos2).toDouble()
                return tmpWorldBlockPos2.y.toDouble() + height
            }
            dy++
        }

        return null
    }

    private fun isWorldPointInWater(level: Level, x: Double, y: Double, z: Double): Boolean {
        tmpWorldBlockPos2.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))
        val fs = level.getFluidState(tmpWorldBlockPos2)
        if (fs.isEmpty || !fs.`is`(Fluids.WATER)) return false

        val height = fs.getHeight(level, tmpWorldBlockPos2).toDouble()
        if (height <= 0.0) return false

        val yInBlock = y - tmpWorldBlockPos2.y.toDouble()
        return yInBlock < height - FLUID_HEIGHT_EPS
    }

    private inline fun withFaceVerticesShipSpace(
        upDir: UpDir,
        x: Double,
        y: Double,
        z: Double,
        shift: Double,
        cb: (Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double) -> Unit,
    ) {
        val dx = upDir.dx
        val dy = upDir.dy
        val dz = upDir.dz

        // Face plane coordinate (shifted along upDir axis).
        val fx = if (dx > 0) x + 1.0 else x
        val fy = if (dy > 0) y + 1.0 else y
        val fz = if (dz > 0) z + 1.0 else z

        when {
            dx != 0 -> {
                val planeX = fx + shift * dx
                cb(
                    planeX, y, z,
                    planeX, y, z + 1.0,
                    planeX, y + 1.0, z + 1.0,
                    planeX, y + 1.0, z,
                )
            }
            dy != 0 -> {
                val planeY = fy + shift * dy
                cb(
                    x, planeY, z,
                    x, planeY, z + 1.0,
                    x + 1.0, planeY, z + 1.0,
                    x + 1.0, planeY, z,
                )
            }
            dz != 0 -> {
                val planeZ = fz + shift * dz
                cb(
                    x, y, planeZ,
                    x + 1.0, y, planeZ,
                    x + 1.0, y + 1.0, planeZ,
                    x, y + 1.0, planeZ,
                )
            }
            else -> {}
        }
    }

    private fun getEventShip(event: Any): ClientShip? {
        return try {
            event.javaClass.getMethod("getShip").invoke(event) as? ClientShip
        } catch (_: Throwable) {
            null
        }
    }

    private fun getEventCamX(event: Any): Double? = try {
        event.javaClass.getMethod("getCamX").invoke(event) as? Double
    } catch (_: Throwable) {
        null
    }

    private fun getEventCamY(event: Any): Double? = try {
        event.javaClass.getMethod("getCamY").invoke(event) as? Double
    } catch (_: Throwable) {
        null
    }

    private fun getEventCamZ(event: Any): Double? = try {
        event.javaClass.getMethod("getCamZ").invoke(event) as? Double
    } catch (_: Throwable) {
        null
    }

    private fun getEventMatrices(event: Any): Any? {
        return try {
            event.javaClass.getMethod("getMatrices").invoke(event)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSodiumProjection(matrices: Any): org.joml.Matrix4fc? {
        return try {
            matrices.javaClass.getMethod("projection").invoke(matrices) as? org.joml.Matrix4fc
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSodiumModelView(matrices: Any): org.joml.Matrix4fc? {
        return try {
            matrices.javaClass.getMethod("modelView").invoke(matrices) as? org.joml.Matrix4fc
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSodiumPassLayer(event: Any): RenderType? {
        val pass = try {
            event.javaClass.getMethod("getPass").invoke(event)
        } catch (_: Throwable) {
            null
        } ?: return null

        return try {
            val field = pass.javaClass.getDeclaredField("layer")
            field.isAccessible = true
            field.get(pass) as? RenderType
        } catch (_: Throwable) {
            null
        }
    }
}
