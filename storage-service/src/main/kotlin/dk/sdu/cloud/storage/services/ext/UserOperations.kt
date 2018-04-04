package dk.sdu.cloud.storage.services.ext

interface UserOperations {
    fun modifyMyPassword(currentPassword: String, newPassword: String)
}