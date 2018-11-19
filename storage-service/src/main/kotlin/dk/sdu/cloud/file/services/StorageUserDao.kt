package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.SERVICE_UNIX_USER
import dk.sdu.cloud.file.SERVICE_USER

interface StorageUserDao {
    /**
     * Finds a cloud user based on a [unixUser]
     *
     * Note: This method is required to resolve [SERVICE_UNIX_USER] to [SERVICE_USER]
     */
    fun findCloudUser(unixUser: String, verify: Boolean = false): String?

    /**
     * Finds a storage user based on a [cloudUser]
     *
     * Note: This method is required to resolve [SERVICE_USER] to [SERVICE_UNIX_USER]
     */
    fun findStorageUser(cloudUser: String, verify: Boolean = false): String?
}
