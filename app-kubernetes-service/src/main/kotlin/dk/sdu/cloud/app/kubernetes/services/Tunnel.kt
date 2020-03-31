package dk.sdu.cloud.app.kubernetes.services

import java.io.Closeable

@Suppress("ConstructorParameterNaming")
class Tunnel(
    val jobId: String,
    val ipAddress: String,
    val localPort: Int,
    val urlId: String?,
    private val _isAlive: () -> Boolean,
    private val _close: () -> Unit
) : Closeable {
    fun isAlive() = _isAlive()
    override fun close() = _close()
}
