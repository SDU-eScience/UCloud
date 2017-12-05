package org.esciencecloud.storage.ext.irods

import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.NotFoundException
import org.esciencecloud.storage.ext.PermissionException
import org.irods.jargon.core.exception.*
import org.irods.jargon.core.packinstr.DataObjInp
import org.irods.jargon.core.protovalues.FilePermissionEnum
import org.irods.jargon.core.pub.*
import org.irods.jargon.core.pub.domain.*
import org.irods.jargon.core.pub.io.*
import org.irods.jargon.core.query.*
import org.irods.jargon.core.transfer.TransferControlBlock
import org.irods.jargon.core.transfer.TransferStatusCallbackListener
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.io.FilenameFilter
import java.net.URI

// Semi-automatically generated code which wraps all iRODS calls with remapException
// Generation done via text-editor macros

// TODO Could probably just use a dynamic proxy instead of this

private inline fun <T> remapException(call: () -> T): T {
    try {
        return call()
    } catch (exception: Exception) {
        throw remapException(exception)
    }
}

fun remapException(exception: Throwable): Exception {
    when (exception) {
        is FileNotFoundException, is org.irods.jargon.core.exception.FileNotFoundException -> {
            return NotFoundException("object", "Unknown", exception.message ?: "Unknown")
        }
        is InvalidGroupException -> {
            return NotFoundException("usergroup", "Unknown", exception.message ?: "Unknown")
        }
        is DuplicateDataException -> {
            return PermissionException("Cannot create new entry - Entry already exists. Cause: ${exception.message}")
        }
        is CatNoAccessException -> {
            return PermissionException("Not allowed. Cause: ${exception.message}")
        }
        is DataNotFoundException -> {
            return NotFoundException("Unknown", "Unknown", exception.message ?: "Unknown")
        }

        // Needs to be just before the else branch since this is the super type of all Jargon exceptions
        is JargonException -> {
            val cause = exception.cause as? Exception
            return if (cause != null) {
                remapException(cause)
            } else {
                RuntimeException("Caught unexpected exception", exception)
            }
        }

        else -> {
            return RuntimeException("Exception in iRODS. Cause is unknown.", exception)
        }
    }
}

inline fun <T : Any> remapExceptionToResult(call: () -> T?): Result<T> {
    return try {
        val result = call()
        if (result != null) Ok(result) else Error.notFound()
    } catch (ex: Exception) {
        remapExceptionToResult(ex)
    }
}

fun <T : Any> remapExceptionToResult(exception: Exception): Result<T> {
    var current = exception
    while (true) {
        when (current) {
            is FileNotFoundException, is org.irods.jargon.core.exception.FileNotFoundException ->
                return Error.notFound()
            is InvalidGroupException -> return Error.notFound()
            is DuplicateDataException -> return Error.duplicateResource()
            is CatNoAccessException -> return Error.permissionDenied()
            is DataNotFoundException -> return Error.notFound()

        // Needs to be just before the else branch since this is the super type of all Jargon exceptions
            is JargonException -> {
                current = exception.cause as? Exception ?:
                        throw RuntimeException("Caught unexpected exception", exception)
            }

            else -> {
                throw RuntimeException("Exception in iRODS. Cause is unknown.", exception)
            }
        }
    }
}

interface StorageUserAO {
    fun addUser(user: User): Result<User>
    fun findAll(): Result<List<User>>
    fun findByName(name: String): Result<User>
    fun findById(id: String): Result<User>
    fun findByIdInZone(id: String, zone: String): Result<User>
    fun listUserMetadataForUserId(id: String): Result<List<AvuData>>
    fun listUserMetadataForUserName(id: String): Result<List<AvuData>>
    fun deleteUser(username: String): Result<Unit>
    fun updateUser(user: User): Result<Unit>
    fun findWhere(whereStatement: String): Result<List<User>>
    fun changeAUserPasswordByThatUser(username: String, currentPassword: String, newPassword: String): Result<Unit>
    fun changeAUserPasswordByAnAdmin(username: String, newPassword: String): Result<Unit>
    fun addAVUMetadata(username: String, avuTuple: AvuData): Result<Unit>
    fun deleteAVUMetadata(username: String, avuTuple: AvuData): Result<Unit>
    fun modifyAVUMetadata(username: String, avuTuple: AvuData): Result<Unit>
    fun findUserNameLike(likeUserName: String): Result<List<String>>
    fun getTemporaryPasswordForConnectedUser(): Result<String>
    fun getTemporaryPasswordForASpecifiedUser(username: String): Result<String>
    fun retriveUserDNByUserId(var1: String): Result<String>
    fun updateUserInfo(var1: String, var2: String): Result<Unit>
    fun updateUserDN(var1: String, var2: String): Result<Unit>
    fun removeUserDN(var1: String, var2: String): Result<Unit>
}

