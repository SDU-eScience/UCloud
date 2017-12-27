package dk.sdu.cloud.app.services

import java.time.Duration

sealed class SlurmEvent {
    abstract val jobId: Long
    abstract val name: String

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

data class SlurmEventBegan(
        override val jobId: Long,
        override val name: String,
        val queueTime: Duration
) : SlurmEvent()

data class SlurmEventEnded(
        override val jobId: Long,
        override val name: String,
        val runTime: Duration,
        val status: String,
        val exitCode: Int
) : SlurmEvent()

data class SlurmEventUnknown(override val jobId: Long, override val name: String, val type: String) : SlurmEvent()

typealias SlurmEventListener = (SlurmEvent) -> Unit

