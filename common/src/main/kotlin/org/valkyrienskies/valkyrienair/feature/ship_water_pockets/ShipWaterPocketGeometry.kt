package org.valkyrienskies.valkyrienair.feature.ship_water_pockets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.round

private const val GEOMETRY_FACE_SAMPLES_BASE = 8
private const val GEOMETRY_FACE_SAMPLES_REFINED = 16
private const val GEOMETRY_SAMPLE_EPS = 1e-4

internal data class ShapeWaterGeometry(
    val fullSolid: Boolean,
    val refined: Boolean,
    val boxes: List<AABB>,
)

private fun isGameplaySealedState(state: BlockState): Boolean {
    val block = state.block
    return when (block) {
        is DoorBlock -> !state.getValue(BlockStateProperties.OPEN)
        is TrapDoorBlock -> !state.getValue(BlockStateProperties.OPEN)
        is FenceGateBlock -> !state.getValue(BlockStateProperties.OPEN)
        else -> false
    }
}

private fun isFullCubeBox(box: AABB): Boolean {
    val eps = 1e-6
    return box.minX <= eps && box.minY <= eps && box.minZ <= eps &&
        box.maxX >= 1.0 - eps && box.maxY >= 1.0 - eps && box.maxZ >= 1.0 - eps
}

private fun requiresRefinedSampling(shape: VoxelShape, boxes: List<AABB>): Boolean {
    if (shape.isEmpty() || boxes.isEmpty()) return false
    if (boxes.size != 1) return true

    val box = boxes[0]
    val eps = 1e-6
    fun isGridAligned(v: Double): Boolean {
        val n = v * GEOMETRY_FACE_SAMPLES_BASE.toDouble()
        return abs(n - round(n)) <= eps
    }

    // Partial or angled-ish shapes with non-grid-aligned boundaries get refined sampling.
    return !isGridAligned(box.minX) || !isGridAligned(box.maxX) ||
        !isGridAligned(box.minY) || !isGridAligned(box.maxY) ||
        !isGridAligned(box.minZ) || !isGridAligned(box.maxZ)
}

internal fun computeShapeWaterGeometry(level: Level, pos: BlockPos, state: BlockState): ShapeWaterGeometry {
    if (state.isAir) return ShapeWaterGeometry(fullSolid = false, refined = false, boxes = emptyList())
    if (isGameplaySealedState(state)) {
        return ShapeWaterGeometry(
            fullSolid = true,
            refined = true,
            boxes = listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
        )
    }

    var shape = state.getCollisionShape(level, pos)
    if (shape.isEmpty()) {
        shape = state.getOcclusionShape(level, pos)
    }
    if (shape.isEmpty()) {
        return ShapeWaterGeometry(fullSolid = false, refined = false, boxes = emptyList())
    }

    val boxes = shape.toAabbs()
    val fullSolid = state.isCollisionShapeFullBlock(level, pos) || boxes.any(::isFullCubeBox)
    if (fullSolid) {
        return ShapeWaterGeometry(
            fullSolid = true,
            refined = false,
            boxes = listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
        )
    }

    return ShapeWaterGeometry(
        fullSolid = false,
        refined = requiresRefinedSampling(shape, boxes),
        boxes = boxes,
    )
}

private fun isSolidAt(geom: ShapeWaterGeometry, x: Double, y: Double, z: Double): Boolean {
    if (geom.fullSolid) return true
    if (geom.boxes.isEmpty()) return false
    val eps = 1e-8
    for (box in geom.boxes) {
        if (
            x >= box.minX - eps && x <= box.maxX + eps &&
            y >= box.minY - eps && y <= box.maxY + eps &&
            z >= box.minZ - eps && z <= box.maxZ + eps
        ) {
            return true
        }
    }
    return false
}

