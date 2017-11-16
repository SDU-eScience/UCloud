package org.esciencecloud.abc

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.esciencecloud.storage.ext.GuardedOutputStream
import org.esciencecloud.storage.ext.irods.IRodsConnectionInformation
import org.esciencecloud.storage.ext.irods.IRodsStorageConnectionFactory
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory
import java.io.*

data class SimpleConfiguration(val server: String, val port: Int, val keyName: String, val user: String,
                               val keyPassword: String)

fun main(args: Array<String>) {
    val irods = IRodsStorageConnectionFactory(IRodsConnectionInformation(
            host = "localhost",
            zone = "tempZone",
            port = 1247,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE,
            authScheme = AuthScheme.STANDARD
    ))
    val adminConnection = irods.createForAccount("rods", "rods").orThrow()
    val irodsPath = adminConnection.paths.parseAbsolute("/tempZone/home/rods/hello.txt")

    val mapper = jacksonObjectMapper().apply {
        enable(JsonParser.Feature.ALLOW_COMMENTS)
    }

    val config = mapper.readValue<SimpleConfiguration>(File("ssh_conf.json"))
    SSHConnection.connect(config).use { sshConnection ->
        // Upload data from iRODS to Abacus
        val stat = adminConnection.fileQuery.stat(irodsPath).orThrow()
        sshConnection.scp(stat.sizeInBytes, "hello.txt", "/home/dthrane/hello.txt", "0644") {
            adminConnection.files.get(irodsPath, it)
        }

        // Submit a job and retrieve job ID
        val (exit, output, jobId) = sshConnection.sbatch("/home/dthrane/test_job_mail.sh")
        println(exit)
        println(output)
        println(jobId)
    }
}

class SSHConnection(val session: Session) : Closeable {
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

        fun connect(config: SimpleConfiguration): SSHConnection {
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


data class SBatchSubmissionResult(val exitCode: Int, val output: String, val jobId: Long?)

private val SBATCH_SUBMIT_REGEX = Regex("Submitted batch job (\\d+)")
fun SSHConnection.sbatch(file: String, vararg args: String): SBatchSubmissionResult {
    val (exit, output) = exec("sbatch $file ${args.joinToString(" ")}") { inputStream.reader().readText() }

    val match = SBATCH_SUBMIT_REGEX.find(output)
    return if (match != null) {
        SBatchSubmissionResult(exit, output, match.groupValues[1].toLong())
    } else {
        SBatchSubmissionResult(exit, output, null)
    }
}


fun SSHConnection.scp(file: File, destination: String, permissions: String) =
        scp(
                fileLength = file.length(),
                fileName = file.name,
                fileDestination = destination,
                filePermissions = permissions
        ) { out ->
            out.write(file.readBytes())
        }

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

fun SSHConnection.scp(fileLength: Long, fileName: String, fileDestination: String, filePermissions: String,
                      fileWriter: (OutputStream) -> Unit): Int {
    val execChannel = openExecChannel()
    val log = LoggerFactory.getLogger("SCP")


    val ins = execChannel.inputStream
    val outs = execChannel.outputStream

    log.info("Setting command")
    execChannel.setCommand("scp -t $fileDestination")
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

    return exec("scp -f $remoteFile") {
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

class CappedInputStream(private val delegate: InputStream, private val maxBytes: Long) : InputStream() {
    private var remainingBytes = maxBytes

    val isEmpty get() = remainingBytes == 0L

    fun skipRemaining() {
        skip(remainingBytes)
    }

    override fun read(): Int {
        if (remainingBytes == 0L) return -1
        return delegate.read().also { remainingBytes-- }
    }

    override fun read(b: ByteArray): Int = read(b, 0, b.size)

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remainingBytes == 0L) return -1

        val actualLength = if (remainingBytes < len) remainingBytes.toInt() else len
        return delegate.read(b, off, actualLength).also { remainingBytes -= it }
    }

    override fun skip(n: Long): Long {
        val actualLength = if (remainingBytes < n) remainingBytes else n
        return delegate.skip(actualLength)
    }

    override fun available(): Int {
        val avail = delegate.available()
        return if (remainingBytes < avail) remainingBytes.toInt() else avail
    }

    override fun reset() {
        throw UnsupportedOperationException("reset not supported")
    }

    override fun close() {
        // Do nothing
    }

    override fun mark(readlimit: Int) {
        throw UnsupportedOperationException("mark not supported")
    }

    override fun markSupported(): Boolean = false
}

fun ChannelExec.awaitClosed(timeout: Long = 1000, pollRate: Long = 10): Boolean {
    val deadline = System.currentTimeMillis() + timeout
    while (System.currentTimeMillis() < deadline && !isClosed) {
        Thread.sleep(pollRate)
    }
    return isClosed
}

