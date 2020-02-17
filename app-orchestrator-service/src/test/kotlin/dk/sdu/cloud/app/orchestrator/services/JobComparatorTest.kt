package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.fs.api.SharedFileSystem
import dk.sdu.cloud.app.orchestrator.api.ApplicationPeer
import dk.sdu.cloud.app.orchestrator.api.MachineReservation
import dk.sdu.cloud.app.orchestrator.api.MountMode
import dk.sdu.cloud.app.orchestrator.api.SharedFileSystemMount
import dk.sdu.cloud.app.orchestrator.api.VerifiedJobInput
import dk.sdu.cloud.app.orchestrator.utils.validatedFileForUpload
import dk.sdu.cloud.app.orchestrator.utils.verifiedJob
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobForTestGenerator
import dk.sdu.cloud.app.store.api.BooleanApplicationParameter
import dk.sdu.cloud.app.store.api.SharedFileSystemType
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.StringApplicationParameter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JobComparatorTest {

    @Test
    fun `compare same job`() {
        assertEquals(verifiedJob, verifiedJob)
    }

    @Test
    fun `compare different jobs`() {

        //Test compare with different maxTime, nodes, tasksPerNode.
        run {
            val jobNodes = verifiedJobForTestGenerator(nodes = 2)
            val jobTasksPerNode = verifiedJobForTestGenerator(tasksPerNode = 2)
            val jobTime = verifiedJobForTestGenerator(maxTime = SimpleDuration(1,1,0))
            assertEquals(verifiedJob, verifiedJob)
            assertNotEquals(verifiedJob, jobNodes)
            assertNotEquals(verifiedJob, jobTasksPerNode)
            assertNotEquals(verifiedJob, jobTime)
        }

        //Test compare with different mount modes.
        run {
            val jobCopy = verifiedJobForTestGenerator(mountMode = MountMode.COPY_FILES)
            val jobCOW = verifiedJobForTestGenerator(mountMode = MountMode.COPY_ON_WRITE)
            assertNotEquals(verifiedJob, jobCOW)
            assertNotEquals(verifiedJob, jobCopy)
            assertNotEquals(jobCOW, jobCopy)
        }

        //Test compare with different reservations.
        run {
            val jobReservation = verifiedJobForTestGenerator(reservation = MachineReservation("XL", 100, 2000))
            val jobReservation2 = verifiedJobForTestGenerator(reservation = MachineReservation("L", 100, 2000))
            val jobReservation3 = verifiedJobForTestGenerator(reservation = MachineReservation("XL", 10, 2000))
            val jobReservation4 = verifiedJobForTestGenerator(reservation = MachineReservation("XL", 100, 10))
            assertEquals(jobReservation, jobReservation)
            assertNotEquals(verifiedJob, jobReservation)
            assertNotEquals(jobReservation, jobReservation2)
            assertNotEquals(jobReservation, jobReservation3)
            assertNotEquals(jobReservation, jobReservation4)
        }

        //Test compare with different workspace and project.
        run {
            val jobProject = verifiedJobForTestGenerator(project = "thisProject")
            assertEquals(jobProject, jobProject)
            assertNotEquals(verifiedJob, jobProject)
        }

        //Test compare with different upload files.
        run {
            val newJobSingleFile = verifiedJobForTestGenerator(files = listOf(validatedFileForUpload))
            val newJobSingleFile2 = verifiedJobForTestGenerator(files = listOf(validatedFileForUpload.copy(id = "id")))
            val newJobDoubleFile = verifiedJobForTestGenerator(files = listOf(
                validatedFileForUpload,
                validatedFileForUpload.copy(id = "fileID2")
            ))

            assertNotEquals(verifiedJob, newJobSingleFile)
            assertEquals(newJobSingleFile, newJobSingleFile)
            assertNotEquals(newJobSingleFile, newJobSingleFile2)
            assertNotEquals(newJobDoubleFile, newJobSingleFile)

        }

        //Test compare with different mounts
        run {
            val newJobMounts = verifiedJobForTestGenerator(mounts = listOf(validatedFileForUpload))
            val newJobMounts2 = verifiedJobForTestGenerator(mounts = listOf(validatedFileForUpload.copy("id")))
            val newJobDoubleMount = verifiedJobForTestGenerator(
                mounts = listOf(
                    validatedFileForUpload,
                    validatedFileForUpload.copy("id")
                )
            )

            assertEquals(newJobMounts, newJobMounts)
            assertNotEquals(newJobMounts, newJobMounts2)
            assertNotEquals(newJobMounts2, newJobDoubleMount)
            assertEquals(newJobDoubleMount, newJobDoubleMount)
        }
        //Test compare with different peers
        run {
            val newJobPeers = verifiedJobForTestGenerator(peers = listOf(ApplicationPeer("name", "jobid")))
            val newJobPeers2 = verifiedJobForTestGenerator(peers = listOf(ApplicationPeer("name2", "jobid")))
            val newJobDoublePeers = verifiedJobForTestGenerator(
                peers = listOf(
                    ApplicationPeer("name", "jobid"),
                    ApplicationPeer("name2", "jobid")
                )
            )

            assertEquals(newJobPeers, newJobPeers)
            assertNotEquals(newJobPeers, newJobPeers2)
            assertNotEquals(newJobPeers2, newJobDoublePeers)
            assertEquals(newJobDoublePeers, newJobDoublePeers)
        }

        //Test compare with different system mounts
        run {
            val newJobMounts = verifiedJobForTestGenerator(
                sharedFileSystemMounts = listOf(SharedFileSystemMount(
                    SharedFileSystem(
                        "systemId",
                        "owner",
                        "backend",
                        "title",
                        1234567
                    ),
                    "mounted/at/path",
                    SharedFileSystemType.EPHEMERAL,
                    false
                ))
            )
            val newJobMounts2 = verifiedJobForTestGenerator(
                sharedFileSystemMounts = listOf(SharedFileSystemMount(
                    SharedFileSystem(
                        "systemId2",
                        "owner",
                        "backend",
                        "title",
                        1234567
                    ),
                    "mounted/at/path",
                    SharedFileSystemType.EPHEMERAL,
                    false
                ))
            )
            val newJobDoubleMount = verifiedJobForTestGenerator(
                sharedFileSystemMounts = listOf(SharedFileSystemMount(
                    SharedFileSystem(
                        "systemId",
                        "owner",
                        "backend",
                        "title",
                        1234567
                    ),
                    "mounted/at/path",
                    SharedFileSystemType.EPHEMERAL,
                    false
                ),
                SharedFileSystemMount(
                    SharedFileSystem(
                        "systemId2",
                        "owner",
                        "backend",
                        "title",
                        1234567
                    ),
                    "mounted/at/path",
                    SharedFileSystemType.EPHEMERAL,
                    false
                ))
            )

            assertEquals(newJobMounts, newJobMounts)
            assertNotEquals(newJobMounts, newJobMounts2)
            assertNotEquals(newJobMounts2, newJobDoubleMount)
            assertEquals(newJobDoubleMount, newJobDoubleMount)
        }

        //Test compare with different job input
        run {
            val newJobInput = verifiedJobForTestGenerator(
                jobInput = VerifiedJobInput(
                    mapOf(
                        "Text" to StringApplicationParameter("hello")
                    )
                )
            )
            val newJobInput2 = verifiedJobForTestGenerator(
                jobInput = VerifiedJobInput(
                    mapOf(
                        "Text" to StringApplicationParameter("mojn")
                    )
                )
            )
            val newJobInput3 = verifiedJobForTestGenerator(
                jobInput = VerifiedJobInput(
                    mapOf(
                        "Text" to StringApplicationParameter("hello"),
                        "Boolean" to BooleanApplicationParameter(false)
                    )
                )
            )
            val newJobInput4 = verifiedJobForTestGenerator(
                jobInput = VerifiedJobInput(
                    mapOf(
                        "Boolean" to BooleanApplicationParameter(false),
                        "Text" to StringApplicationParameter("hello")
                    )
                )
            )
            val newJobInput5 = verifiedJobForTestGenerator(
                jobInput = VerifiedJobInput(
                    mapOf(
                        "Boolean" to BooleanApplicationParameter(false)
                    )
                )
            )

            assertEquals(newJobInput, newJobInput)
            assertEquals(newJobInput3, newJobInput3)
            assertNotEquals(newJobInput, newJobInput2)
            assertNotEquals(newJobInput2, newJobInput3)
            assertEquals(newJobInput3, newJobInput4)
            assertNotEquals(newJobInput, newJobInput5)

        }
    }

}