internal fun hasOpenVolume(geom: ShapeWaterGeometry): Boolean {
    if (geom.fullSolid) return false
    if (geom.boxes.isEmpty()) return true

    val samples = if (geom.refined) GEOMETRY_FACE_SAMPLES_REFINED else GEOMETRY_FACE_SAMPLES_BASE
    for (sz in 0 until samples) {
        val z = (sz + 0.5) / samples.toDouble()
        for (sy in 0 until samples) {
            val y = (sy + 0.5) / samples.toDouble()
            for (sx in 0 until samples) {
                val x = (sx + 0.5) / samples.toDouble()
                if (!isSolidAt(geom, x, y, z)) return true
            }
        }
    }
    return false
}

internal fun computeFaceConductance(geomA: ShapeWaterGeometry, geomB: ShapeWaterGeometry, axis: Int): Int {
    if (geomA.fullSolid || geomB.fullSolid) return 0

    val samples = if (geomA.refined || geomB.refined) GEOMETRY_FACE_SAMPLES_REFINED else GEOMETRY_FACE_SAMPLES_BASE
    val eps = GEOMETRY_SAMPLE_EPS
    var openSamples = 0

    for (sv in 0 until samples) {
        val v = (sv + 0.5) / samples.toDouble()
        for (su in 0 until samples) {
            val u = (su + 0.5) / samples.toDouble()

            val aSolid: Boolean
            val bSolid: Boolean
            when (axis) {
                0 -> { // +X face of A to -X face of B
                    aSolid = isSolidAt(geomA, 1.0 - eps, u, v)
                    bSolid = isSolidAt(geomB, eps, u, v)
                }
                1 -> { // +Y face of A to -Y face of B
                    aSolid = isSolidAt(geomA, u, 1.0 - eps, v)
                    bSolid = isSolidAt(geomB, u, eps, v)
                }
                else -> { // +Z face of A to -Z face of B
                    aSolid = isSolidAt(geomA, u, v, 1.0 - eps)
                    bSolid = isSolidAt(geomB, u, v, eps)
                }
            }

            if (!aSolid && !bSolid) {
                openSamples++
            }
        }
    }

    return openSamples
}

