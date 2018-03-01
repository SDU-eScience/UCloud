package dk.sdu.cloud.app.services.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import java.io.File

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

fun SSHConnection.lsWithGlob(baseDirectory: String, path: String): List<Pair<String, Long>> {
    val hasGlob = path.substringAfterLast('/').contains("*")
    val parentDirectory = baseDirectory.removeSuffix("/") + "/" + path.substringBeforeLast('/', ".")
    val query = baseDirectory.removeSuffix("/") + "/" + path.removeSuffix("/")
    return try {
        ls(query)
            .map {
                if (hasGlob) Pair(parentDirectory + "/" + it.filename, it.attrs.size)
                else Pair(query + "/" + it.filename, it.attrs.size)
            }
            .map { Pair(File(it.first).normalize().absolutePath, it.second) }
            .filter { it.first.startsWith(baseDirectory) }
    } catch (ex: SftpException) {
        if (ex.id == 2) return emptyList()
        else throw ex
    }
}