class UserAOWrapper(private val delegate: UserAO) : StorageUserAO {
    override fun modifyAVUMetadata(username: String, avuTuple: AvuData): Result<Unit> {
        return remapExceptionToResult { delegate.modifyAVUMetadata(username, avuTuple) }
    }

    override fun updateUser(user: User): Result<Unit> {
        return remapExceptionToResult { delegate.updateUser(user) }
    }

    override fun addAVUMetadata(username: String, avuTuple: AvuData): Result<Unit> {
        return remapExceptionToResult { delegate.addAVUMetadata(username, avuTuple) }
    }

    override fun getTemporaryPasswordForConnectedUser(): Result<String> {
        return remapExceptionToResult { delegate.temporaryPasswordForConnectedUser }
    }

    override fun updateUserDN(var1: String, var2: String): Result<Unit> {
        return remapExceptionToResult { delegate.updateUserDN(var1, var2) }
    }

    override fun removeUserDN(var1: String, var2: String): Result<Unit> {
        return remapExceptionToResult { delegate.removeUserDN(var1, var2) }
    }

    override fun listUserMetadataForUserName(id: String): Result<List<AvuData>> {
        return remapExceptionToResult { delegate.listUserMetadataForUserName(id) }
    }

    override fun updateUserInfo(var1: String, var2: String): Result<Unit> {
        return remapExceptionToResult { delegate.updateUserInfo(var1, var2) }
    }

    override fun findAll(): Result<List<User>> {
        return remapExceptionToResult { delegate.findAll() }
    }

    override fun listUserMetadataForUserId(id: String): Result<List<AvuData>> {
        return remapExceptionToResult { delegate.listUserMetadataForUserId(id) }
    }

    override fun changeAUserPasswordByAnAdmin(username: String, newPassword: String): Result<Unit> {
        return remapExceptionToResult { delegate.changeAUserPasswordByAnAdmin(username, newPassword) }
    }

    override fun getTemporaryPasswordForASpecifiedUser(username: String): Result<String> {
        return remapExceptionToResult { delegate.getTemporaryPasswordForASpecifiedUser(username) }
    }

    override fun changeAUserPasswordByThatUser(
            username: String,
            currentPassword: String,
            newPassword: String
    ): Result<Unit> {
        return remapExceptionToResult { delegate.changeAUserPasswordByThatUser(username, currentPassword, newPassword) }
    }

    override fun findUserNameLike(likeUserName: String): Result<List<String>> {
        return remapExceptionToResult { delegate.findUserNameLike(likeUserName) }
    }

    override fun deleteAVUMetadata(username: String, avuTuple: AvuData): Result<Unit> {
        return remapExceptionToResult { delegate.deleteAVUMetadata(username, avuTuple) }
    }

    override fun findByName(name: String): Result<User> {
        return remapExceptionToResult { delegate.findByName(name) }
    }

    override fun findWhere(whereStatement: String): Result<List<User>> {
        return remapExceptionToResult { delegate.findWhere(whereStatement) }
    }

    override fun findByIdInZone(id: String, zone: String): Result<User> {
        return remapExceptionToResult { delegate.findByIdInZone(id, zone) }
    }

    override fun addUser(user: User): Result<User> {
        return remapExceptionToResult { delegate.addUser(user) }
    }

    override fun findById(id: String): Result<User> {
        return remapExceptionToResult { delegate.findById(id) }
    }

    override fun deleteUser(username: String): Result<Unit> {
        return remapExceptionToResult { delegate.deleteUser(username) }
    }

