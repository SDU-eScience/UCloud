package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.SERVICE_UNIX_USER
import dk.sdu.cloud.storage.api.*

// This slightly messy code allows us to skip null checks. This makes for a better API
class FileRow(
    private val _fileType: FileType?,
    private val _isLink: Boolean?,
    private val _linkTarget: String?,
    private val _unixMode: Int?,
    private val _owner: String?,
    private val _group: String?,
    private val _timestamps: Timestamps?,
    private val _path: String?,
    private val _rawPath: String?,
    private val _inode: String?,
    private val _size: Long?,
    private val _shares: List<AccessEntry>?,
    private val _annotations: Set<String>?,
    private val _checksum: FileChecksum?,
    private val _sensitivityLevel: SensitivityLevel?,
    private val _linkInode: String?
) {
    val fileType: FileType get() = _fileType!!
    val isLink: Boolean get() = _isLink!!
    val linkTarget: String get() = _linkTarget!!
    val unixMode: Int get() = _unixMode!!
    val owner: String get() = _owner!!
    val group: String get() = _group!!
    val timestamps: Timestamps get() = _timestamps!!
    val path: String get() = _path!!
    val rawPath: String get() = _rawPath!!
    val inode: String get() = _inode!!
    val size: Long get() = _size!!
    val shares: List<AccessEntry> get() = _shares!!
    val annotations: Set<String> get() = _annotations!!
    val checksum: FileChecksum get() = _checksum!!
    val sensitivityLevel: SensitivityLevel get() = _sensitivityLevel!!
    val linkInode: String get() = _linkInode!!

    fun convertToCloud(usernameConverter: (String) -> String, pathConverter: (String) -> String): FileRow {
        fun normalizeShares(incoming: List<AccessEntry>): List<AccessEntry> {
            return incoming.mapNotNull {
                if (it.isGroup) {
                    it
                } else {
                    if (it.entity == SERVICE_UNIX_USER) null
                    else it.copy(entity = usernameConverter(it.entity))
                }
            }
        }

        return FileRow(
            _fileType,
            _isLink,
            _linkTarget?.let(pathConverter),
            _unixMode,
            _owner?.let(usernameConverter),
            _group,
            _timestamps,
            _path?.let(pathConverter),
            _rawPath?.let(pathConverter),
            _inode,
            _size,
            _shares?.let { normalizeShares(it) },
            _annotations,
            _checksum,
            _sensitivityLevel,
            _linkInode
        )
    }
}


fun Set<FileAttribute>.asBitSet(): Long {
    var result = 0L
    for (item in this) {
        result = result or item.value
    }
    return result
}
