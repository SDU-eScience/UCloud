package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.services.ssh.SSHConnection
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import io.mockk.*
import org.junit.Test
import java.lang.Exception
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SlurmPollTest{

    @Test
    fun `normal run test` () {
        val ssh = mockk<SSHConnectionPool>()
        val scheduledExeService = Executors.newSingleThreadScheduledExecutor()
        val sshConnect = mockk<SSHConnection>()
        every { ssh.borrowConnection() } answers {
            every { sshConnect.execWithOutputAsText(any(), any()) } returns Pair(0, "8282|COMPLETED|0")
            Pair(0,sshConnect)
        }
        every { ssh.returnConnection(0) } just runs

        val slurmPoll = SlurmPollAgent(ssh, scheduledExeService, 2, 100, TimeUnit.SECONDS)
        slurmPoll.startTracking(8282)
        slurmPoll.start()
        Thread.sleep(10000)

        slurmPoll.addListener(mockk(relaxed = true))
        slurmPoll.removeListener(mockk(relaxed = true))
        slurmPoll.stop()

    }

    @Test
    fun `normal run - exception caught, continue test` () {
        val ssh = mockk<SSHConnectionPool>()
        val scheduledExeService = Executors.newSingleThreadScheduledExecutor()

        every { ssh.borrowConnection() } answers {
            throw Exception("Something went wrong")
        }
        every { ssh.returnConnection(0) } just runs

        val slurmPoll = SlurmPollAgent(ssh, scheduledExeService, 2, 100, TimeUnit.SECONDS)
        slurmPoll.startTracking(8282)
        slurmPoll.start()
        Thread.sleep(5000)

        slurmPoll.stop()

        verify { ssh.borrowConnection() }
    }

    @Test (expected = IllegalStateException::class)
    fun `start Test - already running`() {
        val ssh = mockk<SSHConnectionPool>()
        val scheduledExeService = Executors.newSingleThreadScheduledExecutor()

        val slurmPoll = SlurmPollAgent(ssh, scheduledExeService, 10, 100, TimeUnit.SECONDS)
        slurmPoll.start()
        slurmPoll.start()
    }

}