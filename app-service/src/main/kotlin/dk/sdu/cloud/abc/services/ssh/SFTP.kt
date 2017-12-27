package dk.sdu.cloud.abc.services.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException

// TODO I'm not certain we should always disconnect after running a command. We might have to, but not sure.
fun <R> SSHConnection.sftp(body: ChannelSftp.() -> R): R = openSFTPChannel().run {
    connect()
    try {
        body()
    } finally {
        disconnect()
    }
}

fun SSHConnection.ls(path: String): List<ChannelSftp.LsEntry> {
    val allFiles = ArrayList<ChannelSftp.LsEntry>()
    sftp {
        ls(path) {
            allFiles.add(it); ChannelSftp.LsEntrySelector.CONTINUE
        }
    }
    return allFiles
}

fun SSHConnection.stat(path: String): SftpATTRS? =
        sftp {
            try {
                stat(path)
            } catch (ex: SftpException) {
                null
            }
        }
