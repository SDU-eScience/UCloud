package org.esciencecloud.abc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.irods.IRodsConnectionInformation
import org.esciencecloud.storage.ext.irods.IRodsStorageConnectionFactory
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.*
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import javax.mail.internet.InternetAddress
import javax.mail.search.FlagTerm

sealed class SlurmEvent(val jobId: Long, val name: String) {
    companion object {
        val BASE_REGEX = Regex("SLURM Job_id=(\\d+) Name=(([^,]|[ \\t\\w])+) (([^,]|[ \\t\\w])+),(.+)")
        val BEGAN_REGEX = Regex("Queued time (\\d+):(\\d+):(\\d+)")
        val ENDED_REGEX = Regex("Run time (\\d+):(\\d+):(\\d+), (.+), ExitCode (\\d+)")

        private fun parsePeriodFromResults(match: MatchResult, startIdx: Int): Duration {
            val text = match.groupValues[0]
            val hours = match.groupValues[startIdx].toLongOrNull() ?:
                    throw IllegalStateException("Could not parse hours: $text")

            val minutes = match.groupValues[startIdx + 1].toLongOrNull() ?:
                    throw IllegalStateException("Could not parse minutes: $text")

            val seconds = match.groupValues[startIdx + 2].toLongOrNull() ?:
                    throw IllegalStateException("Could not parse seconds: $text")

            return Duration.ofHours(hours).plus(Duration.ofMinutes(minutes)).plus(Duration.ofSeconds(seconds))
        }

        fun parse(text: String): SlurmEvent? {
            val matches = BASE_REGEX.find(text) ?: return null

            // WE REALLY NEED TO CONTROL THE NAME SUCH THAT IT DOESN'T CONTAIN BAD STUFF LIKE COMMAS!
            val jobId = matches.groupValues[1].toLongOrNull() ?:
                    throw IllegalStateException("Could not convert job ID to long! Input: $text")
            val name = matches.groupValues[2]
            val eventType = matches.groupValues[4]
            val remaining = matches.groupValues[6]

            if (!text.startsWith("SLURM")) return null

            return when (eventType) {
                "Began" -> {
                    val beganMatches = BEGAN_REGEX.find(remaining) ?:
                            throw IllegalStateException("Unable to parse remaining part of began event: $text")
                    val duration = parsePeriodFromResults(beganMatches, 1)

                    SlurmEventBegan(jobId, name, duration)
                }

                "Ended" -> {
                    val endedMatches = ENDED_REGEX.find(remaining) ?:
                            throw IllegalStateException("Unable to parse remaining part of ended event: $text")
                    val duration = parsePeriodFromResults(endedMatches, 1)
                    val status = endedMatches.groupValues[4]
                    val exitCode = endedMatches.groupValues[5].toIntOrNull() ?:
                            throw IllegalStateException("Unable to parse exit code from ended event: $text")

                    SlurmEventEnded(jobId, name, duration, status, exitCode)
                }

                else -> SlurmEventUnknown(jobId, name, eventType)
            }
        }
    }
}

class SlurmEventBegan(jobId: Long, name: String, val queueTime: Duration) : SlurmEvent(jobId, name) {
    override fun toString(): String = "SlurmEventBegan(jobId=$jobId, name=$name, queueTime=$queueTime)"
}

class SlurmEventEnded(jobId: Long, name: String, val runTime: Duration, val status: String, val exitCode: Int)
    : SlurmEvent(jobId, name) {
    override fun toString(): String = "SlurmEventEnded(jobId=$jobId, name=$name, runTime=$runTime, status='$status', " +
            "exitCode=$exitCode)"
}

class SlurmEventUnknown(jobId: Long, name: String, val type: String) : SlurmEvent(jobId, name)

class MailAgent(private val config: MailAgentConfiguration) {
    private lateinit var idleThread: Thread
    private lateinit var sshConnection: SSHConnection
    private lateinit var adminConnection: StorageConnection
    private var isRunning = false

    companion object {
        private val SLURM_MAIL = "slurm@deic.sdu.dk"
    }

    fun start() {
        if (isRunning) throw IllegalStateException("Mail agent is already running!")

        val session = Session.getInstance(Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", config.host)
            put("mail.imaps.port", config.port)
            put("mail.imaps.timeout", "10000")
        })

        val store = session.getStore("imaps") as IMAPStore
        store.connect(config.username, config.password)

        if (!store.hasCapability("IDLE")) {
            throw IllegalStateException("IDLE capability not supported by server, but required by this mail agent!")
        }

        val inbox = store.getFolder("INBOX") as IMAPFolder
        inbox.open(Folder.READ_WRITE)
        // TODO This should probably read old (unread) messages too
        inbox.addMessageCountListener(object : MessageCountAdapter() {
            override fun messagesAdded(e: MessageCountEvent) {
                e.messages.forEach { processMessage(it) }
            }
        })


        // ---------------------------------------------------------
        // The last part doesn't belong here, just for demo purposes
        // ---------------------------------------------------------
        val irods = IRodsStorageConnectionFactory(IRodsConnectionInformation(
                host = "localhost",
                zone = "tempZone",
                port = 1247,
                storageResource = "radosRandomResc",
                sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE,
                authScheme = AuthScheme.STANDARD
        ))
        adminConnection = irods.createForAccount("rods", "rods").orThrow()

        val mapper = jacksonObjectMapper()
        val sshConfig = mapper.readValue<SimpleConfiguration>(File("ssh_conf.json"))
        sshConnection = SSHConnection.connect(sshConfig)

        val log = LoggerFactory.getLogger("MailAgent")
        log.info("Ready!")
        // ---------------------------------------------------------

        val unreadMails = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
        unreadMails.forEach { processMessage(it) }

        idleThread = Thread {
            while (isRunning) {
                if (!store.isConnected) store.connect(config.username, config.password)
                inbox.idle()
            }
        }

        isRunning = true
        idleThread.start()

    }

    private fun processMessage(message: Message) {
        val from = message.from.map { (it as? InternetAddress)?.address }.filterNotNull()
        val subject = message.subject
        println(from)
        println(subject)
        if (!from.contains(SLURM_MAIL)) return
        val event = SlurmEvent.parse(message.subject)
        when (event) {
            is SlurmEventBegan -> println("We just began a job! $event")
            is SlurmEventEnded -> {
                println("We are done! $event")
                sshConnection.scpDownload("/home/dthrane/slurm-${event.jobId}.out") {
                    adminConnection.files.put(adminConnection.paths.homeDirectory.push("output-${event.jobId}.txt"), it)
                }
            }
        }
        message.setFlag(Flags.Flag.SEEN, true)
    }

    fun stop() {
        isRunning = false
    }
}

data class MailAgentConfiguration(val host: String, val port: Int, val username: String, val password: String)

fun main(args: Array<String>) {
    val mapper = jacksonObjectMapper()
    val conf = mapper.readValue<MailAgentConfiguration>(File("mail_conf.json"))
    val agent = MailAgent(conf)
    agent.start()
    while (true) {
        Thread.sleep(100)
    }
}