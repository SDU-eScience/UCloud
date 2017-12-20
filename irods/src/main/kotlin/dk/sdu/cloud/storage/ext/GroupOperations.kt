package dk.sdu.cloud.storage.ext

import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.model.User

interface GroupOperations {
    fun createGroup(name: String): Result<Unit>
    fun deleteGroup(name: String, force: Boolean = false): Result<Unit>
    fun addUserToGroup(groupName: String, username: String): Result<Unit>
    fun removeUserFromGroup(groupName: String, username: String): Result<Unit>
    fun listGroupMembers(groupName: String): Result<List<User>>
}