package dk.sdu.cloud.app.kubernetes.services

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private data class TunnelWithUsageTracking(val tunnel: Tunnel, var lastUsed: Long)

class TunnelManager(
    private val podService: PodService
) {
    private val openTunnels = HashMap<String, TunnelWithUsageTracking>()
    private val mutex = Mutex()
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor()
    private val usedPorts = HashSet<Int>()

    fun install() {
        cleanupExecutor.schedule({
            runBlocking {
                mutex.withLock {
                    val now = System.currentTimeMillis()

                    val iterator = openTunnels.iterator()
                    while (iterator.hasNext()) {
                        val (_, tunnelWithTracking) = iterator.next()
                        if (!tunnelWithTracking.tunnel.isAlive()) {
                            iterator.remove()
                        }

                        if (now - tunnelWithTracking.lastUsed > 60_000 * 15) {
                            tunnelWithTracking.tunnel.close()
                            iterator.remove()
                        }
                    }
                }
            }
        }, 30, TimeUnit.SECONDS)
    }

    fun shutdown() {
        cleanupExecutor.shutdownNow()
    }

    suspend fun refresh(id: String) {
        mutex.withLock {
            openTunnels[id]?.lastUsed = System.currentTimeMillis()
        }
    }

    suspend fun createOrUseExistingTunnel(id: String, remotePort: Int): Tunnel {
        mutex.withLock {
            val existing = openTunnels[id]
            val alive = existing?.tunnel?.isAlive() ?: false
            if (existing != null && !alive) {
                existing.tunnel.close()
            }

            return if (existing != null && alive) {
                existing.lastUsed = System.currentTimeMillis()
                existing.tunnel
            } else {
                val localPort: Int = run {
                    while (true) {
                        val localPort = Random.nextInt(30000, 64000)
                        if (localPort !in usedPorts) {
                            return@run localPort
                        }
                    }

                    // Needed to compile code
                    @Suppress("UNREACHABLE_CODE")
                    return@run -1
                }

                val newTunnel = podService.createTunnel(id, localPort, remotePort)
                openTunnels[id] = TunnelWithUsageTracking(newTunnel, System.currentTimeMillis())
                newTunnel
            }
        }
    }
}
