package dk.sdu.cloud.storage.services

interface StorageUserDao {
    fun findCloudUser(unixUser: String, verify: Boolean = false): String?
    fun findStorageUser(cloudUser: String, verify: Boolean = false): String?
}