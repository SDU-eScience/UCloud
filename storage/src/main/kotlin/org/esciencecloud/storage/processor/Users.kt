package org.esciencecloud.storage.processor

import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.model.Request
import org.esciencecloud.storage.model.RequestHeader
import org.esciencecloud.storage.model.UserEvent
import org.esciencecloud.storage.model.UserProcessor

class Users(private val storageService: StorageService) {
    fun initStream(builder: KStreamBuilder) {
        UserProcessor.UserEvents.process(builder) { _, request ->
            val connection = storageService.validateRequest(request.header).capture() ?:
                    return@process Result.lastError<Unit>()

            @Suppress("UNCHECKED_CAST")
            when (request.event) {
                is UserEvent.Create -> createUser(connection, request as Request<UserEvent.Create>)
                is UserEvent.Modify -> modifyUser(connection, request as Request<UserEvent.Modify>)
                is UserEvent.Delete -> deleteUser(connection, request as Request<UserEvent.Delete>)
            }
        }
    }

    private fun createUser(connection: StorageConnection, request: Request<UserEvent.Create>): Result<Unit> {
        val userAdmin = connection.userAdmin ?: return Error.permissionDenied()
        return userAdmin.createUser(request.event.username, request.event.password)
    }

    private fun modifyUser(connection: StorageConnection, request: Request<UserEvent.Modify>): Result<Unit> {
        return when {
            connection.userAdmin != null -> {
                val userAdmin = connection.userAdmin!!
                // We can do whatever
                // TODO Rolling back if any of these fail is not trivial
                if (request.event.newPassword != null) {
                    userAdmin.modifyPassword(request.event.currentUsername, request.event.newPassword).capture() ?:
                            return Result.lastError()
                }

                if (request.event.newUserType != null) {
                    TODO("Not provided by the API")
                }

                Ok.empty()
            }

            request.event.currentUsername == connection.connectedUser.name -> {
                // Otherwise we need to modifying ourselves
                // And even then, we only allow certain things

                if (request.event.newUserType != null) return Error.permissionDenied()
                if (request.event.newPassword != null) {
                    val currentPassword = request.header.performedFor.password // TODO This shouldn't be available directly
                    connection.users.modifyMyPassword(currentPassword, request.event.newPassword)
                }
                Ok.empty()
            }

            else -> Error.permissionDenied()
        }
    }

    private fun deleteUser(connection: StorageConnection, request: Request<UserEvent.Delete>): Result<Unit> = TODO()
}