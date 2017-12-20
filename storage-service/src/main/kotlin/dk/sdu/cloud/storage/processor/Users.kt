package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.service.KafkaRequest
import org.apache.kafka.streams.StreamsBuilder
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.model.UserEvent
import dk.sdu.cloud.storage.model.UserProcessor

class Users(private val storageService: StorageService) {
    fun initStream(builder: StreamsBuilder) {
        UserProcessor.UserEvents.process(builder) { _, request ->
            val connection = storageService.validateRequest(request.header).capture() ?:
                    return@process Result.lastError<Unit>()

            connection.use {
                @Suppress("UNCHECKED_CAST")
                when (request.event) {
                    is UserEvent.Create -> createUser(connection, request as KafkaRequest<UserEvent.Create>)
                    is UserEvent.Modify -> modifyUser(connection, request as KafkaRequest<UserEvent.Modify>)
                    is UserEvent.Delete -> deleteUser(connection, request as KafkaRequest<UserEvent.Delete>)
                }
            }
        }
    }

    private fun createUser(connection: StorageConnection, request: KafkaRequest<UserEvent.Create>): Result<Unit> {
        val userAdmin = connection.userAdmin ?: return Error.permissionDenied()
        return userAdmin.createUser(request.event.username, request.event.password)
    }

    private fun modifyUser(connection: StorageConnection, request: KafkaRequest<UserEvent.Modify>): Result<Unit> {
        return when {
            connection.userAdmin != null -> {
                val userAdmin = connection.userAdmin!!
                // We can do whatever
                // TODO Rolling back if any of these fail is not trivial
                val newPassword = request.event.newPassword
                if (newPassword != null) {
                    userAdmin.modifyPassword(request.event.currentUsername, newPassword).capture() ?:
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
                val newPassword = request.event.newPassword
                if (newPassword != null) {
                    val currentPassword = request.header.performedFor
                    connection.users.modifyMyPassword(currentPassword, newPassword)
                }
                Ok.empty()
            }

            else -> Error.permissionDenied()
        }
    }

    private fun deleteUser(connection: StorageConnection, request: KafkaRequest<UserEvent.Delete>): Result<Unit> =
            TODO()
}