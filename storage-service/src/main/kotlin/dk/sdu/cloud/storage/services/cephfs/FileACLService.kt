package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.services.FSException
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.ShareException
import org.slf4j.LoggerFactory
import java.util.*

class FileACLService(private val cephFSUserDao: CephFSUserDao) {
    // NOTE(Dan): The setfacl command is implemented by passing it directly to a shell. Care should be taken in
    // here to ensure we don't pass unsafe arguments on CLI

    private fun internalCreateEntry(
        ctx: FSUserContext,
        faclEntity: String,
        mountedPath: String,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ) {
        val command = ArrayList<String>().apply {
            if (defaultList) add("-d")
            if (recursive) add("-R")

            add("-m")
            val permissions: String = run {
                val read = if (AccessRight.READ in rights) "r" else "-"
                val write = if (AccessRight.WRITE in rights) "w" else "-"
                val execute = if (AccessRight.EXECUTE in rights) "x" else "X" // Note: execute is implicit for dirs

                read + write + execute
            }

            add("$faclEntity:$permissions")

            add(mountedPath)
        }.toList()

        ctx.runCommand(
            InterpreterCommand.SETFACL,
            *command.toTypedArray(),
            consumer = {
                it.stdoutLineSequence().any { internalStatusCheck(it) }
            }
        )
    }

    fun createEntry(
        ctx: FSUserContext,
        toUser: String,
        mountedPath: String,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ) {
        val toUserUnix =
            cephFSUserDao.findUnixUser(toUser) ?: throw ShareException.BadRequest(
                "$toUser does not exist"
            )

        internalCreateEntry(ctx, "u:$toUserUnix", mountedPath, rights, defaultList, recursive)
    }

    fun createEntryForOthers(
        ctx: FSUserContext,
        mountedPath: String,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ) {
        internalCreateEntry(ctx, "o", mountedPath, rights, defaultList, recursive)
    }

    fun removeEntry(
        ctx: FSUserContext,
        toUser: String,
        mountedPath: String,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ) {
        val toUserUnix =
            cephFSUserDao.findUnixUser(toUser) ?: throw ShareException.BadRequest(
                "$toUser does not exist"
            )

        val command = ArrayList<String>().apply {
            if (defaultList) add("-d")
            if (recursive) add("-R")
            add("-x")
            add("u:$toUserUnix")
            add(mountedPath)
        }.toList()

        ctx.runCommand(
            InterpreterCommand.SETFACL,
            *command.toTypedArray(),
            consumer = {
                it.stdoutLineSequence().any { internalStatusCheck(it) }
            }
        )
    }

    private fun internalStatusCheck(line: String): Boolean {
        log.debug(line)

        if (line.startsWith("EXIT:")) {
            val status = line.split(":")[1].toInt()
            if (status != 0) throw FSException.PermissionException()
            return true
        }
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileACLService::class.java)
    }
}