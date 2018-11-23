package dk.sdu.cloud.app.abacus.services.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import dk.sdu.cloud.app.abacus.services.ssh.SSH.log
import dk.sdu.cloud.service.BashEscaper
import dk.sdu.cloud.service.BashEscaper.safeBashArgument
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

fun SSHConnection.mkdir(path: String, createParents: Boolean = false): Int {
    val invocation = arrayListOf("mkdir")
    if (createParents) invocation += "-p"
    invocation += BashEscaper.safeBashArgument(path)
    val (status, output) = execWithOutputAsText(invocation.joinToString(" "))
    if (status != 0) {
        log.info("Unable to create directory at: $path")
        log.info("Status: $status, output: $output")
    }
    return status
}

fun SSHConnection.stat(path: String): SftpATTRS? =
    sftp {
        try {
            stat(path)
        } catch (ex: SftpException) {
            null
        }
    }

data class LSWithGlobResult(val fileName: String, val size: Long)

fun SSHConnection.lsWithGlob(baseDirectory: String, path: String): List<LSWithGlobResult> {
    val parentDirectory = baseDirectory.removeSuffix("/") + "/" + path.substringBeforeLast('/', ".")
    val query = baseDirectory.removeSuffix("/") + "/" + path.removeSuffix("/")
    // TODO FIXME Should this be escaped?
    return try {
        ls(query)
            .asSequence()
            .map { Pair(parentDirectory + "/" + it.filename, it.attrs.size) }
            .map { LSWithGlobResult(File(it.first).normalize().absolutePath, it.second) }
            .filter { it.fileName.startsWith(baseDirectory) }
            .toList()
    } catch (ex: SftpException) {
        if (ex.id == 2) return emptyList()
        else throw ex
    }
}

fun SSHConnection.rm(path: String, recurse: Boolean = false, force: Boolean = false): Int {
    val invocation = mutableListOf("rm")

    val flags = run {
        var flags = ""
        if (recurse) flags += "r"
        if (force) flags += "f"
        if (flags.isNotEmpty()) "-$flags" else null
    }
    if (flags != null) invocation += flags

    invocation += safeBashArgument(path)

    return execWithOutputAsText(invocation.joinToString(" ")).first
}

fun SSHConnection.linesInRange(path: String, startingAt: Int, maxLines: Int): Pair<Int, String> {
    val invocation = """
            bash -c "tail -n +$startingAt ${safeBashArgument(safeBashArgument(path))} | head -n $maxLines"
        """.trimIndent().lines().first()
    return execWithOutputAsText(invocation)
}
