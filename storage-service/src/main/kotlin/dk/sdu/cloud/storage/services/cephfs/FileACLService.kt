package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.services.ShareException
import org.slf4j.LoggerFactory
import java.util.ArrayList

class FileACLService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val processRunner: ProcessRunnerFactory,
    private val isDevelopment: Boolean
) {
    private val setfaclExecutable: String
        get() = if (isDevelopment) "echo" else "setfacl"

    fun createEntry(
        fromUser: String,
        toUser: String,
        mountedPath: String,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ) {
        val toUserUnix =
            cloudToCephFsDao.findUnixUser(toUser) ?: throw ShareException.BadRequest(
                "$toUser does not exist"
            )

        val command = ArrayList<String>().apply {
            add(setfaclExecutable)

            if (defaultList) add("-d")
            if (recursive) add("-R")

            add("-m")
            val permissions: String = run {
                val read = if (AccessRight.READ in rights) "r" else "-"
                val write = if (AccessRight.WRITE in rights) "w" else "-"
                val execute = if (AccessRight.EXECUTE in rights) "x" else "X" // Note: execute is implicit for dirs

                read + write + execute
            }

            add("u:$toUserUnix:$permissions")

            add(mountedPath)
        }.toList()

        val result = processRunner(fromUser).runWithResultAsInMemoryString(command)
        if (result.status != 0) {
            log.info("createEntry failed with status ${result.status}!")
            log.info("stderr: ${result.stderr}")
            log.info("stdout: ${result.stdout}")
            throw ShareException.PermissionException()
        }
    }

    fun removeEntry(
        fromUser: String,
        toUser: String,
        mountedPath: String,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ) {
        val toUserUnix =
            cloudToCephFsDao.findUnixUser(toUser) ?: throw ShareException.BadRequest(
                "$toUser does not exist"
            )

        val command = ArrayList<String>().apply {
            add(setfaclExecutable)
            if (defaultList) add("-d")
            if (recursive) add("-R")
            add("-x")
            add("u:$toUserUnix")
            add(mountedPath)
        }.toList()

        val result = processRunner(fromUser).runWithResultAsInMemoryString(command)
        if (result.status != 0) {
            log.info("removeEntry failed with status ${result.status}!")
            log.info("stderr: ${result.stderr}")
            log.info("stdout: ${result.stdout}")
            throw ShareException.PermissionException()
        }
    }

    companion object {
        private val log =
            LoggerFactory.getLogger(FileACLService::class.java)
    }
}