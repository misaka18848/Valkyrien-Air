package org.valkyrienskies.valkyrienair.forge.client

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.valkyrienskies.valkyrienair.client.ValkyrienAirModClient

class ValkyrienAirModForgeClient {
    companion object {
        @JvmStatic
        fun clientInit(event: FMLClientSetupEvent) {
            // Put anything initialized on forge-side client here.
            ValkyrienAirModClient.initClient()
        }
    }
}
