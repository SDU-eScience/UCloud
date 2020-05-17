package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.service.SimpleCache

/**
 * Acts as a cache of applications and tools
 */
class ApplicationService(private val serviceClient: AuthenticatedClient) {
    val tools = SimpleCache<NameAndVersion, Tool>(SimpleCache.DONT_EXPIRE) {
        ToolStore.findByNameAndVersion
            .call(FindByNameAndVersion(it.name, it.version), serviceClient)
            .throwIfInternal()
            .orNull()
    }

    val apps = SimpleCache<NameAndVersion, Application>(SimpleCache.DONT_EXPIRE) { nv ->
        AppStore.findByNameAndVersion
            .call(FindApplicationAndOptionalDependencies(nv.name, nv.version), serviceClient)
            .throwIfInternal()
            .orNull()
            ?.let { Application(it.metadata, it.invocation) }
    }
}
