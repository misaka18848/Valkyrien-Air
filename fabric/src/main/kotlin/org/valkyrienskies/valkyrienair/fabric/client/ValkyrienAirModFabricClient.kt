package org.valkyrienskies.valkyrienair.fabric.client

import net.fabricmc.api.ClientModInitializer
import org.valkyrienskies.valkyrienair.client.ValkyrienAirModClient

/**
 * The fabric-side client initializer for the mod. Used for fabric-platform-specific code that runs on the client exclusively.
 */
class ValkyrienAirModFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Put anything initialized on fabric-side client here.
        ValkyrienAirModClient.initClient()
    }
}
