package dk.sdu.escience.storage

interface UserOperations {
    fun modifyMyPassword(currentPassword: String, newPassword: String)
}