    override fun retriveUserDNByUserId(var1: String): Result<String> {
        return remapExceptionToResult { delegate.retriveUserDNByUserId(var1) }
    }
}

class FileFactoryWrapper(private val delegate: IRODSFileFactory) : IRODSFileFactory by delegate {
    override fun instanceIRODSFileReader(p0: String?): IRODSFileReader {
        return remapException { delegate.instanceIRODSFileReader(p0) }
    }

    override fun instanceIRODSFileInputStreamWithRerouting(p0: String?): IRODSFileInputStream {
        return remapException { delegate.instanceIRODSFileInputStreamWithRerouting(p0) }
    }

    override fun instanceIRODSFileOutputStream(p0: String?): IRODSFileOutputStream {
        return remapException { delegate.instanceIRODSFileOutputStream(p0) }
    }

    override fun instanceIRODSFileOutputStream(p0: String?, p1: DataObjInp.OpenFlags?): IRODSFileOutputStream {
        return remapException { delegate.instanceIRODSFileOutputStream(p0) }
    }

    override fun instanceIRODSFileOutputStream(p0: IRODSFile?): IRODSFileOutputStream {
        return remapException { delegate.instanceIRODSFileOutputStream(p0) }
    }

    override fun instanceIRODSFileOutputStream(p0: IRODSFile?, p1: DataObjInp.OpenFlags?): IRODSFileOutputStream {
        return remapException { delegate.instanceIRODSFileOutputStream(p0) }
    }

    override fun instanceIRODSRandomAccessFile(p0: IRODSFile?): IRODSRandomAccessFile {
        return remapException { delegate.instanceIRODSRandomAccessFile(p0) }
    }

    override fun instanceIRODSRandomAccessFile(p0: String?): IRODSRandomAccessFile {
        return remapException { delegate.instanceIRODSRandomAccessFile(p0) }
    }

    override fun instanceIRODSRandomAccessFile(p0: String?, p1: DataObjInp.OpenFlags?): IRODSRandomAccessFile {
        return remapException { delegate.instanceIRODSRandomAccessFile(p0) }
    }

    override fun instanceSessionClosingIRODSFileOutputStream(p0: IRODSFile?): SessionClosingIRODSFileOutputStream {
        return remapException { delegate.instanceSessionClosingIRODSFileOutputStream(p0) }
    }

    override fun instanceSessionClosingIRODSFileInputStream(p0: IRODSFile?): SessionClosingIRODSFileInputStream {
        return remapException { delegate.instanceSessionClosingIRODSFileInputStream(p0) }
    }

    override fun instanceSessionClosingIRODSFileInputStream(p0: String?): SessionClosingIRODSFileInputStream {
        return remapException { delegate.instanceSessionClosingIRODSFileInputStream(p0) }
    }

    override fun instanceIRODSFileOutputStreamWithRerouting(p0: IRODSFile?): IRODSFileOutputStream {
        return remapException { delegate.instanceIRODSFileOutputStreamWithRerouting(p0) }
    }

    override fun instanceIRODSFileInputStreamGivingFD(p0: IRODSFile?, p1: Int): IRODSFileInputStream {
        return remapException { delegate.instanceIRODSFileInputStreamGivingFD(p0, p1) }
    }

    override fun instanceIRODSFileWriter(p0: String?): IRODSFileWriter {
        return remapException { delegate.instanceIRODSFileWriter(p0) }
    }

    override fun instanceIRODSFileInputStream(p0: IRODSFile?): IRODSFileInputStream {
        return remapException { delegate.instanceIRODSFileInputStream(p0) }
    }

    override fun instanceIRODSFileInputStream(p0: String?): IRODSFileInputStream {
        return remapException { delegate.instanceIRODSFileInputStream(p0) }
    }

    override fun instanceIRODSFile(p0: URI?): IRODSFile {
        return remapException { delegate.instanceIRODSFile(p0) }
    }

    override fun instanceIRODSFile(p0: String?, p1: String?): IRODSFile {
        return remapException { delegate.instanceIRODSFile(p0) }
    }

    override fun instanceIRODSFile(p0: String?): IRODSFile {
        return remapException { delegate.instanceIRODSFile(p0) }
    }

