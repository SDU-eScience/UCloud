package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.ptr.PointerByReference
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
    // USER_OBJ
    private const val USER_OWNER = 0x01
    // GROUP_OBJ
    private const val GROUP_OWNER = 0x04
    private const val OTHER = 0x20
    private const val USER = 0x02
    private const val GROUP = 0x08
    private const val MASK = 0x10
    private const val READ = 0x04
    private const val WRITE = 0x02
    private const val EXECUTE = 0x01

    fun getEntries(path: String): Sequence<Entry> = sequence {
        if (!Platform.isLinux()) return@sequence

        with(ACLLibrary.INSTANCE) {
            val type = 0x8000
            val entry = LongArray(1)
            val tag = ACLTag()
            val permset = ACLPermSet()

            val acl = acl_get_file(path, type) ?: return@sequence

            var idx = 0
            while (idx++ >= 0) {
                if (acl_get_entry(acl, if (idx == 1) 0 else 1, entry) != 1) {
                    break
                }

                val entryValue = entry.single()
                if (acl_get_tag_type(entryValue, tag) == -1) {
                    log.debug("errno=${Native.getLastError()}")
                    log.debug("Requesting tag $entry ${tag.toList()}")
                    throw ACLException("acl_get_tag_type unsuccessful")
                }

                val tagValue = tag.single()
                if (tagValue in setOf(USER, GROUP)) {
                    val qualifier = acl_get_qualifier(entryValue) ?: continue

                    if (Platform.isLinux()) {
                        val uid = qualifier.getInt(0)
                        acl_free(qualifier)

                        val isUser = tagValue == USER

                        if (acl_get_permset(
                                entryValue,
                                permset
                            ) == -1
                        ) throw ACLException("acl_get_permset unsuccessful")

                        val permsetValue = permset.single()
                        val hasRead = acl_get_perm(permsetValue, READ) == 1
                        val hasWrite = acl_get_perm(permsetValue, WRITE) == 1
                        val hasExecute = acl_get_perm(permsetValue, EXECUTE) == 1

                        yield(
                            Entry(
                                isUser,
                                uid.toString(),
                                hasRead,
                                hasWrite,
                                hasExecute
                            )
                        )
                    }
                }
            }

            log.debug("Found $idx entries in ACL")
            acl_free(acl)
        }
    }

    fun addEntry(path: String) {
        if (!Platform.isLinux()) return
        with(ACLLibrary.INSTANCE) {
            val type = 0x8000
            val entry = LongArray(1)
            val permset = ACLPermSet()

            // This code works as long as we are using acl_get_file and there an entry is already present
            val acl = acl_get_file(path, type) ?: TODO()
            val initialEntryCount = acl_entries(acl)

            /*
            run {
                acl_create_entry(PointerByReference(acl), entry).takeIf { it == 0 } ?: run {
                    log.debug("Last error: " + Native.getLastError())
                    TODO()
                }
                acl_set_tag_type(entry.single(), USER_OWNER).takeIf { it == 0 } ?: TODO()
                acl_get_permset(entry.single(), permset).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), READ).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), WRITE).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), EXECUTE).takeIf { it == 0 } ?: TODO()
            }

            run {
                acl_create_entry(PointerByReference(acl), entry).takeIf { it == 0 } ?: run {
                    log.debug("Last error: " + Native.getLastError())
                    TODO()
                }
                acl_set_tag_type(entry.single(), GROUP_OWNER).takeIf { it == 0 } ?: TODO()
                acl_get_permset(entry.single(), permset).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), READ).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), WRITE).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), EXECUTE).takeIf { it == 0 } ?: TODO()
            }

            run {
                acl_create_entry(PointerByReference(acl), entry).takeIf { it == 0 } ?: run {
                    log.debug("Last error: " + Native.getLastError())
                    TODO()
                }
                acl_set_tag_type(entry.single(), OTHER).takeIf { it == 0 } ?: TODO()
                acl_get_permset(entry.single(), permset).takeIf { it == 0 } ?: TODO()
                acl_clear_perms(permset.single()).takeIf { it == 0 } ?: TODO()
            }
            */

            // Create mask if we have not created it yet
            if (initialEntryCount == 3L) {
                run {
                    acl_create_entry(PointerByReference(acl), entry).takeIf { it == 0 } ?: run {
                        log.debug("Last error: " + Native.getLastError())
                        TODO()
                    }
                    acl_set_tag_type(entry.single(), MASK).takeIf { it == 0 } ?: TODO()
                    acl_get_permset(entry.single(), permset).takeIf { it == 0 } ?: TODO()
                    acl_add_perm(permset.single(), READ).takeIf { it == 0 } ?: TODO()
                    acl_add_perm(permset.single(), WRITE).takeIf { it == 0 } ?: TODO()
                    acl_add_perm(permset.single(), EXECUTE).takeIf { it == 0 } ?: TODO()
                }
            }

            run {
                acl_create_entry(PointerByReference(acl), entry).takeIf { it == 0 } ?: run {
                    log.debug("Last error: " + Native.getLastError())
                    TODO()
                }
                acl_set_tag_type(entry.single(), USER).takeIf { it == 0 } ?: TODO()
                acl_set_qualifier(entry.single(), intArrayOf(1006)).takeIf { it == 0 } ?: TODO()
                acl_get_permset(entry.single(), permset).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), READ).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), WRITE).takeIf { it == 0 } ?: TODO()
                acl_add_perm(permset.single(), EXECUTE).takeIf { it == 0 } ?: TODO()
            }


            acl_set_file(path, type, acl).takeIf { it == 0 } ?: run {
                log.debug("${Native.getLastError()}")
                TODO()
            }
//
            // TODO ACL free on acl_create_entry

            acl_free(acl)
        }
    }
}

class ACLException(why: String) : RuntimeException(why)

