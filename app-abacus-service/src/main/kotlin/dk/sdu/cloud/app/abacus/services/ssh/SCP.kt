package dk.sdu.cloud.app.abacus.services.ssh

import dk.sdu.cloud.app.abacus.services.ssh.SSH.log
import dk.sdu.cloud.app.abacus.util.CappedInputStream
import dk.sdu.cloud.service.BashEscaper
import dk.sdu.cloud.service.GuardedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private fun scpCheckAck(ins: InputStream): Int {
    val b = ins.read()
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if (b == 0) return b
    if (b == -1) return b

    if (b == 1 || b == 2) {
        val sb = StringBuffer()
        var c: Int
        do {
            c = ins.read()
            sb.append(c.toChar())
        } while (c != '\n'.toInt())
        if (b == 1) { // error
            print(sb.toString())
        }
        if (b == 2) { // fatal error
            print(sb.toString())
        }
    }
    return b
}

fun SSHConnection.scpUpload(file: File, destination: String, permissions: String) =
    scpUpload(
        fileLength = file.length(),
        fileName = file.name,
        fileDestination = destination,
        filePermissions = permissions
    ) { out ->
        out.write(file.readBytes())
    }

fun SSHConnection.scpUpload(
    fileLength: Long, fileName: String, fileDestination: String, filePermissions: String,
    fileWriter: (OutputStream) -> Unit
): Int {
    log.debug(
        "scpUpload(fileLength=$fileLength, fileName=$fileName, fileDestination=$fileDestination, " +
                "filePermissions=$filePermissions, fileWriter=$fileWriter)"
    )
    val execChannel = openExecChannel()

    val ins = execChannel.inputStream
    val outs = execChannel.outputStream

    val scpCommand = "scp -t ${BashEscaper.safeBashArgument(fileDestination)}"
    log.debug("Setting command: $scpCommand")
    execChannel.setCommand(scpCommand)
    execChannel.connect()
    scpCheckAck(ins).also { if (it != 0) return it }

    log.debug("Writing file permissions and length")
    outs.write("C$filePermissions $fileLength $fileName\n".toByteArray())
    outs.flush()
    scpCheckAck(ins).also { if (it != 0) return it }

    log.debug("Transferring file")
    fileWriter(GuardedOutputStream(outs))
    outs.write(byteArrayOf(0))
    outs.flush()
    log.debug("Done! Awaiting acknowledgement")
    scpCheckAck(ins).also { if (it != 0) return it }
    log.debug("Acknowledgement received. Transfer done!")
    execChannel.disconnect()
    log.debug("Closed yet?" + execChannel.awaitClosed())
    val exitStatus = execChannel.exitStatus // TODO Bugged
    return if (exitStatus == -1) 0 else exitStatus
}

private const val SSH_KEY_EXCHANGE_FAILED_STATUSCODE = 67
private const val DELIMITER_IN_HEX = 0xA
private const val SKIP_CONSTANT = 5L

fun SSHConnection.scpDownload(remoteFile: String, body: (InputStream) -> Unit): Int {
    fun requireNotEOF(read: Int) {
        if (read < 0) throw IllegalStateException("Unexpected EOF")
    }

    fun readStringUntil(ins: InputStream, delim: Int): String {
        val builder = StringBuilder()
        while (ins.read().also { requireNotEOF(it); builder.append(it.toChar()) } != delim);
        return builder.substring(0, builder.lastIndex)
    }

    return exec("scp -f ${BashEscaper.safeBashArgument(remoteFile)}") {
        val outs = outputStream
        val ins = inputStream

        outs.write(0)
        outs.flush()
        scpCheckAck(ins).also { if (it != SSH_KEY_EXCHANGE_FAILED_STATUSCODE) return@exec it }

        ins.skip(SKIP_CONSTANT) // Discard octal permissions

        val fileSize =
            readStringUntil(ins, ' '.toInt()).toLongOrNull() ?: throw IllegalStateException("Unexpected file size")
        val fileName = readStringUntil(ins, DELIMITER_IN_HEX)
        println(fileSize)
        println(fileName)

        outs.write(0)
        outs.flush()

        val cappedIns = CappedInputStream(ins, fileSize)
        body(cappedIns)
        if (!cappedIns.isEmpty) {
            cappedIns.skipRemaining()
        }

        scpCheckAck(ins).also { if (it != 0) return@exec it }
        outs.write(0)
        outs.flush()

        0
    }.second
}