    override fun instanceIRODSFile(p0: File?, p1: String?): IRODSFile {
        return remapException { delegate.instanceIRODSFile(p0, p1) }
    }

    override fun instanceIRODSFileUserHomeDir(p0: String?): IRODSFile {
        return remapException { delegate.instanceIRODSFileUserHomeDir(p0) }
    }
}

class FileSystemWrapper(private val delegate: IRODSFileSystemAO) : IRODSFileSystemAO by delegate {
    override fun getFilePermissions(p0: IRODSFile?): Int {
        return remapException { delegate.getFilePermissions(p0) }
    }

    override fun createFileInResource(p0: String?, p1: DataObjInp.OpenFlags?, p2: Int, p3: String?): Int {
        return remapException { delegate.createFileInResource(p0, p1, p2, p3) }
    }

    override fun getDirectoryPermissionsForGivenUser(p0: IRODSFile?, p1: String?): Int {
        return remapException { delegate.getDirectoryPermissionsForGivenUser(p0, p1) }
    }

    override fun isFileReadable(p0: IRODSFile?): Boolean {
        return remapException { delegate.isFileReadable(p0) }
    }

    override fun getListInDir(p0: IRODSFile?): MutableList<String> {
        return remapException { delegate.getListInDir(p0) }
    }

    override fun isFileExists(p0: IRODSFile?): Boolean {
        return remapException { delegate.isFileExists(p0) }
    }

    override fun createFile(p0: String?, p1: DataObjInp.OpenFlags?, p2: Int): Int {
        return remapException { delegate.createFile(p0, p1, p2) }
    }

    override fun fileClose(p0: Int, p1: Boolean) {
        return remapException { delegate.fileClose(p0, p1) }
    }

    override fun physicalMove(p0: String?, p1: String?) {
        return remapException { delegate.physicalMove(p0, p1) }
    }

    override fun physicalMove(p0: IRODSFile?, p1: String?) {
        return remapException { delegate.physicalMove(p0, p1) }
    }

    override fun isFileExecutable(p0: IRODSFile?): Boolean {
        return remapException { delegate.isFileExecutable(p0) }
    }

    override fun isFile(p0: IRODSFile?): Boolean {
        return remapException { delegate.isFile(p0) }
    }

    override fun getFilePermissionsForGivenUser(p0: IRODSFile?, p1: String?): Int {
        return remapException { delegate.getFilePermissionsForGivenUser(p0, p1) }
    }

    override fun renameFile(p0: IRODSFile?, p1: IRODSFile?) {
        return remapException { delegate.renameFile(p0, p1) }
    }

    override fun fileDeleteNoForce(p0: IRODSFile?) {
        return remapException { delegate.fileDeleteNoForce(p0) }
    }

    override fun getListInDirWithFileFilter(p0: IRODSFile?, p1: FileFilter?): MutableList<File> {
        return remapException { delegate.getListInDirWithFileFilter(p0, p1) }
    }

    override fun renameDirectory(p0: IRODSFile?, p1: IRODSFile?) {
        return remapException { delegate.renameDirectory(p0, p1) }
    }

    override fun openFile(p0: IRODSFile?, p1: DataObjInp.OpenFlags?): Int {
        return remapException { delegate.openFile(p0, p1) }
    }

    override fun isFileWriteable(p0: IRODSFile?): Boolean {
        return remapException { delegate.isFileWriteable(p0) }
    }

    override fun mkdir(p0: IRODSFile?, p1: Boolean) {
        return remapException { delegate.mkdir(p0, p1) }
    }

    override fun getObjStat(p0: String?): ObjStat {
        return remapException { delegate.getObjStat(p0) }
    }

    override fun getResourceNameForFile(p0: IRODSFile?): String {
        return remapException { delegate.getResourceNameForFile(p0) }
    }

    override fun directoryDeleteNoForce(p0: IRODSFile?) {
        return remapException { delegate.directoryDeleteNoForce(p0) }
    }

    override fun isDirectory(p0: IRODSFile?): Boolean {
        return remapException { delegate.isDirectory(p0) }
    }

