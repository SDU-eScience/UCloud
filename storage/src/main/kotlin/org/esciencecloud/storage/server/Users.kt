package org.esciencecloud.storage.server

import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result

class Users(private val storageService: StorageService) {
    fun init(builder: KStreamBuilder) {
        UserProcessor.UserEvents.process(builder) { _, request ->
            val header = request.header
            val event = request.event

            when (event) {
                is UserEvent.Create -> createUser(header, event)
                is UserEvent.Modify -> modifyUser(header, event)
                is UserEvent.Delete -> deleteUser(header, event)
            }
        }
    }

    private fun createUser(header: RequestHeader, request: UserEvent.Create): Result<Unit> {
        val connection = storageService.validateRequest(header).capture() ?: return Result.lastError()
        val userAdmin = connection.userAdmin ?: return Error(123, "Not allowed")
        return userAdmin.createUser(request.username, request.password)
    }

    private fun modifyUser(header: RequestHeader, event: UserEvent.Modify): Result<Unit> {
        val connection = storageService.validateRequest(header).capture() ?: return Result.lastError()
        return when {
            connection.userAdmin != null -> {
                val userAdmin = connection.userAdmin!!
                // We can do whatever
                // TODO Rolling back if any of these fail is not trivial
                if (event.newPassword != null) {
                    userAdmin.modifyPassword(event.currentUsername, event.newPassword).capture() ?:
                            return Result.lastError()
                }

                if (event.newUserType != null) {
                    TODO("Not provided by the API")
                }

                Ok.empty()
            }

            event.currentUsername == connection.connectedUser.name -> {
                // Otherwise we need to modifying ourselves
                // And even then, we only allow certain things

                if (event.newUserType != null) return Error.permissionDenied()
                if (event.newPassword != null) {
                    val currentPassword = header.performedFor.password // TODO This shouldn't be available directly
                    connection.users.modifyMyPassword(currentPassword, event.newPassword)
                }
                Ok.empty()
            }

            else -> Error.permissionDenied()
        }
    }

    private fun deleteUser(header: RequestHeader, event: UserEvent.Delete): Result<Unit> = TODO()
}