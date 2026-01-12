package org.valkyrienskies.valkyrienair.fabric

import net.fabricmc.api.ModInitializer
import org.valkyrienskies.valkyrienair.ValkyrienAirMod

/**
 * The fabric-side initializer for the mod. Used for fabric-platform-specific code.
 */
class ValkyrienAirModFabric : ModInitializer {
    override fun onInitialize() {
        // Put anything initialized on fabric-side here, such as platform-specific registries.
        ValkyrienAirMod.init()
    }
}