    override fun fileDeleteForce(p0: IRODSFile?) {
        return remapException { delegate.fileDeleteForce(p0) }
    }

    override fun getListInDirWithFilter(p0: IRODSFile?, p1: FilenameFilter?): MutableList<String> {
        return remapException { delegate.getListInDirWithFilter(p0, p1) }
    }

    override fun directoryDeleteForce(p0: IRODSFile?) {
        return remapException { delegate.directoryDeleteForce(p0) }
    }

    override fun getDirectoryPermissions(p0: IRODSFile?): Int {
        return remapException { delegate.getDirectoryPermissions(p0) }
    }

    override fun getFileDataType(p0: IRODSFile?): CollectionAndDataObjectListingEntry.ObjectType {
        return remapException { delegate.getFileDataType(p0) }
    }
}

class UserGroupsWrapper(private val delegate: UserGroupAO) : UserGroupAO by delegate {
    override fun addUserGroup(p0: UserGroup?) {
        return remapException { delegate.addUserGroup(p0) }
    }

    override fun listUserGroupMembers(p0: String?): MutableList<User> {
        return remapException { delegate.listUserGroupMembers(p0) }
    }

    override fun addUserToGroup(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.addUserToGroup(p0, p1, p2) }
    }

    override fun removeUserFromGroup(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.removeUserFromGroup(p0, p1, p2) }
    }

    override fun isUserInGroup(p0: String?, p1: String?): Boolean {
        return remapException { delegate.isUserInGroup(p0, p1) }
    }

    override fun findAll(): MutableList<UserGroup> {
        return remapException { delegate.findAll() }
    }

    override fun find(p0: String?): UserGroup? {
        return remapException { delegate.find(p0) }
    }

    override fun findByName(p0: String?): UserGroup? {
        return remapException { delegate.findByName(p0) }
    }

    override fun findWhere(p0: String?): MutableList<UserGroup>? {
        return remapException { delegate.findWhere(p0) }
    }

    override fun removeUserGroup(p0: UserGroup?) {
        return remapException { delegate.removeUserGroup(p0) }
    }

    override fun removeUserGroup(p0: String?) {
        return remapException { delegate.removeUserGroup(p0) }
    }

    override fun findUserGroupsForUser(p0: String?): MutableList<UserGroup> {
        return remapException { delegate.findUserGroupsForUser(p0) }
    }
}

