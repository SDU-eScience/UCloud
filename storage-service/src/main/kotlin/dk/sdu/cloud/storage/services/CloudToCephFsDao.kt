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
        addUser("user3@test.dk", "c_user3_test_dk")
        addUser("user4@test.dk", "c_user4_test_dk")
        addUser("dthrane", "dthrane")
        addUser("root", "root")
    }

    fun findCloudUser(unixUser: String): String? = userToCloud[unixUser]

    fun findUnixUser(cloudUser: String): String? = cloudToUser[cloudUser]
}