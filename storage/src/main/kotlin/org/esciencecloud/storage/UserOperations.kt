package org.esciencecloud.storage

interface UserOperations {
    fun modifyMyPassword(currentPassword: String, newPassword: String)
}