package dk.sdu.escience.storage

interface Connection {
    val connectedUser: User

    // Services
    val files: FileOperations
    val metadata: MetadataOperations
    val accessControl: AccessControlOperations
    val fileQuery: FileQueryOperations
    val users: UserOperations
    val groups: GroupOperations

    // Methods
    fun close()
}

interface ConnectionFactory {
    /**
     * Opens a new connection for a user
     *
     * The [username] and [password] will be used for the authentication. Note that [password] used might be,
     * depending on implementation, not the actual password of the user, but rather an access token. This is left
     * as an implementation detail of the connection.
     *
     * @throws PermissionException If the authentication fails
     */
    fun createForAccount(username: String, password: String): Connection
}