package dk.sdu.cloud.file.services

interface StorageUserDao<UID> {
    /**
     * Finds a cloud user based on a [uid]
     */
    suspend fun findCloudUser(uid: UID, verify: Boolean = false): String?

    /**
     * Finds a storage user based on a [cloudUser]
     */
    suspend fun findStorageUser(cloudUser: String, verify: Boolean = false): UID?
}