interface StorageDataObjectAO {
    /*
    fun findByCollectionNameAndDataName(var1: String, var2: String): DataObject
    fun instanceIRODSFileForPath(var1: String): IRODSFile
    fun addAVUMetadata(var1: String, var2: AvuData)
    fun findMetadataValuesForDataObjectUsingAVUQuery(var1: List<AVUQueryElement>, var2: String, var3: String): List<MetaDataAndDomainData>
    fun findMetadataValuesForDataObjectUsingAVUQuery(var1: List<AVUQueryElement>, var2: String, var3: String, var4: Boolean): List<MetaDataAndDomainData>
    fun findMetadataValuesForDataObjectUsingAVUQuery(var1: List<AVUQueryElement>, var2: String): List<MetaDataAndDomainData>
    fun findMetadataValuesByMetadataQuery(var1: List<AVUQueryElement>): List<MetaDataAndDomainData>
    fun findMetadataValuesByMetadataQuery(var1: List<AVUQueryElement>, var2: Int): List<MetaDataAndDomainData>
    fun findMetadataValuesByMetadataQuery(var1: List<AVUQueryElement>, var2: Int, var3: Boolean): List<MetaDataAndDomainData>
    fun findDomainByMetadataQuery(var1: List<AVUQueryElement>): List<DataObject>
    fun findDomainByMetadataQuery(var1: List<AVUQueryElement>, var2: Int): List<DataObject>
    fun findDomainByMetadataQuery(var1: List<AVUQueryElement>, var2: Int, var3: Boolean): List<DataObject>
    fun findMetadataValuesForDataObject(var1: String, var2: String): List<MetaDataAndDomainData>
    fun replicateIrodsDataObject(var1: String, var2: String)
    fun getResourcesForDataObject(var1: String, var2: String): List<Resource>
    fun computeMD5ChecksumOnDataObject(var1: IRODSFile): String
    fun replicateIrodsDataObjectToAllResourcesInResourceGroup(var1: String, var2: String)
    fun deleteAVUMetadata(var1: String, var2: AvuData)
    fun findByAbsolutePath(var1: String): DataObject
    fun setAccessPermissionRead(var1: String, var2: String, var3: String)
    fun setAccessPermissionWrite(var1: String, var2: String, var3: String)
    fun setAccessPermissionOwn(var1: String, var2: String, var3: String)
    fun removeAccessPermissionsForUser(var1: String, var2: String, var3: String)
    fun modifyAvuValueBasedOnGivenAttributeAndUnit(var1: String, var2: AvuData)
    fun modifyAVUMetadata(var1: String, var2: AvuData, var3: AvuData)
    fun modifyAVUMetadata(var1: String, var2: String, var3: AvuData, var4: AvuData)
    fun addAVUMetadata(var1: String, var2: String, var3: AvuData)
    fun listPermissionsForDataObject(var1: String, var2: String): List<UserFilePermission>
    fun getPermissionForDataObjectForUserName(var1: String, var2: String, var3: String): UserFilePermission
    fun getPermissionForDataObjectForUserName(var1: String, var2: String): UserFilePermission
    fun setAccessPermissionReadInAdminMode(var1: String, var2: String, var3: String)
    fun setAccessPermissionWriteInAdminMode(var1: String, var2: String, var3: String)
    fun setAccessPermissionOwnInAdminMode(var1: String, var2: String, var3: String)
    fun removeAccessPermissionsForUserInAdminMode(var1: String, var2: String, var3: String)
    fun listFileResources(var1: String): List<Resource>
    fun findGivenObjStat(var1: ObjStat): DataObject
    fun findById(var1: Int): DataObject
    fun listReplicationsForFileInResGroup(var1: String, var2: String, var3: String): List<DataObject>
    fun getTotalNumberOfReplsForDataObject(var1: String, var2: String): Int
    fun getTotalNumberOfReplsInResourceGroupForDataObject(var1: String, var2: String, var3: String): Int
    fun trimDataObjectReplicas(var1: String, var2: String, var3: String, var4: Int, var5: Int, var6: Boolean)
    fun listReplicationsForFile(var1: String, var2: String): List<DataObject>
    fun replicateIrodsDataObjectAsynchronously(var1: String, var2: String, var3: String, var4: Int)
    fun computeSHA1ChecksumOfIrodsFileByReadingDataFromStream(var1: String): ByteArray
    fun findMetadataValueForDataObjectById(var1: ObjStat, var2: Int): MetaDataAndDomainData
    fun findMetadataValueForDataObjectById(var1: String, var2: Int): MetaDataAndDomainData
    fun computeChecksumOnDataObject(var1: IRODSFile): ChecksumValue
    fun retrieveRestartInfoIfAvailable(var1: FileRestartInfo.RestartType, var2: String): FileRestartInfo
    */

    fun getPermissionForDataObject(absolutePath: String, username: String, zone: String): Result<FilePermissionEnum>
    fun verifyChecksumBetweenLocalAndIrods(irodsFile: IRODSFile, localFile: File): Result<Boolean>
    fun listPermissionsForDataObject(absolutePath: String): Result<List<UserFilePermission>>
    fun setAccessPermission(zone: String, absolutePath: String, username: String,
                            permission: FilePermissionEnum): Result<Unit>


    fun addBulkAVUMetadataToDataObject(absolutePath: String, dataToAdd: List<AvuData>): Result<List<BulkAVUOperationResponse>>
    fun deleteBulkAVUMetadataFromDataObject(absolutePath: String, dataToDelete: List<AvuData>): Result<List<BulkAVUOperationResponse>>
    fun deleteAllAVUForDataObject(absolutePath: String): Result<Unit>


    fun findMetadataValuesForDataObject(file: IRODSFile): Result<List<MetaDataAndDomainData>>
    fun findMetadataValuesForDataObject(absolutePath: String): Result<List<MetaDataAndDomainData>>
}

