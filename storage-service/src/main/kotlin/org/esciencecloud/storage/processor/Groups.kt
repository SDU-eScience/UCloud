package org.esciencecloud.storage.processor

import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.model.GroupEvent
import org.esciencecloud.storage.model.GroupsProcessor
import org.esciencecloud.storage.model.Request

class Groups(private val storageService: StorageService) {
    fun initStream(builder: KStreamBuilder) {
        GroupsProcessor.Groups.process(builder) { _, request ->
            val connection = storageService.validateRequest(request.header).capture() ?:
                    return@process Result.lastError<Unit>()

            connection.use {
                @Suppress("UNCHECKED_CAST")
                when (request.event) {
                    is GroupEvent.Create -> createGroup(connection, request as Request<GroupEvent.Create>)
                    is GroupEvent.AddMember -> addMember(connection, request as Request<GroupEvent.AddMember>)
                    is GroupEvent.RemoveMember -> removeMember(connection, request as Request<GroupEvent.RemoveMember>)
                    is GroupEvent.Delete -> deleteGroup(connection, request as Request<GroupEvent.Delete>)
                }
            }
        }
    }

    private fun createGroup(connection: StorageConnection, request: Request<GroupEvent.Create>): Result<Unit> =
            connection.groups.createGroup(request.event.groupName)

    private fun addMember(connection: StorageConnection, request: Request<GroupEvent.AddMember>): Result<Unit> =
            connection.groups.addUserToGroup(request.event.groupName, request.event.username)

    private fun removeMember(connection: StorageConnection, request: Request<GroupEvent.RemoveMember>): Result<Unit> =
            connection.groups.removeUserFromGroup(request.event.groupName, request.event.username)

    private fun deleteGroup(connection: StorageConnection, request: Request<GroupEvent.Delete>): Result<Unit> =
            connection.groups.deleteGroup(request.event.groupName, request.event.force)
}