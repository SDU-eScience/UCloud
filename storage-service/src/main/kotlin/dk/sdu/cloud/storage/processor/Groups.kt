package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.service.KafkaRequest
import org.apache.kafka.streams.StreamsBuilder
import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.model.GroupEvent
import dk.sdu.cloud.storage.model.GroupsProcessor

class Groups(private val storageService: StorageService) {
    fun initStream(builder: StreamsBuilder) {
        GroupsProcessor.Groups.process(builder) { _, request ->
            val connection = storageService.validateRequest(request.header).capture() ?:
                    return@process Result.lastError<Unit>()

            connection.use {
                @Suppress("UNCHECKED_CAST")
                when (request.event) {
                    is GroupEvent.Create -> createGroup(connection, request as KafkaRequest<GroupEvent.Create>)
                    is GroupEvent.AddMember -> addMember(connection, request as KafkaRequest<GroupEvent.AddMember>)
                    is GroupEvent.RemoveMember -> removeMember(connection, request as KafkaRequest<GroupEvent.RemoveMember>)
                    is GroupEvent.Delete -> deleteGroup(connection, request as KafkaRequest<GroupEvent.Delete>)
                }
            }
        }
    }

    private fun createGroup(connection: StorageConnection, request: KafkaRequest<GroupEvent.Create>): Result<Unit> =
            connection.groups.createGroup(request.event.groupName)

    private fun addMember(connection: StorageConnection, request: KafkaRequest<GroupEvent.AddMember>): Result<Unit> =
            connection.groups.addUserToGroup(request.event.groupName, request.event.username)

    private fun removeMember(connection: StorageConnection, request: KafkaRequest<GroupEvent.RemoveMember>): Result<Unit> =
            connection.groups.removeUserFromGroup(request.event.groupName, request.event.username)

    private fun deleteGroup(connection: StorageConnection, request: KafkaRequest<GroupEvent.Delete>): Result<Unit> =
            connection.groups.deleteGroup(request.event.groupName, request.event.force)
}