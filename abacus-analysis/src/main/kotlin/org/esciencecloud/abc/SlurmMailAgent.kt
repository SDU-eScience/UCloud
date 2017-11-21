package org.esciencecloud.abc

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import org.slf4j.LoggerFactory
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

typealias SlurmEventListener = (SlurmEvent) -> Unit

class SlurmMailAgent(private val config: MailAgentConfiguration) {
    private lateinit var idleThread: Thread
    private val eventListeners = ArrayList<SlurmEventListener>()
    private var isRunning = false
    private val log = LoggerFactory.getLogger(SlurmMailAgent::class.java)

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
        inbox.addMessageCountListener(object : MessageCountAdapter() {
            override fun messagesAdded(e: MessageCountEvent) {
                e.messages.forEach { processMessage(it) }
            }
        })

        val unreadMails = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
        unreadMails.forEach { processMessage(it) }

        idleThread = Thread {
            while (isRunning) {
                if (!store.isConnected) store.connect(config.username, config.password)
                inbox.idle()
            }
            inbox.close()
            store.close()
        }

        isRunning = true
        idleThread.start()
    }

    fun addListener(listener: SlurmEventListener) {
        eventListeners.add(listener)
    }

    fun removeListener(listener: SlurmEventListener) {
        eventListeners.remove(listener)
    }

    private fun processMessage(message: Message) {
        val from = message.from.mapNotNull { (it as? InternetAddress)?.address }
        val subject = message.subject
        if (!from.contains(SLURM_MAIL)) return
        val event = try {
            SlurmEvent.parse(message.subject)
        } catch (ex: Exception) {
            log.warn("Exception while parsing slurm event!")
            log.warn("From: $from")
            log.warn("Subject: $subject")
            log.warn("Exception was: ${ex.stackTraceToString()}")
            null
        }

        if (event == null) {
            log.warn("Was unable to parse Slurm message")
            log.warn("From: $from")
            log.warn("Subject: $subject")
        } else {
            eventListeners.forEach { it(event) }
        }

        // This should run after the listeners have been invoked, this way listeners will have a chance to mark the
        // event as having been processed. If they crash, then this e-mail notification will be replayed on restart.
        message.setFlag(Flags.Flag.SEEN, true)
    }

    fun stop() {
        // TODO Will probably need to wake up the idle command for this to work
        isRunning = false
    }
}

data class MailAgentConfiguration(val host: String, val port: Int, val username: String, val password: String)
