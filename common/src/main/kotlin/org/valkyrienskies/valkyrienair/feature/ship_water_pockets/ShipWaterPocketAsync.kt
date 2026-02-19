package org.valkyrienskies.valkyrienair.feature.ship_water_pockets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import java.util.BitSet

internal data class GeometryAsyncSnapshot(
    val generation: Long,
    val invalidationStamp: Long,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val floodFluid: Fluid,
    val blockStates: Array<BlockState>,
    val shapeGeometry: Array<ShapeWaterGeometry>,
)

internal data class GeometryAsyncResult(
    val generation: Long,
    val invalidationStamp: Long,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val open: BitSet,
    val exterior: BitSet,
    val interior: BitSet,
    val flooded: BitSet,
    val materializedWater: BitSet,
    val faceCondXP: ShortArray,
    val faceCondYP: ShortArray,
    val faceCondZP: ShortArray,
    val templatePalette: List<ShapeCellTemplate>,
    val templateIndexByVoxel: IntArray,
    val voxelExteriorComponentMask: LongArray,
    val voxelInteriorComponentMask: LongArray,
    val componentGraphDegraded: Boolean,
    val computeNanos: Long,
)

private val EMPTY_GEOMETRY = ShapeWaterGeometry(
    fullSolid = false,
    refined = false,
    boxes = emptyList(),
)

private fun canonicalFloodSource(fluid: Fluid): Fluid {
    return if (fluid is FlowingFluid) fluid.source else fluid
}

private fun isWaterloggableForFlood(state: BlockState, floodFluid: Fluid): Boolean {
    return canonicalFloodSource(floodFluid) == Fluids.WATER && state.hasProperty(BlockStateProperties.WATERLOGGED)
}

private const val MAX_COMPONENT_GRAPH_NODES = 12_000_000

private class ShapeTemplateKey(
    private val fullSolid: Boolean,
    private val refined: Boolean,
    private val boxBits: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShapeTemplateKey) return false
        if (fullSolid != other.fullSolid) return false
        if (refined != other.refined) return false
        return boxBits.contentEquals(other.boxBits)
    }

    override fun hashCode(): Int {
        var result = fullSolid.hashCode()
        result = 31 * result + refined.hashCode()
        result = 31 * result + boxBits.contentHashCode()
        return result
    }

    companion object {
        fun fromGeometry(geom: ShapeWaterGeometry): ShapeTemplateKey {
            val bits = LongArray(geom.boxes.size * 6)
            var i = 0
            for (box in geom.boxes) {
                bits[i++] = java.lang.Double.doubleToLongBits(box.minX)
                bits[i++] = java.lang.Double.doubleToLongBits(box.minY)
                bits[i++] = java.lang.Double.doubleToLongBits(box.minZ)
                bits[i++] = java.lang.Double.doubleToLongBits(box.maxX)
                bits[i++] = java.lang.Double.doubleToLongBits(box.maxY)
                bits[i++] = java.lang.Double.doubleToLongBits(box.maxZ)
            }
            return ShapeTemplateKey(
                fullSolid = geom.fullSolid,
                refined = geom.refined,
                boxBits = bits,
            )
        }
    }
}

internal fun captureGeometryAsyncSnapshot(
    level: Level,
    generation: Long,
    invalidationStamp: Long,
    minX: Int,
    minY: Int,
    minZ: Int,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
    floodFluid: Fluid,
): GeometryAsyncSnapshot {
    val volume = sizeX * sizeY * sizeZ
    val blockStates = Array(volume) { Blocks.AIR.defaultBlockState() }
    val shapeGeometry = Array(volume) { EMPTY_GEOMETRY }

    val pos = BlockPos.MutableBlockPos()
    var idx = 0
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            for (x in 0 until sizeX) {
                pos.set(minX + x, minY + y, minZ + z)
                val state = level.getBlockState(pos)
                blockStates[idx] = state
                shapeGeometry[idx] = computeShapeWaterGeometry(level, pos, state)
                idx++
            }
        }
    }

    return GeometryAsyncSnapshot(
        generation = generation,
        invalidationStamp = invalidationStamp,
        minX = minX,
        minY = minY,
        minZ = minZ,
        sizeX = sizeX,
        sizeY = sizeY,
        sizeZ = sizeZ,
        floodFluid = floodFluid,
        blockStates = blockStates,
        shapeGeometry = shapeGeometry,
    )
}

