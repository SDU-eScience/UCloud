package org.esciencecloud.storage.ext

interface UserOperations {
    fun modifyMyPassword(currentPassword: String, newPassword: String)
}