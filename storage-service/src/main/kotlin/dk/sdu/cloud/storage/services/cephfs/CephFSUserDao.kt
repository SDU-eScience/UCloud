package dk.sdu.cloud.storage.services.cephfs

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.service.stackTraceToString
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class CephFSUserDao(private val isDevelopment: Boolean) {
    private val cloudToUser = HashMap<String, String>()
    private val userToCloud = HashMap<String, String>()

    private fun addUser(cloudUser: String, unixUser: String) {
        assert(isDevelopment)
        cloudToUser[cloudUser] = unixUser
        userToCloud[unixUser] = cloudUser
    }

    init {
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

    fun findCloudUser(unixUser: String, verify: Boolean = false): String? {
        if (verify) TODO("Username verification not yet implemented")

        if (unixUser.startsWith(B64_PREFIX)) {
            val encodedName = unixUser.substring(B64_PREFIX.length).replace('.', '=')
            return String(decoder.decode(encodedName), USERNAME_CHARSET)
        } else {
            if (isDevelopment) return userToCloud[unixUser]
            throw IllegalArgumentException("Unsupported unix user")
        }
    }

    fun findUnixUser(cloudUser: String, verify: Boolean = false): String? {
        if (verify) TODO("Username verification not yet implemented")

        return if (isDevelopment) {
            cloudToUser[cloudUser]
        } else {
            B64_PREFIX + encoder.encodeToString(cloudUser.toByteArray(USERNAME_CHARSET)).replace('=', '.')
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CephFSUserDao::class.java)

        // We use a non-standard file-name and URL safe base64 encoding with '.' as the padding
        // character as opposed to '='. This makes the encoding Unix username safe
        private const val B64_PREFIX = "b64"
        private val USERNAME_CHARSET = Charsets.UTF_8

        private val encoder = Base64.getUrlEncoder()
        private val decoder = Base64.getUrlDecoder()
    }
}