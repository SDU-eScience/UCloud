package dk.sdu.cloud.storage.ext

import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.model.User
import dk.sdu.cloud.storage.model.UserType

interface UserAdminOperations {
    /**
     * Creates a new user of a given [type] with [username] and [password]
     *
     * The password is optional. If no password is provided the user should be created with a disabled password.
     * It must not be possible to login to the newly created user if no [password] is provided.
     *
     * Permission error if the connected user is not capable of performing the operation (permission or
     * user already exists)
     */
    fun createUser(username: String, password: String? = null, type: UserType = UserType.USER): Result<Unit>

    /**
     * Deletes a user with [username]
     *
     * Not found error if the user could not be found
     * Permission error if the connected user is not capable of performing this operation
     */
    fun deleteUser(username: String): Result<Unit>

    /**
     * Modifies the password of a given user with [username] the [newPassword] will be used.
     *
     * It is implementation dependant if this should cause existing connections from [username] to be closed.
     *
     * Not found error if the user does not exist.
     *
     * Permission error if the connected user is not allowed to perform this operation.
     */
    fun modifyPassword(username: String, newPassword: String): Result<Unit>

    fun findByUsername(username: String): Result<User>
}