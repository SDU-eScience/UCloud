package org.esciencecloud.storage.ext

import org.esciencecloud.storage.model.User

interface StorageConnection {
    val connectedUser: User

    // Services
    val paths: PathOperations
    val files: FileOperations
    val metadata: MetadataOperations
    val accessControl: AccessControlOperations
    val fileQuery: FileQueryOperations
    val users: UserOperations
    val groups: GroupOperations

    // Optional services, these might not be available on all connections.
    // For example, administrative operations should not be provided for every open connection, as
    // some users might not be authenticated for them.
    val userAdmin: UserAdminOperations?

    // Methods
    fun close()
}

interface StorageConnectionFactory {
    /**
     * Opens a new connection for a user
     *
     * The [username] and [password] will be used for the authentication. Note that [password] used might be,
     * depending on implementation, not the actual password of the user, but rather an access token. This is left
     * as an implementation detail of the connection.
     *
     * @throws PermissionException If the authentication fails
     */
    fun createForAccount(username: String, password: String): StorageConnection
}
