package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.store.api.FindByNameAndVersion
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.throwIfInternal

class ToolStoreService(
    private val serviceClient: AuthenticatedClient
) {
    private val toolMap = HashMap<NameAndVersion, Tool>()

    suspend fun findByNameAndVersion(name: String, version: String): Tool? {
        val nameAndVersion = NameAndVersion(name, version)
        val cachedTool = toolMap[nameAndVersion]
        if (cachedTool != null) return cachedTool

        val tool = ToolStore.findByNameAndVersion
            .call(FindByNameAndVersion(name, version), serviceClient)
            .throwIfInternal()
            .orNull() ?: return null

        toolMap[nameAndVersion] = tool
        return tool
    }
}