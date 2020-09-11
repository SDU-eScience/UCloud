package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.InternetProtocol
import dk.sdu.cloud.app.orchestrator.api.PortAndProtocol
import dk.sdu.cloud.app.orchestrator.api.PublicIP
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.safeUsername

class PublicIPService {
    suspend fun applyForAddress(
        ctx: DBContext,
        actor: Actor,
        project: String?,
        application: String
    ): Long {
        TODO()
    }

    suspend fun lookupAddressById(
        ctx: DBContext,
        actor: Actor,
        id: Long
    ): PublicIP {
        // NOTE(Dan): Used for testing purposes
        // TODO: Replace it
        return PublicIP(
            id,
            "10.135.0.142",
            actor.safeUsername(),
            WalletOwnerType.USER,
            listOf(
                PortAndProtocol(11042, InternetProtocol.TCP)
            )
        )
    }
}