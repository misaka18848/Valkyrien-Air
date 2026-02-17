package org.valkyrienskies.valkyrienair.feature.ship_water_pockets

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth

internal fun indexOf(state: ShipPocketState, lx: Int, ly: Int, lz: Int): Int =
    lx + state.sizeX * (ly + state.sizeY * lz)

internal fun posFromIndex(state: ShipPocketState, idx: Int, out: BlockPos.MutableBlockPos): BlockPos.MutableBlockPos {
    val sx = state.sizeX
    val sy = state.sizeY
    val lx = idx % sx
    val t = idx / sx
    val ly = t % sy
    val lz = t / sy
    return out.set(state.minX + lx, state.minY + ly, state.minZ + lz)
}

internal fun edgeConductance(state: ShipPocketState, idx: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Int {
    if (state.faceCondXP.isEmpty() || state.faceCondYP.isEmpty() || state.faceCondZP.isEmpty()) return 1
    val sizeX = state.sizeX
    val sizeY = state.sizeY
    return when (dirCode) {
        0 -> if (lx > 0) state.faceCondXP[idx - 1].toInt() and 0xFFFF else 0
        1 -> if (lx + 1 < sizeX) state.faceCondXP[idx].toInt() and 0xFFFF else 0
        2 -> if (ly > 0) state.faceCondYP[idx - sizeX].toInt() and 0xFFFF else 0
        3 -> if (ly + 1 < sizeY) state.faceCondYP[idx].toInt() and 0xFFFF else 0
        4 -> if (lz > 0) state.faceCondZP[idx - sizeX * sizeY].toInt() and 0xFFFF else 0
        else -> if (lz + 1 < state.sizeZ) state.faceCondZP[idx].toInt() and 0xFFFF else 0
    }
}

internal fun isOpenAtShipPoint(
    state: ShipPocketState,
    x: Double,
    y: Double,
    z: Double,
    tmp: BlockPos.MutableBlockPos,
): Boolean {
    tmp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))
    return isOpen(state, tmp)
}

internal fun isInterior(state: ShipPocketState, shipPos: BlockPos): Boolean {
    val lx = shipPos.x - state.minX
    val ly = shipPos.y - state.minY
    val lz = shipPos.z - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
    val idx = indexOf(state, lx, ly, lz)
    return state.interior.get(idx)
}

internal fun isOpen(state: ShipPocketState, shipPos: BlockPos): Boolean {
    val lx = shipPos.x - state.minX
    val ly = shipPos.y - state.minY
    val lz = shipPos.z - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
    val idx = indexOf(state, lx, ly, lz)
    return state.open.get(idx)
}

internal fun isAirPocket(state: ShipPocketState, shipPos: BlockPos): Boolean {
    val lx = shipPos.x - state.minX
    val ly = shipPos.y - state.minY
    val lz = shipPos.z - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
    val idx = indexOf(state, lx, ly, lz)
    return state.unreachableVoid.get(idx) && !state.materializedWater.get(idx)
}
