package dk.sdu.escience.storage

interface GroupOperations {
    fun createGroup(name: String)
    fun deleteGroup(name: String, force: Boolean = false)
    fun addUserToGroup(groupName: String, username: String)
    fun removeUserFromGroup(groupName: String, username: String)
    fun listGroupMembers(groupName: String): List<User>
}