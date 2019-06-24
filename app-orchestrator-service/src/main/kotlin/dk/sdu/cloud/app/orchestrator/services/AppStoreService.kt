package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.throwIfInternal

class AppStoreService(
    private val serviceClient: AuthenticatedClient
) {
    private val appMap = HashMap<NameAndVersion, Application>()

    suspend fun findByNameAndVersion(name: String, version: String): Application? {
        val nameAndVersion = NameAndVersion(name, version)
        val cachedApp = appMap[nameAndVersion]
        if (cachedApp != null) return cachedApp

        val appWithFavorite = AppStore.findByNameAndVersion.call(
            FindApplicationAndOptionalDependencies(name, version),
            serviceClient
        ).throwIfInternal().orNull() ?: return null

        val app = Application(appWithFavorite.metadata, appWithFavorite.invocation)
        appMap[nameAndVersion] = app
        return app
    }
}