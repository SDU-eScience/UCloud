package dk.sdu.cloud.app.kubernetes.services.proxy

import kotlinx.coroutines.runBlocking
import java.io.Closeable

@Suppress("ConstructorParameterNaming")
class Tunnel(
    val jobId: String,
    val ipAddress: String,
    val localPort: Int,
    val urlId: String?,
    private val _isAlive: suspend () -> Boolean,
    private val _close: suspend () -> Unit,
    val originalIpAddress: String = ipAddress,
    val originalPort: Int = localPort
) : Closeable {
    suspend fun isAlive() = _isAlive()
    override fun close() = runBlocking { _close() }
}
