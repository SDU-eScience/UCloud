package org.esciencecloud.storage

interface GroupOperations {
    fun createGroup(name: String): Result<Unit>
    fun deleteGroup(name: String, force: Boolean = false): Result<Unit>
    fun addUserToGroup(groupName: String, username: String): Result<Unit>
    fun removeUserFromGroup(groupName: String, username: String): Result<Unit>
    fun listGroupMembers(groupName: String): Result<List<User>>
}