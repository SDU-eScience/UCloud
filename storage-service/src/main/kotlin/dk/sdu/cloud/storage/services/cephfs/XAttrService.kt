package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.FileSystemException
import org.slf4j.LoggerFactory

class XAttrService(
    private val isDevelopment: Boolean
) {
    fun getAttributeList(ctx: FSUserContext, mountedPath: String): Map<String, String> {
        val command = listOf(getfattrExecutable, "-d", mountedPath)
        val (status, stdout, stderr) = ctx.runWithResultAsInMemoryString(command)

        if (status != 0) {
            if (stderr.contains("Permission denied")) {
                throw FileSystemException.PermissionException()
            }
            log.warn("getfattr failed! $status, $stdout, $stderr")
            throw FileSystemException.CriticalException("getfattr failed")
        }

        return stdout
            .lines()
            .filter { !it.startsWith("#") && !it.isBlank() && it.contains('=') }
            .map {
                val keyName = it.substringBefore('=').removePrefix("user.")
                val value = it.substringAfter('=').removePrefix("\"").removeSuffix("\"")

                keyName to value
            }
            .toMap()
    }

    fun setAttribute(ctx: FSUserContext, mountedPath: String, key: String, value: String) {
        if (!safeKeyRegex.matches(key)) throw IllegalArgumentException("invalid key")
        if (!safeValueRegex.matches(key)) throw IllegalArgumentException("invalid value")

        val command = listOf(setfattrExecutable, "-n", "user.$key", "-v", value, mountedPath)
        val (status, stdout, stderr) = ctx.runWithResultAsInMemoryString(command)
        if (status != 0) {
            if (stderr.contains("Permission denied")) {
                throw FileSystemException.PermissionException()
            }
            log.warn("setfattr failed! $status, $stdout, $stderr")
            throw FileSystemException.CriticalException("setfattr failed")
        }
    }

    private val getfattrExecutable: String = if (isDevelopment) "echo" else "getfattr"
    private val setfattrExecutable: String = if (isDevelopment) "echo" else "setfattr"

    companion object {
        private val log = LoggerFactory.getLogger(XAttrService::class.java)
        private val safeKeyRegex = Regex("([A-Za-z0-9 _])+")
        private val safeValueRegex = Regex("([A-Za-z0-9 _])+")
    }
}