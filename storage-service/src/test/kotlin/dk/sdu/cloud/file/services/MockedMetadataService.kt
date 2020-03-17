package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.services.acl.MetadataService
import io.mockk.*
import kotlinx.coroutines.runBlocking

val mockedMetadataService: MetadataService by lazy {
    val service = mockk<MetadataService>(relaxed = true)
    coEvery { service.findMetadata(any(), any(), any()) } returns null
    coEvery { service.listMetadata(any(), any(), any()) } returns emptyMap()
    coEvery { service.findByPrefix(any(), any(), any()) } returns emptyList()

    val slot = CapturingSlot<suspend () -> Any?>()
    coEvery { service.runDeleteAction<Any?>(any(), capture(slot)) } answers {
        runBlocking { slot.captured!!() }
    }

    coEvery { service.runMoveAction(any(), any(), capture(slot))} answers {
        runBlocking { slot.captured!!() }
    }

    service
}
