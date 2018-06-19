package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.AccessEntry
import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SensitivityLevel

enum class FileAttribute(val value: Long) {
    FILE_TYPE(1 shl 0),
    IS_LINK(1 shl 1),
    LINK_TARGET(1 shl 2),
    LINK_INODE(1 shl 3),
    UNIX_MODE(1 shl 4),
    OWNER(1 shl 5),
    GROUP(1 shl 6),
    TIMESTAMPS(1 shl 7),
    PATH(1 shl 8),
    INODE(1 shl 9),
    SIZE(1 shl 10),
    SHARES(1 shl 11),
    ANNOTATIONS(1 shl 12),
    CHECKSUM(1 shl 13),
    SENSITIVITY(1 shl 14),
    ;

    companion object {
        private const val SHARED_WITH_UTYPE = 1
        private const val SHARED_WITH_READ = 2
        private const val SHARED_WITH_WRITE = 4
        private const val SHARED_WITH_EXECUTE = 8

        internal fun rawParse(
            iterator: Iterator<String>,
            attributes: Set<FileAttribute>
        ): Sequence<FileRow> {
            var line = 0
            val sortedAttributes = attributes.toSortedSet(Comparator.comparingLong { it.value })

            fun next(): String {
                line++
                return iterator.next()
            }

            fun parsingError(why: String): Nothing {
                throw IllegalStateException("Parsing error in line: $line. $why")
            }

            return generateSequence {
                if (!iterator.hasNext()) return@generateSequence null
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
                var linkInode: String? = null

                var attributesCovered = 0
                for (attribute in sortedAttributes) {
                    val currentLine = next()

                    if (currentLine.startsWith("EXIT:")) {
                        val status = currentLine.split(":")[1].toInt()
                        if (status != 0) throwExceptionBasedOnStatus(status)
                        break
                    }

                    attributesCovered++
                    when (attribute) {
                        null -> throw NullPointerException()

                        FileAttribute.FILE_TYPE -> {
                            fileType = when (currentLine) {
                                "F" -> FileType.FILE
                                "D" -> FileType.DIRECTORY
                                else -> parsingError("$currentLine is to a recognized file type!")
                            }
                        }

                        FileAttribute.IS_LINK -> {
                            isLink = when (currentLine) {
                                "1" -> true
                                "0" -> false
                                else -> parsingError("Not a valid boolean")
                            }
                        }

                        FileAttribute.LINK_TARGET -> linkTarget = currentLine

                        FileAttribute.UNIX_MODE -> unixMode = currentLine.toInt()

                        FileAttribute.OWNER -> owner = currentLine

                        FileAttribute.GROUP -> group = currentLine

                        FileAttribute.TIMESTAMPS -> {
                            val accessed = currentLine.toLong() * 1000
                            val modified = next().toLong() * 1000
                            val created = next().toLong() * 1000

                            timestamps = Timestamps(accessed, created, modified)
                        }

                        FileAttribute.PATH -> path = currentLine

                        FileAttribute.INODE -> inode = currentLine

                        FileAttribute.SIZE -> size = currentLine.toLong()

                        FileAttribute.SHARES -> {
                            shares = (0 until currentLine.toInt()).map {
                                val aclEntity = next()
                                val mode = next().toInt()

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
                            annotations = currentLine.toCharArray().map { it.toString() }.toSet()
                        }

                        FileAttribute.CHECKSUM -> {
                            val sum = currentLine
                            val type = next()

                            checksum = FileChecksum(sum, type)
                        }

                        FileAttribute.SENSITIVITY -> {
                            sensitivityLevel = SensitivityLevel.valueOf(currentLine)
                        }

                        FileAttribute.LINK_INODE -> {
                            linkInode = currentLine
                        }
                    }
                }

                if (attributesCovered == attributes.size) {
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
                        sensitivityLevel,
                        linkInode
                    )
                } else {
                    if (attributesCovered != 0) parsingError("unexpected end of stream")
                    null
                }
            }
        }
    }
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
    val sensitivityLevel: SensitivityLevel?,
    val linkInode: String?
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

