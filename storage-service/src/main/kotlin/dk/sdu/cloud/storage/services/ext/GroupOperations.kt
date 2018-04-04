package dk.sdu.cloud.storage.services.ext

import dk.sdu.cloud.storage.api.User

interface GroupOperations {
    fun createGroup(name: String)
    fun deleteGroup(name: String, force: Boolean = false)
    fun addUserToGroup(groupName: String, username: String)
    fun removeUserFromGroup(groupName: String, username: String)
    fun listGroupMembers(groupName: String): List<User>
}