class DataObjectsWrapper(private val delegate: DataObjectAO) : StorageDataObjectAO {
    override fun findMetadataValuesForDataObject(absolutePath: String): Result<List<MetaDataAndDomainData>> {
        return remapExceptionToResult { delegate.findMetadataValuesForDataObject(absolutePath) }
    }

    override fun findMetadataValuesForDataObject(file: IRODSFile): Result<List<MetaDataAndDomainData>> {
        return remapExceptionToResult { delegate.findMetadataValuesForDataObject(file) }
    }

    override fun addBulkAVUMetadataToDataObject(absolutePath: String, dataToAdd: List<AvuData>): Result<List<BulkAVUOperationResponse>> {
        return remapExceptionToResult { delegate.addBulkAVUMetadataToDataObject(absolutePath, dataToAdd) }
    }

    override fun deleteBulkAVUMetadataFromDataObject(absolutePath: String, dataToDelete: List<AvuData>): Result<List<BulkAVUOperationResponse>> {
        return remapExceptionToResult { delegate.deleteBulkAVUMetadataFromDataObject(absolutePath, dataToDelete) }
    }

    override fun deleteAllAVUForDataObject(absolutePath: String): Result<Unit> {
        return remapExceptionToResult { delegate.deleteAllAVUForDataObject(absolutePath) }
    }

    override fun getPermissionForDataObject(absolutePath: String, username: String, zone: String):
            Result<FilePermissionEnum> {
        return remapExceptionToResult { delegate.getPermissionForDataObject(absolutePath, username, zone) }
    }

    override fun verifyChecksumBetweenLocalAndIrods(irodsFile: IRODSFile, localFile: File): Result<Boolean> {
        return remapExceptionToResult { delegate.verifyChecksumBetweenLocalAndIrods(irodsFile, localFile) }
    }

    override fun listPermissionsForDataObject(absolutePath: String): Result<List<UserFilePermission>> {
        return remapExceptionToResult { delegate.listPermissionsForDataObject(absolutePath) }
    }

    override fun setAccessPermission(zone: String, absolutePath: String, username: String,
                                     permission: FilePermissionEnum): Result<Unit> {
        return remapExceptionToResult { delegate.setAccessPermission(zone, absolutePath, username, permission) }
    }

}

class CollectionsAndObjectSearchWrapper(private val delegate: CollectionAndDataObjectListAndSearchAO) : CollectionAndDataObjectListAndSearchAO by delegate {
    override fun countCollectionsUnderPath(p0: String?): Int {
        return remapException { delegate.countCollectionsUnderPath(p0) }
    }

    override fun getFullObjectForType(p0: String?): Any {
        return remapException { delegate.getFullObjectForType(p0) }
    }

