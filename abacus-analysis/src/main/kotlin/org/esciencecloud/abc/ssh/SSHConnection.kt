package org.esciencecloud.abc.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.slf4j.LoggerFactory
import java.io.*

data class SimpleSSHConfig(val server: String, val port: Int, val keyName: String, val user: String,
                           val keyPassword: String)

class SSHConnection(private val session: Session) : Closeable {
    override fun close() {
        session.disconnect()
    }

    fun openExecChannel(): ChannelExec = session.openChannel("exec") as ChannelExec

    fun <T> exec(command: String, body: ChannelExec.() -> T): Pair<Int, T> =
            openExecChannel().run {
                setCommand(command)
                connect()
                val res = try {
                    body()
                } finally {
                    disconnect()
                    awaitClosed()
                }

                Pair(exitStatus, res)
            }

    companion object {
        private val log = LoggerFactory.getLogger(SSHConnection::class.java)

        fun connect(config: SimpleSSHConfig): SSHConnection {
            val sshKeyLoc = File(File(System.getProperty("user.home"), ".ssh"), config.keyName)
            val knownHostsFile = File(File(System.getProperty("user.home"), ".ssh"), "known_hosts")
            log.info("Connecting to ${config.server}:${config.port} with key $sshKeyLoc")

            if (!knownHostsFile.exists()) throw IllegalArgumentException("Could not find known hosts!")

            val jsch = JSch()
            jsch.setKnownHosts(FileInputStream(knownHostsFile))
            val session = jsch.getSession(config.user, config.server, config.port)

            jsch.addIdentity(sshKeyLoc.absolutePath, config.keyPassword)
            try {
                session.connect()
            } catch (ex: Exception) {
                println("${session.hostKey.host} ${session.hostKey.key}")
                ex.printStackTrace()
                System.exit(0)
            }
            log.info("Connected")
            return SSHConnection(session)
        }
    }
}

fun ChannelExec.awaitClosed(timeout: Long = 1000, pollRate: Long = 10): Boolean {
    val deadline = System.currentTimeMillis() + timeout
    while (System.currentTimeMillis() < deadline && !isClosed) {
        Thread.sleep(pollRate)
    }
    return isClosed
}