internal fun computeInteriorMaskHeuristic(open: BitSet, sizeX: Int, sizeY: Int, sizeZ: Int): BitSet {
    val volumeLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
    if (volumeLong <= 0L) return BitSet()
    val volume = volumeLong.toInt()

    if (open.isEmpty) return BitSet(volume)

    val strideY = sizeX
    val strideZ = sizeX * sizeY

    val negX = BitSet(volume)
    val posX = BitSet(volume)
    val negY = BitSet(volume)
    val posY = BitSet(volume)
    val negZ = BitSet(volume)
    val posZ = BitSet(volume)
    val negXZ = BitSet(volume)
    val posXZ = BitSet(volume)
    val negXPosZ = BitSet(volume)
    val posXNegZ = BitSet(volume)

    // X direction (for each Y/Z line).
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            val base = sizeX * (y + sizeY * z)
            var seenSolid = false
            for (x in 0 until sizeX) {
                val idx = base + x
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negX.set(idx)
                }
            }
            seenSolid = false
            for (x in sizeX - 1 downTo 0) {
                val idx = base + x
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posX.set(idx)
                }
            }
        }
    }

    // Y direction (for each X/Z line).
    for (z in 0 until sizeZ) {
        for (x in 0 until sizeX) {
            var seenSolid = false
            var idx = x + strideZ * z
            for (y in 0 until sizeY) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negY.set(idx)
                }
                idx += strideY
            }
            seenSolid = false
            idx = x + strideZ * z + strideY * (sizeY - 1)
            for (y in sizeY - 1 downTo 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posY.set(idx)
                }
                idx -= strideY
            }
        }
    }

    // Z direction (for each X/Y line).
    for (y in 0 until sizeY) {
        for (x in 0 until sizeX) {
            var seenSolid = false
            var idx = x + strideY * y
            for (z in 0 until sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negZ.set(idx)
                }
                idx += strideZ
            }
            seenSolid = false
            idx = x + strideY * y + strideZ * (sizeZ - 1)
            for (z in sizeZ - 1 downTo 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posZ.set(idx)
                }
                idx -= strideZ
            }
        }
    }

    // Diagonal directions in the XZ plane (for each Y slice).
    val diagInc = 1 + strideZ
    val diagDec = 1 - strideZ
    val diagDecRev = strideZ - 1
    for (y in 0 until sizeY) {
        val yBase = strideY * y

        // (-X,-Z) / (+X,+Z)
        for (startX in 0 until sizeX) {
            var x = startX
            var z = 0
            var idx = yBase + startX
            var seenSolid = false
            while (x < sizeX && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXZ.set(idx)
                }
                x++
                z++
                idx += diagInc
            }
        }
        for (startZ in 1 until sizeZ) {
            var x = 0
            var z = startZ
            var idx = yBase + strideZ * startZ
            var seenSolid = false
            while (x < sizeX && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXZ.set(idx)
                }
                x++
                z++
                idx += diagInc
            }
        }
        for (startX in 0 until sizeX) {
            var x = startX
            var z = sizeZ - 1
            var idx = yBase + startX + strideZ * (sizeZ - 1)
            var seenSolid = false
            while (x >= 0 && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXZ.set(idx)
                }
                x--
                z--
                idx -= diagInc
            }
        }
        for (startZ in sizeZ - 2 downTo 0) {
            var x = sizeX - 1
            var z = startZ
            var idx = yBase + (sizeX - 1) + strideZ * startZ
            var seenSolid = false
            while (x >= 0 && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXZ.set(idx)
                }
                x--
                z--
                idx -= diagInc
            }
        }

        // (-X,+Z) / (+X,-Z)
        for (startX in 0 until sizeX) {
            var x = startX
            var z = sizeZ - 1
            var idx = yBase + startX + strideZ * (sizeZ - 1)
            var seenSolid = false
            while (x < sizeX && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXPosZ.set(idx)
                }
                x++
                z--
                idx += diagDec
            }
        }
        for (startZ in sizeZ - 2 downTo 0) {
            var x = 0
            var z = startZ
            var idx = yBase + strideZ * startZ
            var seenSolid = false
            while (x < sizeX && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXPosZ.set(idx)
                }
                x++
                z--
                idx += diagDec
            }
        }
        for (startX in 0 until sizeX) {
            var x = startX
            var z = 0
            var idx = yBase + startX
            var seenSolid = false
            while (x >= 0 && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXNegZ.set(idx)
                }
                x--
                z++
                idx += diagDecRev
            }
        }
        for (startZ in 1 until sizeZ) {
            var x = sizeX - 1
            var z = startZ
            var idx = yBase + (sizeX - 1) + strideZ * startZ
            var seenSolid = false
            while (x >= 0 && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXNegZ.set(idx)
                }
                x--
                z++
                idx += diagDecRev
            }
        }
    }

    val interior = BitSet(volume)
    var idx = open.nextSetBit(0)
    while (idx >= 0 && idx < volume) {
        val lx = idx % sizeX
        val t = idx / sizeX
        val ly = t % sizeY
        val lz = t / sizeY

        if (lx == 0 || lx + 1 == sizeX || ly == 0 || ly + 1 == sizeY || lz == 0 || lz + 1 == sizeZ) {
            idx = open.nextSetBit(idx + 1)
            continue
        }

        val pairX = negX.get(idx) && posX.get(idx)
        val pairY = negY.get(idx) && posY.get(idx)
        val pairZ = negZ.get(idx) && posZ.get(idx)
        val pairDiagA = negXZ.get(idx) && posXZ.get(idx)
        val pairDiagB = negXPosZ.get(idx) && posXNegZ.get(idx)

        var axisPairs = 0
        if (pairX) axisPairs++
        if (pairY) axisPairs++
        if (pairZ) axisPairs++
        if (pairDiagA) axisPairs++
        if (pairDiagB) axisPairs++

        val diagPairs = (if (pairDiagA) 1 else 0) + (if (pairDiagB) 1 else 0)
        if (axisPairs >= 3 && (pairY || diagPairs == 2)) {
            interior.set(idx)
        }

        idx = open.nextSetBit(idx + 1)
    }

    return interior
}
