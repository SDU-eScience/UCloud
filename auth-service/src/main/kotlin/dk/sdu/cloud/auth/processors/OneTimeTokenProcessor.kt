package dk.sdu.cloud.auth.processors

import dk.sdu.cloud.auth.api.OneTimeTokenEvent
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate
import org.slf4j.LoggerFactory

class OneTimeTokenProcessor(
    private val stream: KStream<String, OneTimeTokenEvent>
) {
    fun init() {
        log.info("Initializing...")

        val branches = stream.branch(
            Predicate { _, value -> value is OneTimeTokenEvent.Created },
            Predicate { _, value -> value is OneTimeTokenEvent.Claimed }
        )

        // TODO We don't really need this for anything. It is more a log than anything else.
        val createdStream = branches[0].mapValues { it as OneTimeTokenEvent.Created }
        val claimedStream = branches[1].mapValues { it as OneTimeTokenEvent.Claimed }

        createdStream.foreach { _, value ->
            log.info("Handling event: $value")
        }

        claimedStream.foreach { _, value ->
            log.info("Handling event: $value")
        }

        log.info("Initialized!")
    }

    companion object {
        private val log = LoggerFactory.getLogger(RefreshTokenProcessor::class.java)
    }
}
