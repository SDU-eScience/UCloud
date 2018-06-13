package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.AccessEntry
import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SensitivityLevel
import java.util.Comparator
import kotlin.collections.ArrayList

enum class FileAttribute(val value: Long) {
    // File type and link operations
    FILE_TYPE(1 shl 0),
    IS_LINK(1 shl 1),
    LINK_TARGET(1 shl 2),

    // Basic (directly available in stat)
    UNIX_MODE(1 shl 3),
    OWNER(1 shl 4),
    GROUP(1 shl 5),
    TIMESTAMPS(1 shl 6),
    PATH(1 shl 7),
    INODE(1 shl 8),
    SIZE(1 shl 9),

    // Special (XAttr/ACL based)
    SHARES(1 shl 10),
    ANNOTATIONS(1 shl 11),
    CHECKSUM(1 shl 12),
    SENSITIVITY(1 shl 13);
}

data class FileRow(
    val fileType: FileType?,
    val isLink: Boolean?,
    val linkTarget: String?,
    val unixMode: Int?,
    val owner: String?,
    val group: String?,
    val timestamps: Timestamps?,
    val path: String?,
    val inode: String?,
    val size: Long?,
    val shares: List<AccessEntry>?,
    val annotations: Set<String>?,
    val checksum: FileChecksum?,
    val sensitivityLevel: SensitivityLevel?
)

data class Timestamps(val accessed: Long, val created: Long, val modified: Long)
data class FileChecksum(val checksum: String, val type: String)

fun Set<FileAttribute>.asBitSet(): Long {
    var result = 0L
    for (item in this) {
        result = result or item.value
    }
    return result
}

fun parseFileAttributes(input: Iterable<String>, attributes: Set<FileAttribute>): List<FileRow> {
    val result = ArrayList<FileRow>()
    val sortedAttributes = attributes.toSortedSet(Comparator.comparingLong { it.value })

    val iterator = input.iterator()

    while (iterator.hasNext()) {
        sortedAttributes.forEach { attribute ->
            var fileType: FileType? = null
            var isLink: Boolean? = null
            var linkTarget: String? = null
            var unixMode: Int? = null
            var owner: String? = null
            var group: String? = null
            var timestamps: Timestamps? = null
            var path: String? = null
            var inode: String? = null
            var size: Long? = null
            var shares: List<AccessEntry>? = null
            var annotations: Set<String>? = null
            var checksum: FileChecksum? = null
            var sensitivityLevel: SensitivityLevel? = null

            when (attribute) {
                null -> throw NullPointerException()

                FileAttribute.FILE_TYPE -> {
                    fileType = when (iterator.next()) {
                        "F" -> FileType.FILE
                        "D" -> FileType.DIRECTORY
                        else -> throw IllegalStateException()
                    }
                }

                FileAttribute.IS_LINK -> {
                    isLink = when (iterator.next()) {
                        "1" -> true
                        "0" -> false
                        else -> throw IllegalStateException()
                    }
                }

                FileAttribute.LINK_TARGET -> linkTarget = iterator.next()

                FileAttribute.UNIX_MODE -> unixMode = iterator.next().toInt()

                FileAttribute.OWNER -> owner = iterator.next()

                FileAttribute.GROUP -> group = iterator.next()

                FileAttribute.TIMESTAMPS -> {
                    val accessed = iterator.next().toLong() * 1000
                    val modified = iterator.next().toLong() * 1000
                    val created = iterator.next().toLong() * 1000

                    timestamps = Timestamps(accessed, created, modified)
                }

                FileAttribute.PATH -> path = iterator.next()

                FileAttribute.INODE -> inode = iterator.next()

                FileAttribute.SIZE -> size = iterator.next().toLong()

                FileAttribute.SHARES -> {
                    shares = (0 until iterator.next().toInt()).map {
                        val aclEntity = iterator.next()
                        val mode = iterator.next().toInt()

                        val isGroup = (mode and SHARED_WITH_UTYPE) != 0
                        val hasRead = (mode and SHARED_WITH_READ) != 0
                        val hasWrite = (mode and SHARED_WITH_WRITE) != 0
                        val hasExecute = (mode and SHARED_WITH_EXECUTE) != 0

                        val rights = mutableSetOf<AccessRight>()
                        if (hasRead) rights += AccessRight.READ
                        if (hasWrite) rights += AccessRight.WRITE
                        if (hasExecute) rights += AccessRight.EXECUTE

                        AccessEntry(aclEntity, isGroup, rights)
                    }
                }

                FileAttribute.ANNOTATIONS -> {
                    annotations = iterator.next().toCharArray().map { it.toString() }.toSet()
                }

                FileAttribute.CHECKSUM -> {
                    val sum = iterator.next()
                    val type = iterator.next()

                    checksum = FileChecksum(sum, type)
                }

                FileAttribute.SENSITIVITY -> {
                    sensitivityLevel = SensitivityLevel.valueOf(iterator.next())
                }
            }

            result.add(
                FileRow(
                    fileType,
                    isLink,
                    linkTarget,
                    unixMode,
                    owner,
                    group,
                    timestamps,
                    path,
                    inode,
                    size,
                    shares,
                    annotations,
                    checksum,
                    sensitivityLevel
                )
            )
        }
    }

    return result
}

private const val SHARED_WITH_UTYPE = 1
private const val SHARED_WITH_READ = 2
private const val SHARED_WITH_WRITE = 4
private const val SHARED_WITH_EXECUTE = 8
