package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import com.sun.jna.Platform
import dk.sdu.cloud.service.Loggable
import org.slf4j.Logger

data class Entry(
    val isUser: Boolean,
    val id: String,
    val read: Boolean,
    val write: Boolean,
    val execute: Boolean
)

object ACL : Loggable {
    override val log: Logger = logger()
    private const val UNDEFINED_TAG = 0x00
    private const val USER_OBJ = 0x01
    private const val USER = 0x02
    private const val GROUP_OBJ = 0x04
    private const val GROUP = 0x08
    private const val MASK = 0x10
    private const val OTHER = 0x20
    private const val READ = 0x04
    private const val WRITE = 0x02
    private const val EXECUTE = 0x01

    fun getEntries(path: String): Sequence<Entry> = sequence {
        with(ACLLibrary.INSTANCE) {
            val type = if (Platform.isMac()) 0x100 else 0

            val entry = LongArray(1)
            val tag = ACLTag()
            val permset = ACLPermSet()

            log.debug("Looking up ACL")
            val acl = acl_get_file(path, type) ?: return@sequence

            var idx = 0
            while (idx++ >= 0) {
                log.debug("Requesting entry: $idx $entry $acl")
                if (acl_get_entry(acl, if (idx == 1) 0 else -1, entry) != 0) break

                log.debug("Requesting tag $entry ${tag.toList()}")
                val entryValue = entry.single()
                if (acl_get_tag_type(entryValue, tag) == -1) {
                    log.debug("errno=${Native.getLastError()}")
                    log.debug("Requesting tag $entry ${tag.toList()}")
                    throw ACLException("acl_get_tag_type unsuccessful")
                }

                val tagValue = tag.single()
                log.debug("Tag: $tagValue")
                if (tagValue in setOf(USER, USER_OBJ, GROUP, GROUP_OBJ)) {
                    log.debug("Requesting qualifier")
                    val qualifier = acl_get_qualifier(entryValue) ?: continue

                    if (Platform.isLinux()) {
                        val uid = qualifier.getInt(0)
                        log.debug("Qualifier: $uid")
                        acl_free(qualifier)

                        val isUser = tagValue == USER || tagValue == USER_OBJ

                        log.debug("Requesting permset")
                        if (acl_get_permset(
                                entryValue,
                                permset
                            ) == -1
                        ) throw ACLException("acl_get_permset unsuccessful")

                        log.debug("Reading permset")
                        val hasRead = acl_get_perm(permset, READ) == 1
                        val hasWrite = acl_get_perm(permset, WRITE) == 1
                        val hasExecute = acl_get_perm(permset, EXECUTE) == 1

                        log.debug("Ready to yield")
                        yield(
                            Entry(
                                isUser,
                                uid.toString(),
                                hasRead,
                                hasWrite,
                                hasExecute
                            )
                        )
                    } else if (Platform.isMac()) {

                    }
                }
            }
        }
    }
}

class ACLException(why: String) : RuntimeException(why)

