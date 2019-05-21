package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.ptr.PointerByReference
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.service.Loggable
import org.slf4j.Logger
import java.lang.IllegalArgumentException

data class Entry(
    val isUser: Boolean,
    val id: String,
    val rights: Set<AccessRight>
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
    private const val ACCESS = 0x8000
    private const val DEFAULT = 0x4000

    fun getEntries(path: String): List<Entry> {
        if (!Platform.isLinux()) return emptyList()

        with(ACLLibrary.INSTANCE) {
            val type = ACCESS
            val entry = LongArray(1)
            val tag = ACLTag()
            val permset = ACLPermSet()

            val acl = acl_get_file(path, type) ?: return emptyList()

            val result = ArrayList<Entry>()
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

                        val rights = HashSet<AccessRight>()
                        if (hasRead) rights += AccessRight.READ
                        if (hasWrite) rights += AccessRight.WRITE
                        if (hasExecute) rights += AccessRight.EXECUTE

                        result.add(
                            Entry(
                                isUser,
                                uid.toString(),
                                rights
                            )
                        )
                    }
                }
            }
            acl_free(acl)

            // Normalize ACL by collapsing entries of identical IDs
            return result.groupBy { Pair(it.id, it.isUser) }.map { (kv, entries) ->
                val (id, isUser) = kv
                val rights = HashSet<AccessRight>()

                entries.forEach { rights.addAll(it.rights) }
                Entry(isUser, id, rights)
            }
        }
    }

    var calls = 0
    fun addEntry(path: String, uid: Int, permissions: Set<AccessRight>, defaultList: Boolean = false) {
        if (!Platform.isLinux()) {
            calls++
            if (calls == 10) throw IllegalArgumentException("FAKE ERROR")

            Thread.sleep(100)
            log.info("addEntry($path, $uid, $permissions, defaultList = $defaultList)")
            return
        }

        with(ACLLibrary.INSTANCE) {
            val type = if (defaultList) DEFAULT else ACCESS
            val entry = LongArray(1)
            val permset = ACLPermSet()

            val acl = acl_get_file(path, type) ?: throw NativeException(Native.getLastError())
            val initialEntryCount = acl_entries(acl)

            if ((initialEntryCount == 3L && !defaultList) || (initialEntryCount == 0L && defaultList)) {
                // Create mask if we have not created it yet
                acl_create_entry(PointerByReference(acl), entry).orThrow()
                acl_set_tag_type(entry.single(), MASK).orThrow()
                acl_get_permset(entry.single(), permset).orThrow()
                acl_add_perm(permset.single(), READ).orThrow()
                acl_add_perm(permset.single(), WRITE).orThrow()
                acl_add_perm(permset.single(), EXECUTE).orThrow()

                if (defaultList) {
                    // For the default list we _also_ need to add entries for the creator and group
                    run {
                        acl_create_entry(PointerByReference(acl), entry).orThrow()
                        acl_set_tag_type(entry.single(), USER_OWNER).orThrow()
                        acl_get_permset(entry.single(), permset).orThrow()
                        acl_add_perm(permset.single(), READ).orThrow()
                        acl_add_perm(permset.single(), WRITE).orThrow()
                        acl_add_perm(permset.single(), EXECUTE).orThrow()
                    }

                    run {
                        acl_create_entry(PointerByReference(acl), entry).orThrow()
                        acl_set_tag_type(entry.single(), GROUP_OWNER).orThrow()
                        acl_get_permset(entry.single(), permset).orThrow()
                        acl_add_perm(permset.single(), READ).orThrow()
                        acl_add_perm(permset.single(), WRITE).orThrow()
                        acl_add_perm(permset.single(), EXECUTE).orThrow()
                    }

                    run {
                        acl_create_entry(PointerByReference(acl), entry).orThrow()
                        acl_set_tag_type(entry.single(), OTHER).orThrow()
                        acl_get_permset(entry.single(), permset).orThrow()
                        acl_clear_perms(permset.single())
                    }
                }
            }

            run {
                // Create new entry
                acl_create_entry(PointerByReference(acl), entry).orThrow()

                acl_set_tag_type(entry.single(), USER).orThrow()
                acl_set_qualifier(entry.single(), intArrayOf(uid)).orThrow()

                acl_get_permset(entry.single(), permset).orThrow()
                if (AccessRight.READ in permissions) {
                    acl_add_perm(permset.single(), READ).orThrow()
                }

                if (AccessRight.WRITE in permissions) {
                    acl_add_perm(permset.single(), WRITE).orThrow()
                }

                if (AccessRight.EXECUTE in permissions) {
                    acl_add_perm(permset.single(), EXECUTE).orThrow()
                }
            }

            acl_set_file(path, type, acl).orThrow()
            acl_free(acl)
        }
    }

    fun removeEntry(path: String, uid: Int, defaultList: Boolean = false) {
        if (!Platform.isLinux()) {
            log.info("removeEntry($path, $uid, defaultList = $defaultList)")
            return
        }

        with(ACLLibrary.INSTANCE) {
            val type = if (defaultList) DEFAULT else ACCESS
            val entry = LongArray(1)
            val tag = ACLTag()
            val acl = acl_get_file(path, type) ?: throw NativeException(Native.getLastError())

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

                    val entryUid = qualifier.getInt(0)
                    acl_free(qualifier)

                    if (entryUid == uid) {
                        acl_delete_entry(acl, entry.single())
                    }
                }
            }

            acl_set_file(path, type, acl).orThrow()
            acl_free(acl)
        }
    }

    fun copyList(from: String, to: String, addDefaultList: Boolean) {
        val entries = getEntries(from)
        log.debug("Copying ${entries.size} from $from to $to")
        entries.forEach {
            if (it.isUser) {
                addEntry(to, it.id.toInt(), it.rights, defaultList = false)
                if (addDefaultList) {
                    addEntry(to, it.id.toInt(), it.rights, defaultList = true)
                }
            }
        }
    }

    private fun Int.orThrow() {
        if (this != 0) throw NativeException(Native.getLastError())
    }

}

class ACLException(why: String) : RuntimeException(why)

