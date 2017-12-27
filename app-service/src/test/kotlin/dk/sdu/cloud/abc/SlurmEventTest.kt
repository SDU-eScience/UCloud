package dk.sdu.cloud.abc

import dk.sdu.cloud.abc.services.SlurmEvent
import dk.sdu.cloud.abc.services.SlurmEventBegan
import dk.sdu.cloud.abc.services.SlurmEventEnded
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class SlurmEventTest {
    @Test
    fun testVariousEndedEvents() {
        fun validateEvent(text: String, expected: SlurmEvent) {
            assertEquals(expected, SlurmEvent.parse(text))
        }

        validateEvent(
                "SLURM Job_id=1547428 Name=job.sh Ended, Run time 00:00:05, COMPLETED, ExitCode 0",
                SlurmEventEnded(1547428, "job.sh", Duration.ofSeconds(5), "COMPLETED", 0)
        )

        validateEvent(
                "SLURM Job_id=1547040 Name=job.sh Ended, Run time 00:00:03, COMPLETED, ExitCode 0",
                SlurmEventEnded(1547040, "job.sh", Duration.ofSeconds(3), "COMPLETED", 0)
        )

        validateEvent(
                "SLURM Job_id=1545902 Name=job.sh Ended, Run time 10:20:06, COMPLETED, ExitCode 0",
                SlurmEventEnded(
                        1545902,
                        "job.sh",
                        Duration.ofHours(10).plus(Duration.ofMinutes(20).plus(Duration.ofSeconds(6))),
                        "COMPLETED",
                        0
                )
        )
    }

    @Test
    fun testVariousStartedEvents() {
        fun validateEvent(text: String, expected: SlurmEvent) {
            assertEquals(expected, SlurmEvent.parse(text))
        }

        validateEvent(
                "SLURM Job_id=1545902 Name=job.sh Began, Queued time 00:00:00",
                SlurmEventBegan(1545902, "job.sh", Duration.ofSeconds(0))
        )


        validateEvent(
                "SLURM Job_id=1545902 Name=job.sh Began, Queued time 00:20:00",
                SlurmEventBegan(1545902, "job.sh", Duration.ofMinutes(20))
        )

        validateEvent(
                "SLURM Job_id=1545902 Name=job.sh Began, Queued time 10:00:00",
                SlurmEventBegan(1545902, "job.sh", Duration.ofHours(10))
        )
    }
}