    override fun listDataObjectsAndCollectionsUnderPathWithPermissions(p0: String?): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.listDataObjectsAndCollectionsUnderPathWithPermissions(p0) }
    }

    override fun retrieveObjectStatForPathAndDataObjectName(p0: String?, p1: String?): ObjStat {
        return remapException { delegate.retrieveObjectStatForPathAndDataObjectName(p0, p1) }
    }

    override fun listCollectionsUnderPath(p0: String?, p1: Int): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.listCollectionsUnderPath(p0, p1) }
    }

    override fun listDataObjectsAndCollectionsUnderPathProducingPagingAwareCollectionListing(p0: String?): PagingAwareCollectionListing {
        return remapException { delegate.listDataObjectsAndCollectionsUnderPathProducingPagingAwareCollectionListing(p0) }
    }

    override fun searchCollectionsAndDataObjectsBasedOnName(p0: String?): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.searchCollectionsAndDataObjectsBasedOnName(p0) }
    }

    override fun listCollectionsUnderPathWithPermissions(p0: String?, p1: Int): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.listCollectionsUnderPathWithPermissions(p0, p1) }
    }

    override fun countDataObjectsAndCollectionsUnderPath(p0: String?): Int {
        return remapException { delegate.countDataObjectsAndCollectionsUnderPath(p0) }
    }

    override fun countDataObjectsUnderPath(p0: String?): Int {
        return remapException { delegate.countDataObjectsUnderPath(p0) }
    }

    override fun retrieveObjectStatForPathWithHeuristicPathGuessing(p0: String?): ObjStat {
        return remapException { delegate.retrieveObjectStatForPathWithHeuristicPathGuessing(p0) }
    }

    override fun listDataObjectsUnderPath(p0: String?, p1: Int): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.listDataObjectsUnderPath(p0, p1) }
    }

    override fun searchDataObjectsBasedOnName(p0: String?): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.searchDataObjectsBasedOnName(p0) }
    }

    override fun searchDataObjectsBasedOnName(p0: String?, p1: Int): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.searchDataObjectsBasedOnName(p0) }
    }

    override fun getCollectionAndDataObjectListingEntryAtGivenAbsolutePath(p0: String?): CollectionAndDataObjectListingEntry {
        return remapException { delegate.getCollectionAndDataObjectListingEntryAtGivenAbsolutePath(p0) }
    }

    override fun listDataObjectsUnderPathWithPermissions(p0: String?, p1: Int): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.listDataObjectsUnderPathWithPermissions(p0, p1) }
    }

    override fun searchCollectionsBasedOnName(p0: String?, p1: Int): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.searchCollectionsBasedOnName(p0) }
    }

    override fun searchCollectionsBasedOnName(p0: String?): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.searchCollectionsBasedOnName(p0) }
    }

    override fun listDataObjectsAndCollectionsUnderPath(p0: ObjStat?): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.listDataObjectsAndCollectionsUnderPath(p0) }
    }

    override fun listDataObjectsAndCollectionsUnderPath(p0: String?): MutableList<CollectionAndDataObjectListingEntry> {
        return remapException { delegate.listDataObjectsAndCollectionsUnderPath(p0) }
    }

    override fun retrieveObjectStatForPath(p0: String?): ObjStat {
        return remapException { delegate.retrieveObjectStatForPath(p0) }
    }
}

class DataTransferWrapper(private val delegate: DataTransferOperations) : DataTransferOperations by delegate {
    override fun physicalMove(p0: String?, p1: String?) {
        return remapException { delegate.physicalMove(p0, p1) }
    }

    override fun copy(p0: IRODSFile?, p1: IRODSFile?, p2: TransferStatusCallbackListener?, p3: TransferControlBlock?) {
        return remapException { delegate.copy(p0, p1, p2, p3) }
    }

    @Deprecated("Deprecated in Java")
    override fun copy(p0: String?, p1: String?, p2: String?, p3: TransferStatusCallbackListener?, p4: Boolean, p5: TransferControlBlock?) {
        return remapException {
            @Suppress("DEPRECATION")
            delegate.copy(p0, p1, p2, p3, p4, p5)
        }
    }

    override fun copy(p0: String?, p1: String?, p2: String?, p3: TransferStatusCallbackListener?, p4: TransferControlBlock?) {
        return remapException { delegate.copy(p0, p1, p2, p3, p4) }
    }

    override fun getOperation(p0: String?, p1: String?, p2: String?, p3: TransferStatusCallbackListener?, p4: TransferControlBlock?) {
        return remapException { delegate.getOperation(p0, p1, p2, p3, p4) }
    }

    override fun getOperation(p0: IRODSFile?, p1: File?, p2: TransferStatusCallbackListener?, p3: TransferControlBlock?) {
        return remapException { delegate.getOperation(p0, p1, p2, p3) }
    }

    override fun replicate(p0: String?, p1: String?, p2: TransferStatusCallbackListener?, p3: TransferControlBlock?) {
        return remapException { delegate.replicate(p0, p1, p2, p3) }
    }

    override fun move(p0: String?, p1: String?) {
        return remapException { delegate.move(p0, p1) }
    }

    override fun move(p0: IRODSFile?, p1: IRODSFile?) {
        return remapException { delegate.move(p0, p1) }
    }

    override fun putOperation(p0: String?, p1: String?, p2: String?, p3: TransferStatusCallbackListener?, p4: TransferControlBlock?) {
        return remapException { delegate.putOperation(p0, p1, p2, p3, p4) }
    }

    override fun putOperation(p0: File?, p1: IRODSFile?, p2: TransferStatusCallbackListener?, p3: TransferControlBlock?) {
        return remapException { delegate.putOperation(p0, p1, p2, p3) }
    }
}