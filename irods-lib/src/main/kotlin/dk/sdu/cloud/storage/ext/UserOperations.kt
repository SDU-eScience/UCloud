package dk.sdu.cloud.storage.ext

interface UserOperations {
    fun modifyMyPassword(currentPassword: String, newPassword: String)
}