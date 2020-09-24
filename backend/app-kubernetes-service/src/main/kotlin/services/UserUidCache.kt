package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.service.SimpleCache

class UserUidCache(private val serviceClient: AuthenticatedClient) {
    val cache = SimpleCache<String, Long>(1000 * 60 * 60 * 24 * 7L) { username ->
        UserDescriptions.lookupUsers.call(
            LookupUsersRequest(listOf(username)),
            serviceClient
        ).orNull()?.results?.get(username)?.uid
    }
}