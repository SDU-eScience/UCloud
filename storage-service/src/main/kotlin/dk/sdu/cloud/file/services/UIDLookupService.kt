package dk.sdu.cloud.file.services

import dk.sdu.cloud.auth.api.LookupUIDRequest
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.throwIfInternalOrBadRequest
import java.util.concurrent.ConcurrentHashMap

interface UIDLookupService : StorageUserDao<Long> {
    suspend fun lookup(username: String): Long?
    suspend fun reverseLookup(uid: Long): String?
    fun storeMapping(username: String, cloudUid: Long)

    override suspend fun findCloudUser(uid: Long, verify: Boolean): String? {
        return reverseLookup(uid)
    }

    override suspend fun findStorageUser(cloudUser: String, verify: Boolean): Long? {
        return lookup(cloudUser)
    }
}

class DevelopmentUIDLookupService(val result: String): UIDLookupService {
    override suspend fun lookup(username: String): Long? {
        return 0L
    }

    override suspend fun reverseLookup(uid: Long): String? {
        return result
    }

    override fun storeMapping(username: String, cloudUid: Long) {}
}

class AuthUIDLookupService(
    private val serviceCloud: AuthenticatedCloud
) : UIDLookupService {
    private val cache = ConcurrentHashMap<String, Long>()
    private val reverseCache = ConcurrentHashMap<Long, String>()

    override suspend fun lookup(username: String): Long? {
        log.trace("Looking up user: $username")

        if (username == SERVICE_USER) return 0L

        val cachedResult = cache[username]
        if (cachedResult != null) {
            log.trace("Returning result from cache")
            return cachedResult
        }

        val response = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(listOf(username)),
            serviceCloud
        ).throwIfInternalOrBadRequest() as? RESTResponse.Ok ?: return null

        val uid = response.result.results[username]?.uid ?: return null

        storeMapping(username, uid)
        return moveUIDToUnixNS(uid)
    }

    override suspend fun reverseLookup(uid: Long): String? {
        if (uid < 1000) return SERVICE_USER

        val cachedResult = reverseCache[uid]
        if (cachedResult != null) {
            return cachedResult
        }

        val cloudUid = moveUIDFromUnixNS(uid)
        val response = UserDescriptions.lookupUID.call(
            LookupUIDRequest(listOf(cloudUid)),
            serviceCloud
        ).throwIfInternalOrBadRequest() as? RESTResponse.Ok ?: return null

        val lookupResult = response.result.users[cloudUid] ?: return null
        storeMapping(lookupResult.subject, lookupResult.uid)
        return lookupResult.subject
    }

    override fun storeMapping(username: String, cloudUid: Long) {
        log.debug("storeMapping($username, $cloudUid)")
        val unixUid = moveUIDToUnixNS(cloudUid)
        cache[username] = unixUid
        reverseCache[unixUid] = username
    }

    private fun moveUIDToUnixNS(uid: Long): Long = uid + 1000L
    private fun moveUIDFromUnixNS(uid: Long): Long = uid - 1000L

    companion object : Loggable {
        override val log = logger()

    }
}

