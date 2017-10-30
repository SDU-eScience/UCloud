package org.esciencecloud.storage.server

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import java.util.*

class StorageStreamProcessor(private val storageService: StorageService) {
    fun retrieveKafkaConfiguration(): Properties {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092" // Comma-separated. Should point to at least 3
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        return properties
    }

    fun constructStreams(builder: KStreamBuilder) {
        UserGroupsProcessor.CreateUser.mapResult(builder) { createUser(it) }
        UserGroupsProcessor.ModifyUser.mapResult(builder) { modifyUser(it) }

        // TODO FIXME THIS SHOULD BE REMOVED LATER
        // TODO FIXME THIS SHOULD BE REMOVED LATER
        UserGroupsProcessor.Bomb.mapResult(builder) {
            // The idea is that we use this to test handling of jobs that are causing consistent crashes with the
            // system. We should be able to handle these without killing the entire system.
            throw RuntimeException("Boom!")
        }
        // TODO FIXME THIS SHOULD BE REMOVED LATER
        // TODO FIXME THIS SHOULD BE REMOVED LATER

        // TODO How will we have other internal systems do work here? They won't have a performed by when the task
        // is entirely internal to the system. This will probably just be some simple API token such that we can confirm
        // who is performing the request.
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
                if (request.newPassword != null) {
                    userAdmin.modifyPassword(request.currentUsername, request.newPassword)
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
