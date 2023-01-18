package dk.sdu.cloud.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Use this class when you have any section which modifies local users and local groups. This ensures that no race
 * conditions arises which might cause users to not be added to all the appropriate groups.
 *
 * Examples of when to use:
 *
 * - When creating a local user
 * - When adding/removing a user to/from a group
 */
object UserGroupLocks {
    private val rootMutex = Mutex()
    private val userMutexes = HashMap<String, Mutex>()

    suspend fun <R> useLockByUCloudUsername(why: String, ucloudId: String, fn: suspend () -> R): R {
        debug("Wait lock: $why [$ucloudId]")
        try {
            val userMutex = rootMutex.withLock {
                userMutexes.getOrPut(ucloudId) { Mutex() }
            }

            return userMutex.withLock {
                debug("Got lock: $why [$ucloudId]")
                fn()
            }
        } finally {
            debug("Unlock: $why [$ucloudId]")
        }
    }

    suspend fun <R> useLocksByUCloudUsernames(why: String, ucloudIds: List<String>, fn: suspend () -> R): R {
        if (ucloudIds.isEmpty()) return fn()

        debug("Wait lock: $why $ucloudIds")
        val mutexes = rootMutex.withLock {
            ucloudIds.map { ucloudId ->
                userMutexes.getOrPut(ucloudId) { Mutex() }
            }
        }

        mutexes.forEach { it.lock() }
        try {
            debug("Got lock: $why $ucloudIds")
            return fn()
        } finally {
            mutexes.forEach { it.unlock() }
            debug("Unlock: $why $ucloudIds")
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun debug(message: String) {
        if (DEBUG) println(message)
    }

    private const val DEBUG = false
}
