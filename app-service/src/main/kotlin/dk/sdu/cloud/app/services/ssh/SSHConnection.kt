package dk.sdu.cloud.app.services.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import java.io.File

data class SimpleSSHConfig(
        val server: String,
        val port: Int,
        val keyName: String,
        val user: String,
        val keyPassword: String,
        val keyHome: String = System.getProperty("user.home") + File.separator + ".ssh"
)

class SSHConnection(val session: Session) {
    fun openExecChannel(): ChannelExec = session.openChannel("exec") as ChannelExec
    fun openSFTPChannel(): ChannelSftp = session.openChannel("sftp") as ChannelSftp

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

    fun execWithOutputAsText(command: String): Pair<Int, String> =
            exec(command) { inputStream.bufferedReader().readText() }
}

fun ChannelExec.awaitClosed(timeout: Long = 1000, pollRate: Long = 10): Boolean {
    val deadline = System.currentTimeMillis() + timeout
    while (System.currentTimeMillis() < deadline && !isClosed) {
        Thread.sleep(pollRate)
    }
    return isClosed
}
