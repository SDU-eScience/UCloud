package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.Timestamps

// This slightly messy code allows us to skip null checks. This makes for a better API
@Suppress("ConstructorParameterNaming")
class FileRow(
    val _fileType: FileType?,
    val _isLink: Boolean?,
    val _linkTarget: String?,
    val _unixMode: Int?,
    val _creator: String?,
    val _group: String?,
    val _timestamps: Timestamps?,
    val _path: String?,
    val _rawPath: String?,
    val _inode: String?,
    val _size: Long?,
    val _shares: List<AccessEntry>?,
    val _sensitivityLevel: SensitivityLevel?,
    val _linkInode: String?,
    val _owner: String?
) {
    val fileType: FileType get() = _fileType!!
    val isLink: Boolean get() = _isLink!!
    val linkTarget: String get() = _linkTarget!!
    val unixMode: Int get() = _unixMode!!
    val creator: String get() = _creator!!
    val group: String get() = _group!!
    val timestamps: Timestamps get() = _timestamps!!
    val path: String get() = _path!!
    val rawPath: String get() = _rawPath!!
    val inode: String get() = _inode!!
    val size: Long get() = _size!!
    val shares: List<AccessEntry> get() = _shares!!
    val sensitivityLevel: SensitivityLevel? get() = _sensitivityLevel
    val linkInode: String get() = _linkInode!!
    val owner: String get() = _owner!!

    override fun toString(): String {
        return "FileRow(" +
                "_fileType=$_fileType, \n" +
                "_isLink=$_isLink, \n" +
                "_linkTarget=$_linkTarget, \n" +
                "_unixMode=$_unixMode, \n" +
                "_creator=$_creator, \n" +
                "_group=$_group, \n" +
                "_timestamps=$_timestamps, \n" +
                "_path=$_path, \n" +
                "_rawPath=$_rawPath, \n" +
                "_inode=$_inode, \n" +
                "_size=$_size, \n" +
                "_shares=$_shares, \n" +
                "_sensitivityLevel=$_sensitivityLevel, \n" +
                "_linkInode=$_linkInode, \n" +
                "_owner=$_owner\n" +
                ")"
    }
}

fun FileRow.mergeWith(other: FileRow): FileRow {
    val _fileType: FileType? = this._fileType ?: other._fileType
    val _isLink: Boolean? = this._isLink ?: other._isLink
    val _linkTarget: String? = this._linkTarget ?: other._linkTarget
    val _unixMode: Int? = this._unixMode ?: other._unixMode
    val _creator: String? = this._creator ?: other._creator
    val _group: String? = this._group ?: other._group
    val _timestamps: Timestamps? = this._timestamps ?: other._timestamps
    val _path: String? = this._path ?: other._path
    val _rawPath: String? = this._rawPath ?: other._rawPath
    val _inode: String? = this._inode ?: other._inode
    val _size: Long? = this._size ?: other._size
    val _shares: List<AccessEntry>? = this._shares ?: other._shares
    val _sensitivityLevel: SensitivityLevel? = this._sensitivityLevel ?: other._sensitivityLevel
    val _linkInode: String? = this._linkInode ?: other._linkInode
    val _owner: String? = this._owner ?: other._owner

    return FileRow(
        _fileType,
        _isLink,
        _linkTarget,
        _unixMode,
        _creator,
        _group,
        _timestamps,
        _path,
        _rawPath,
        _inode,
        _size,
        _shares,
        _sensitivityLevel,
        _linkInode,
        _owner
    )
}