internal fun computeGeometryAsync(snapshot: GeometryAsyncSnapshot): GeometryAsyncResult {
    val startNanos = System.nanoTime()

    val sizeX = snapshot.sizeX
    val sizeY = snapshot.sizeY
    val sizeZ = snapshot.sizeZ
    val volume = sizeX * sizeY * sizeZ

    val open = BitSet(volume)
    val flooded = BitSet(volume)
    val materialized = BitSet(volume)
    val faceCondXP = ShortArray(volume)
    val faceCondYP = ShortArray(volume)
    val faceCondZP = ShortArray(volume)
    val templateIndexByVoxel = IntArray(volume)
    val templatePalette = ArrayList<ShapeCellTemplate>()
    val templateLookup = HashMap<ShapeTemplateKey, Int>()

    var idx = 0
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            for (x in 0 until sizeX) {
                val geom = snapshot.shapeGeometry[idx]
                val templateKey = ShapeTemplateKey.fromGeometry(geom)
                val templateIdx = templateLookup.getOrPut(templateKey) {
                    val next = templatePalette.size
                    templatePalette.add(buildShapeCellTemplate(geom))
                    next
                }
                templateIndexByVoxel[idx] = templateIdx
                val template = templatePalette[templateIdx]
                if (template.hasOpenVolume) {
                    open.set(idx)
                }

                val bs = snapshot.blockStates[idx]
                val fluidState = bs.fluidState
                if (!fluidState.isEmpty && canonicalFloodSource(fluidState.type) == snapshot.floodFluid) {
                    flooded.set(idx)
                    if (
                        bs.block is LiquidBlock ||
                        (isWaterloggableForFlood(bs, snapshot.floodFluid) &&
                            bs.getValue(BlockStateProperties.WATERLOGGED))
                    ) {
                        materialized.set(idx)
                    }
                }

                idx++
            }
        }
    }

    val strideY = sizeX
    val strideZ = sizeX * sizeY
    val nodeBaseByVoxel = IntArray(volume) { -1 }
    var nodeCount = 0
    var componentGraphDegraded = false

    var openIdx = open.nextSetBit(0)
    while (openIdx >= 0 && openIdx < volume) {
        val template = templatePalette[templateIndexByVoxel[openIdx]]
        val componentCount = template.componentCount
        if (componentCount > 0) {
            nodeBaseByVoxel[openIdx] = nodeCount
            nodeCount += componentCount
            if (nodeCount > MAX_COMPONENT_GRAPH_NODES) {
                componentGraphDegraded = true
                break
            }
        }
        openIdx = open.nextSetBit(openIdx + 1)
    }

    val parent = if (!componentGraphDegraded) IntArray(nodeCount) { it } else IntArray(0)
    val rank = if (!componentGraphDegraded) ByteArray(nodeCount) else ByteArray(0)
    val boundaryNode = if (!componentGraphDegraded) BooleanArray(nodeCount) else BooleanArray(0)

    fun findRoot(x: Int): Int {
        if (componentGraphDegraded) return 0
        var cur = x
        while (parent[cur] != cur) {
            cur = parent[cur]
        }
        var walk = x
        while (parent[walk] != walk) {
            val next = parent[walk]
            parent[walk] = cur
            walk = next
        }
        return cur
    }

    fun unionNodes(a: Int, b: Int) {
        if (componentGraphDegraded) return
        var ra = findRoot(a)
        var rb = findRoot(b)
        if (ra == rb) return

        val rankA = rank[ra].toInt()
        val rankB = rank[rb].toInt()
        if (rankA < rankB) {
            val t = ra
            ra = rb
            rb = t
        }

        parent[rb] = ra
        if (rankA == rankB) {
            rank[ra] = (rankA + 1).toByte()
        }
    }

    fun markBoundaryComponents(baseNode: Int, componentMask: Long) {
        if (componentGraphDegraded || componentMask == 0L || baseNode < 0) return
        var mask = componentMask
        while (mask != 0L) {
            val bit = java.lang.Long.numberOfTrailingZeros(mask)
            boundaryNode[baseNode + bit] = true
            mask = mask and (mask - 1L)
        }
    }

    idx = 0
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            for (x in 0 until sizeX) {
                if (!open.get(idx)) {
                    idx++
                    continue
                }

                val templateIdx = templateIndexByVoxel[idx]
                val template = templatePalette[templateIdx]
                val baseNode = nodeBaseByVoxel[idx]

                if (!componentGraphDegraded && baseNode >= 0) {
                    if (x == 0) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_NEG_X])
                    if (x + 1 == sizeX) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_POS_X])
                    if (y == 0) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_NEG_Y])
                    if (y + 1 == sizeY) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_POS_Y])
                    if (z == 0) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_NEG_Z])
                    if (z + 1 == sizeZ) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_POS_Z])
                }

                if (x + 1 < sizeX) {
                    val n = idx + 1
                    if (open.get(n)) {
                        val nTemplate = templatePalette[templateIndexByVoxel[n]]
                        var cond = 0
                        forEachTemplateFaceConnection(template, nTemplate, dirCodeFromA = 1) { compA, compB ->
                            cond++
                            if (!componentGraphDegraded) {
                                val nBase = nodeBaseByVoxel[n]
                                if (baseNode >= 0 && nBase >= 0) {
                                    unionNodes(baseNode + compA, nBase + compB)
                                }
                            }
                        }
                        faceCondXP[idx] = cond.toShort()
                    }
                }
                if (y + 1 < sizeY) {
                    val n = idx + strideY
                    if (open.get(n)) {
                        val nTemplate = templatePalette[templateIndexByVoxel[n]]
                        var cond = 0
                        forEachTemplateFaceConnection(template, nTemplate, dirCodeFromA = 3) { compA, compB ->
                            cond++
                            if (!componentGraphDegraded) {
                                val nBase = nodeBaseByVoxel[n]
                                if (baseNode >= 0 && nBase >= 0) {
                                    unionNodes(baseNode + compA, nBase + compB)
                                }
                            }
                        }
                        faceCondYP[idx] = cond.toShort()
                    }
                }
                if (z + 1 < sizeZ) {
                    val n = idx + strideZ
                    if (open.get(n)) {
                        val nTemplate = templatePalette[templateIndexByVoxel[n]]
                        var cond = 0
                        forEachTemplateFaceConnection(template, nTemplate, dirCodeFromA = 5) { compA, compB ->
                            cond++
                            if (!componentGraphDegraded) {
                                val nBase = nodeBaseByVoxel[n]
                                if (baseNode >= 0 && nBase >= 0) {
                                    unionNodes(baseNode + compA, nBase + compB)
                                }
                            }
                        }
                        faceCondZP[idx] = cond.toShort()
                    }
                }

                idx++
            }
        }
    }

    fun edgeCond(idxCur: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Int {
        return when (dirCode) {
            0 -> if (lx > 0) faceCondXP[idxCur - 1].toInt() and 0xFFFF else 0
            1 -> if (lx + 1 < sizeX) faceCondXP[idxCur].toInt() and 0xFFFF else 0
            2 -> if (ly > 0) faceCondYP[idxCur - strideY].toInt() and 0xFFFF else 0
            3 -> if (ly + 1 < sizeY) faceCondYP[idxCur].toInt() and 0xFFFF else 0
            4 -> if (lz > 0) faceCondZP[idxCur - strideZ].toInt() and 0xFFFF else 0
            else -> if (lz + 1 < sizeZ) faceCondZP[idxCur].toInt() and 0xFFFF else 0
        }
    }

    val exterior = BitSet(volume)
    val interior = BitSet(volume)
    val voxelExteriorComponentMask = LongArray(volume)
    val voxelInteriorComponentMask = LongArray(volume)

    if (componentGraphDegraded) {
        val strictExterior = floodFillFromBoundaryGraph(open, sizeX, sizeY, sizeZ) { idxCur, lx, ly, lz, dir ->
            edgeCond(idxCur, lx, ly, lz, dir)
        }
        val strictInterior = open.clone() as BitSet
        strictInterior.andNot(strictExterior)
        val heuristicInterior = computeInteriorMaskHeuristic(open, sizeX, sizeY, sizeZ)
        heuristicInterior.andNot(strictExterior)
        strictInterior.or(heuristicInterior)
        exterior.or(strictExterior)
        interior.or(strictInterior)

        openIdx = open.nextSetBit(0)
        while (openIdx >= 0 && openIdx < volume) {
            val template = templatePalette[templateIndexByVoxel[openIdx]]
            val fullMask = fullComponentMask(template.componentCount)
            if (strictExterior.get(openIdx)) {
                voxelExteriorComponentMask[openIdx] = fullMask
            }
            if (strictInterior.get(openIdx)) {
                voxelInteriorComponentMask[openIdx] = fullMask
            }
            openIdx = open.nextSetBit(openIdx + 1)
        }
    } else {
        val rootBoundary = BooleanArray(nodeCount)
        for (node in 0 until nodeCount) {
            if (!boundaryNode[node]) continue
            rootBoundary[findRoot(node)] = true
        }

        openIdx = open.nextSetBit(0)
        while (openIdx >= 0 && openIdx < volume) {
            val template = templatePalette[templateIndexByVoxel[openIdx]]
            val baseNode = nodeBaseByVoxel[openIdx]
            var exteriorMask = 0L
            var interiorMask = 0L

            if (baseNode >= 0) {
                for (component in 0 until template.componentCount) {
                    val root = findRoot(baseNode + component)
                    if (rootBoundary[root]) {
                        exteriorMask = exteriorMask or (1L shl component)
                    } else {
                        interiorMask = interiorMask or (1L shl component)
                    }
                }
            }

            voxelExteriorComponentMask[openIdx] = exteriorMask
            voxelInteriorComponentMask[openIdx] = interiorMask
            if (exteriorMask != 0L) exterior.set(openIdx)
            if (interiorMask != 0L) interior.set(openIdx)

            openIdx = open.nextSetBit(openIdx + 1)
        }

        // Keep legacy enclosure heuristic as a stabilizing projection layer for gameplay semantics:
        // component-level classification is primary, but heuristic-only interior voxels are promoted
        // with a full-component mask so flood/drain logic has a valid interior domain.
        val heuristicInterior = computeInteriorMaskHeuristic(open, sizeX, sizeY, sizeZ)
        var h = heuristicInterior.nextSetBit(0)
        while (h >= 0 && h < volume) {
            if (!open.get(h)) {
                h = heuristicInterior.nextSetBit(h + 1)
                continue
            }
            interior.set(h)
            if (voxelInteriorComponentMask[h] == 0L) {
                val template = templatePalette[templateIndexByVoxel[h]]
                voxelInteriorComponentMask[h] = fullComponentMask(template.componentCount)
            }
            h = heuristicInterior.nextSetBit(h + 1)
        }
    }

    return GeometryAsyncResult(
        generation = snapshot.generation,
        invalidationStamp = snapshot.invalidationStamp,
        minX = snapshot.minX,
        minY = snapshot.minY,
        minZ = snapshot.minZ,
        sizeX = sizeX,
        sizeY = sizeY,
        sizeZ = sizeZ,
        open = open,
        exterior = exterior,
        interior = interior,
        flooded = flooded,
        materializedWater = materialized,
        faceCondXP = faceCondXP,
        faceCondYP = faceCondYP,
        faceCondZP = faceCondZP,
        templatePalette = templatePalette,
        templateIndexByVoxel = templateIndexByVoxel,
        voxelExteriorComponentMask = voxelExteriorComponentMask,
        voxelInteriorComponentMask = voxelInteriorComponentMask,
        componentGraphDegraded = componentGraphDegraded,
        computeNanos = System.nanoTime() - startNanos,
    )
}
