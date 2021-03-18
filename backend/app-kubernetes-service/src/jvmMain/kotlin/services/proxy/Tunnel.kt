package dk.sdu.cloud.app.kubernetes.services.proxy

import kotlinx.coroutines.runBlocking
import java.io.Closeable

@Suppress("ConstructorParameterNaming")
class Tunnel(
    val jobId: String,
    val rank: Int,
    val ipAddress: String,
    val localPort: Int,
    private val _isAlive: suspend () -> Boolean,
    private val _close: suspend () -> Unit,
) : Closeable {
    suspend fun isAlive() = _isAlive()
    override fun close() = runBlocking { _close() }
}
