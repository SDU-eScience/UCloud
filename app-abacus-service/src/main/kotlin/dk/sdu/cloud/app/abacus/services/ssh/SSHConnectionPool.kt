package dk.sdu.cloud.app.abacus.services.ssh

import com.jcraft.jsch.JSch
import dk.sdu.cloud.service.Loggable
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class SSHConnectionPool(
    private val config: SimpleSSHConfig,
    maxConnections: Int = 8,
    private val timeout: Long = 60,
    private val timeoutUnit: TimeUnit = TimeUnit.SECONDS
) {
    // The number of permits permits must _always_ match the number of available elements!
    private val permits = Semaphore(maxConnections)
    private val objectPool = Array<SSHConnection?>(maxConnections) { null }
    private val available = Array(maxConnections) { true }

    // Access to the pool elements (objectPool and available) must always be synchronized on, regardless of permit
    private val poolLock = Any()

    fun borrowConnection(): Pair<Int, SSHConnection> {
        fun getAndValidate(index: Int): SSHConnection? {
            val conn = objectPool[index] ?: return null
            return if (!conn.session.isConnected) null else conn
        }

        permits.acquire()
        synchronized(poolLock) {
            val index = available.indexOfFirst { it }
            assert(index != -1) // If this happens then some permit was released prematurely
            val connection = getAndValidate(index) ?: openNewConnection()

            objectPool[index] = connection
            available[index] = false
            return Pair(index, connection)
        }
    }

    fun returnConnection(idx: Int) {
        // We must mark as available before we start waking up other threads via permits.release()
        synchronized(objectPool) { available[idx] = true }

        permits.release()
    }

    private fun openNewConnection(): SSHConnection {
        val sshKeyLoc = File(File(config.keyHome), config.keyName)
        val knownHostsFile = File(File(config.keyHome), "known_hosts")
        log.info("Connecting to ${config.server}:${config.port} with key $sshKeyLoc")

        if (!knownHostsFile.exists()) {
            throw IllegalArgumentException("Could not find known hosts! Expected at: ${knownHostsFile.absolutePath}")
        }

        val jsch = JSch()
        jsch.setKnownHosts(FileInputStream(knownHostsFile))
        jsch.addIdentity(sshKeyLoc.absolutePath, config.keyPassword)

        val session = jsch.getSession(config.user, config.server, config.port)
        session.timeout = timeoutUnit.toMillis(timeout).toInt()

        try {
            session.connect()
        } catch (ex: Exception) {
            throw IllegalStateException("Unable to connect to ${config.server}.", ex)
        }

        log.info("Connected")
        return SSHConnection(session)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

inline fun <R> SSHConnectionPool.use(body: SSHConnection.() -> R): R {
    val (idx, session) = borrowConnection()
    return try {
        body(session)
    } finally {
        returnConnection(idx)
    }
}
