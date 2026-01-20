package org.valkyrienskies.valkyrienair.client

import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCullRenderContext

/**
 * The common static client object that represents the mod.
 */
object ValkyrienAirModClient {
    @JvmStatic
    fun initClient() {
        VSGameEvents.renderShip.on {
            ShipWaterPocketExternalWaterCullRenderContext.beginShipRender()
        }
        VSGameEvents.postRenderShip.on {
            ShipWaterPocketExternalWaterCullRenderContext.endShipRender()
        }

        VSGameEvents.renderShipSodium.on {
            ShipWaterPocketExternalWaterCullRenderContext.beginShipRender()
        }
        VSGameEvents.postRenderShipSodium.on {
            ShipWaterPocketExternalWaterCullRenderContext.endShipRender()
        }
    }
}
