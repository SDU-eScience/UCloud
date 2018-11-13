package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.storage.services.FileAttribute
import dk.sdu.cloud.storage.services.FileRow

private const val SHARED_WITH_UTYPE = 1
private const val SHARED_WITH_READ = 2
private const val SHARED_WITH_WRITE = 4
private const val SHARED_WITH_EXECUTE = 8
private const val TO_MILLISECONDS = 1000
internal fun FileAttribute.Companion.rawParse(
    iterator: Iterator<String>,
    attributes: Set<FileAttribute>
): Sequence<StatusTerminatedItem<FileRow>> {
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
        var rawPath: String? = null
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
                return@generateSequence StatusTerminatedItem.Exit<FileRow>(status)
            }

            attributesCovered++
            when (attribute) {
                null -> throw NullPointerException()

                FileAttribute.FILE_TYPE -> {
                    fileType = when (currentLine) {
                        "F" -> FileType.FILE
                        "D" -> FileType.DIRECTORY
                        "L" -> FileType.LINK
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
                    val accessed = currentLine.toLong() * TO_MILLISECONDS
                    val modified = next().toLong() * TO_MILLISECONDS
                    val created = next().toLong() * TO_MILLISECONDS

                    timestamps = Timestamps(accessed, created, modified)
                }

                FileAttribute.PATH -> path = currentLine

                FileAttribute.RAW_PATH -> rawPath = currentLine

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

                    checksum = FileChecksum(algorithm = type, checksum = sum)
                }

                FileAttribute.SENSITIVITY -> {
                    sensitivityLevel = try {
                        SensitivityLevel.valueOf(currentLine)
                    } catch (ex: IllegalArgumentException) {
                        SensitivityLevel.PRIVATE
                    }
                }

                FileAttribute.LINK_INODE -> {
                    linkInode = currentLine
                }
            }
        }

        if (attributesCovered == attributes.size) {
            StatusTerminatedItem.Item(
                FileRow(
                    fileType,
                    isLink,
                    linkTarget,
                    unixMode,
                    owner,
                    group,
                    timestamps,
                    path,
                    rawPath,
                    inode,
                    size,
                    shares,
                    annotations,
                    checksum,
                    sensitivityLevel,
                    linkInode
                )
            )
        } else {
            if (attributesCovered != 0) parsingError("unexpected end of stream")
            null
        }
    }
}

sealed class StatusTerminatedItem<T> {
    data class Exit<T>(val statusCode: Int) : StatusTerminatedItem<T>()
    data class Item<T>(val item: T) : StatusTerminatedItem<T>()
}


