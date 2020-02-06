package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.MachineReservation
import dk.sdu.cloud.app.orchestrator.api.MountMode
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.orchestrator.utils.validatedFileForUpload
import dk.sdu.cloud.app.orchestrator.utils.verifiedJob
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobForTestGenerator
import dk.sdu.cloud.app.store.api.SimpleDuration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobComparatorTest {

    @Test
    fun `compare same job`() {
        val jc = JobComparator()
        assertTrue(jc.jobsEqual(verifiedJob, verifiedJob))
    }

    @Test
    fun `compare different jobs`() {
        val jc = JobComparator()

        //Test compare with different maxTime, nodes, tasksPerNode.
        run {
            val jobNodes = verifiedJobForTestGenerator(nodes = 2)
            val jobTasksPerNode = verifiedJobForTestGenerator(tasksPerNode = 2)
            val jobTime = verifiedJobForTestGenerator(maxTime = SimpleDuration(1,1,0))
            assertTrue(jc.jobsEqual(verifiedJob, verifiedJob))
            assertFalse(jc.jobsEqual(verifiedJob, jobNodes))
            assertFalse(jc.jobsEqual(verifiedJob, jobTasksPerNode))
            assertFalse(jc.jobsEqual(verifiedJob, jobTime))
        }

        //Test compare with different mount modes.
        run {
            val jobCopy = verifiedJobForTestGenerator(mountMode = MountMode.COPY_FILES)
            val jobCOW = verifiedJobForTestGenerator(mountMode = MountMode.COPY_ON_WRITE)
            assertFalse(jc.jobsEqual(verifiedJob, jobCOW))
            assertFalse(jc.jobsEqual(verifiedJob, jobCopy))
            assertFalse(jc.jobsEqual(jobCOW, jobCopy))
        }

        //Test compare with different reservations.
        run {
            val jobReservation = verifiedJobForTestGenerator(reservation = MachineReservation("XL", 100, 2000))
            val jobReservation2 = verifiedJobForTestGenerator(reservation = MachineReservation("L", 100, 2000))
            val jobReservation3 = verifiedJobForTestGenerator(reservation = MachineReservation("XL", 10, 2000))
            val jobReservation4 = verifiedJobForTestGenerator(reservation = MachineReservation("XL", 100, 10))
            assertTrue(jc.jobsEqual(jobReservation, jobReservation))
            assertFalse(jc.jobsEqual(verifiedJob, jobReservation))
            assertFalse(jc.jobsEqual(jobReservation, jobReservation2))
            assertFalse(jc.jobsEqual(jobReservation, jobReservation3))
            assertFalse(jc.jobsEqual(jobReservation, jobReservation4))
        }

        //Test compare with different workspace and project.
        run {
            val jobProject = verifiedJobForTestGenerator(project = "thisProject")
            val jobWorkspace = verifiedJobForTestGenerator(workspace = "thisWorkspace")
            assertTrue(jc.jobsEqual(jobProject, jobProject))
            assertFalse(jc.jobsEqual(jobProject, jobWorkspace))
            assertTrue(jc.jobsEqual(jobWorkspace, jobWorkspace))
            assertFalse(jc.jobsEqual(verifiedJob, jobProject))
            assertFalse(jc.jobsEqual(verifiedJob, jobWorkspace))
        }

        //Test compare with different upload files.
        run {
            val newJobSingleFile = verifiedJobForTestGenerator(files = listOf(validatedFileForUpload))
            val newJobSingleFile2 = verifiedJobForTestGenerator(files = listOf(validatedFileForUpload.copy(id = "id")))
            val newJobDoubleFile = verifiedJobForTestGenerator(files = listOf(
                validatedFileForUpload,
                validatedFileForUpload.copy(id = "fileID2")
            ))

            assertFalse(jc.jobsEqual(verifiedJob, newJobSingleFile))
            assertTrue(jc.jobsEqual(newJobSingleFile, newJobSingleFile))
            assertFalse(jc.jobsEqual(newJobSingleFile, newJobSingleFile2))
            assertFalse(jc.jobsEqual(newJobDoubleFile, newJobSingleFile))

        }

        //Test compare with different mounts
        //Test compare with different peers
        //Test compare with different system mounts
        //Test compare with different job input
    }

}
