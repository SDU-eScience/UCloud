package org.esciencecloud.storage.server

import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result

class UserGroupsStreamProcessor(private val storageService: StorageService) {
    fun init(builder: KStreamBuilder) {
        UserGroupsProcessor.CreateUser.mapResult(builder) { createUser(it) }
        UserGroupsProcessor.ModifyUser.mapResult(builder) { modifyUser(it) }
    }

    private fun createUser(request: CreateUserRequest): Result<Unit> {
        val connection = storageService.validateRequest(request).capture() ?: return Result.lastError()
        val userAdmin = connection.userAdmin ?: return Error(123, "Not allowed")
        return userAdmin.createUser(request.username, request.password)
    }

    private fun modifyUser(request: ModifyUserRequest): Result<Unit> {
        val connection = storageService.validateRequest(request).capture() ?: return Result.lastError()
        return when {
            connection.userAdmin != null -> {
                val userAdmin = connection.userAdmin!!
                // We can do whatever
                // TODO Rolling back if any of these fail is not trivial
                if (request.newPassword != null) {
                    userAdmin.modifyPassword(request.currentUsername, request.newPassword).capture() ?:
                            return Result.lastError()
                }

                if (request.newUserType != null) {
                    TODO("Not provided by the API")
                }

                Ok.empty()
            }

            request.currentUsername == connection.connectedUser.name -> {
                // Otherwise we need to modifying ourselves
                // And even then, we only allow certain things

                if (request.newUserType != null) return Error.permissionDenied()
                if (request.newPassword != null) {
                    val currentPassword = request.header.performedFor.password // TODO This shouldn't be available directly
                    connection.users.modifyMyPassword(currentPassword, request.newPassword)
                }
                Ok.empty()
            }

            else -> Error.permissionDenied()
        }
    }
}