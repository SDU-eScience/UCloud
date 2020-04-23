package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.RedisConnectionManager
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.redisConnectionManager
import io.lettuce.core.RedisFuture
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

interface DistributedLockFactory {
    fun create(name: String, duration: Long = 60_000): DistributedLock
}

class DistributedLockBestEffortFactory(private val micro: Micro) : DistributedLockFactory {
    override fun create(name: String, duration: Long): DistributedLock {
        return DistributedLockBestEffort(name, duration, micro.redisConnectionManager)
    }
}

interface DistributedLock {
    suspend fun acquire(): Boolean
    suspend fun release()
    suspend fun renew(durationMs: Long): Boolean
}

/**
 * A redis-based distributed lock.
 *
 * This lock works on a best effort and will work correctly in almost all circumstances. The algorithm attempts to make
 * sure that no two clients holds the lock at the same time. In some rare circumstances multiple clients might hold
 * the lock at the same time.
 *
 * More information here, the single instance algorithm is implemented: https://redis.io/topics/distlock
 */
class DistributedLockBestEffort(
    val name: String,
    val duration: Long = 60_000,
    private val connectionManager: RedisConnectionManager
) : DistributedLock {
    private val mutex = Mutex()
    private val key = "distributed-lock-$name"
    private var acquiredLockValue: String? = null
    private var lockCooldown: Long = 0

    override suspend fun acquire(): Boolean {
        return mutex.withLock {
            acquiredLockValue = Random.nextInt().toString()

            val result = runCatching {
                connectionManager.getConnection().set(
                    key,
                    acquiredLockValue,
                    SetArgs().px(duration).nx()
                ).await() == "OK"
            }.getOrDefault(false)

            if (!result) {
                acquiredLockValue = null
            }

            result
        }
    }

    override suspend fun release() {
        require(acquiredLockValue != null)

        mutex.withLock {
            connectionManager.getConnection().eval<Int>(
                RELEASE_SCRIPT,
                ScriptOutputType.INTEGER,
                arrayOf(key),
                acquiredLockValue
            )

            acquiredLockValue = null
        }
    }

    override suspend fun renew(durationMs: Long): Boolean {
        require(acquiredLockValue != null)

        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now > lockCooldown) {
                if (durationMs >= 5000) {
                    lockCooldown = now + (durationMs / 10)
                }

                return connectionManager.getConnection().eval<Long>(
                    RENEW_SCRIPT,
                    ScriptOutputType.INTEGER,
                    arrayOf(key),
                    acquiredLockValue,
                    (durationMs / 1000L).toString()
                ).get() != 0L
            }

            return true
        }
    }

    companion object {
        private val RELEASE_SCRIPT = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
        """.trimIndent()

        private val RENEW_SCRIPT = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("expire", KEYS[1], ARGV[2])
            else
                return 0
            end
        """.trimIndent()
    }
}

suspend inline fun <R> DistributedLock.withLock(block: () -> R): R {
    return try {
        while (!acquire()) {
            delay(Random.nextLong(150))
        }

        block()
    } finally {
        release()
    }
}

suspend fun <E> RedisFuture<E>.await(): E = suspendCoroutine { cont ->
    handle { resp, throwable ->
        if (throwable != null) {
            cont.resumeWithException(throwable)
        } else {
            cont.resume(resp)
        }
    }
}
