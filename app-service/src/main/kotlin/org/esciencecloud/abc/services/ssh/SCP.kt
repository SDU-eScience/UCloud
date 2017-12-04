package org.esciencecloud.abc.services.ssh

import org.esciencecloud.abc.util.BashEscaper
import org.esciencecloud.abc.util.CappedInputStream
import org.esciencecloud.storage.ext.GuardedOutputStream
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private val log = LoggerFactory.getLogger("org.esciencecloud.abc.scp")

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

fun SSHConnection.scpUpload(fileLength: Long, fileName: String, fileDestination: String, filePermissions: String,
                            fileWriter: (OutputStream) -> Unit): Int {
    val execChannel = openExecChannel()

    val ins = execChannel.inputStream
    val outs = execChannel.outputStream

    log.info("Setting command")
    execChannel.setCommand("scp -t ${BashEscaper.safeBashArgument(fileDestination)}")
    execChannel.connect()
    scpCheckAck(ins).also { if (it != 0) return it }

    log.info("Writing file permissions and length")
    outs.write("C$filePermissions $fileLength $fileName\n".toByteArray())
    outs.flush()
    scpCheckAck(ins).also { if (it != 0) return it }

    log.info("Transferring file")
    fileWriter(GuardedOutputStream(outs))
    outs.write(byteArrayOf(0))
    outs.flush()
    log.info("Done!")
    scpCheckAck(ins).also { if (it != 0) return it }
    log.info("Transfer done!")
    execChannel.disconnect()
    log.info("Closed yet?" + execChannel.awaitClosed())
    return execChannel.exitStatus
}

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
        scpCheckAck(ins).also { if (it != 67) return@exec it }

        ins.skip(5) // Discard octal permissions

        val fileSize = readStringUntil(ins, ' '.toInt()).toLongOrNull() ?:
                throw IllegalStateException("Unexpected file size")
        val fileName = readStringUntil(ins, 0xA)
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
