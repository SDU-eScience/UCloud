package dk.sdu.cloud.storage.services.cephfs

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.service.stackTraceToString
import org.slf4j.LoggerFactory
import java.io.File

class CloudToCephFsDao(val isDevelopment: Boolean) {
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
        addUser("pica@sdu.dk", "c_pica_cp3-origins.net")
        addUser("root", "root")

        if (isDevelopment) {
            try {
                data class Entry(val cloudUser: String, val unixUser: String)

                val mapper = jacksonObjectMapper()
                val src = File("dev_entries.json")
                if (src.exists()) {
                    mapper.readValue<List<Entry>>(src).forEach {
                        log.info("Adding dev entry: $it")
                        addUser(it.cloudUser, it.unixUser)
                    }
                }
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
            }
        }
    }

    fun findCloudUser(unixUser: String): String? {
        log.debug("Looking for $unixUser in $userToCloud")
        return userToCloud[unixUser]
    }

    fun findUnixUser(cloudUser: String): String? = cloudToUser[cloudUser]

    companion object {
        private val log = LoggerFactory.getLogger(CloudToCephFsDao::class.java)
    }
}