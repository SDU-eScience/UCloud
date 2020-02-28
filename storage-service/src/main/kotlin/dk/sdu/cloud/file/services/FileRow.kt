package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.Timestamps

// This slightly messy code allows us to skip null checks. This makes for a better API
@Suppress("ConstructorParameterNaming")
class FileRow(
    val _fileType: FileType?,
    val _creator: String?,
    val _timestamps: Timestamps?,
    val _path: String?,
    val _inode: String?,
    val _size: Long?,
    val _shares: List<AccessEntry>?,
    val _sensitivityLevel: SensitivityLevel?,
    val _owner: String?
) {
    val fileType: FileType get() = _fileType!!
    val timestamps: Timestamps get() = _timestamps!!
    val path: String get() = _path!!
    val inode: String get() = _inode!!
    val size: Long get() = _size!!
    val shares: List<AccessEntry> get() = _shares!!
    val sensitivityLevel: SensitivityLevel? get() = _sensitivityLevel
    val owner: String get() = _owner!!

    override fun toString(): String {
        return "FileRow(" +
                "_fileType=$_fileType, \n" +
                "_creator=$_creator, \n" +
                "_timestamps=$_timestamps, \n" +
                "_path=$_path, \n" +
                "_inode=$_inode, \n" +
                "_size=$_size, \n" +
                "_shares=$_shares, \n" +
                "_sensitivityLevel=$_sensitivityLevel, \n" +
                "_owner=$_owner\n" +
                ")"
    }
}

fun FileRow.mergeWith(other: FileRow): FileRow {
    val _fileType: FileType? = this._fileType ?: other._fileType
    val _creator: String? = this._creator ?: other._creator
    val _timestamps: Timestamps? = this._timestamps ?: other._timestamps
    val _path: String? = this._path ?: other._path
    val _inode: String? = this._inode ?: other._inode
    val _size: Long? = this._size ?: other._size
    val _shares: List<AccessEntry>? = this._shares ?: other._shares
    val _sensitivityLevel: SensitivityLevel? = this._sensitivityLevel ?: other._sensitivityLevel
    val _owner: String? = this._owner ?: other._owner

    return FileRow(
        _fileType,
        _creator,
        _timestamps,
        _path,
        _inode,
        _size,
        _shares,
        _sensitivityLevel,
        _owner
    )
}

