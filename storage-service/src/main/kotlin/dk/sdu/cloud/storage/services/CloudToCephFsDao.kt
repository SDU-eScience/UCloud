package dk.sdu.cloud.storage.services

class CloudToCephFsDao {
    private val cloudToUser = HashMap<String, String>()
    private val userToCloud = HashMap<String, String>()
    private fun addUser(cloudUser: String, unixUser: String) {
        cloudToUser[cloudUser] = unixUser
        userToCloud[unixUser] = cloudUser
    }

    init {
        addUser("jonas@hinchely.dk", "c_jonas_hinchely_dk")
        addUser("dthrane", "dthrane")
        addUser("root", "root")
    }

    fun findCloudUser(unixUser: String): String? = userToCloud[unixUser]

    fun findUnixUser(cloudUser: String): String? = cloudToUser[cloudUser]
}