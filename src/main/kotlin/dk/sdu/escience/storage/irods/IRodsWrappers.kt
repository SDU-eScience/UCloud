package dk.sdu.escience.storage.irods

import dk.sdu.escience.storage.NotFoundException
import dk.sdu.escience.storage.PermissionException
import org.irods.jargon.core.checksum.ChecksumValue
import org.irods.jargon.core.exception.DuplicateDataException
import org.irods.jargon.core.exception.InvalidGroupException
import org.irods.jargon.core.exception.JargonException
import org.irods.jargon.core.packinstr.DataObjInp
import org.irods.jargon.core.protovalues.FilePermissionEnum
import org.irods.jargon.core.pub.*
import org.irods.jargon.core.pub.domain.*
import org.irods.jargon.core.pub.io.*
import org.irods.jargon.core.query.AVUQueryElement
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry
import org.irods.jargon.core.query.MetaDataAndDomainData
import org.irods.jargon.core.query.PagingAwareCollectionListing
import org.irods.jargon.core.transfer.FileRestartInfo
import org.irods.jargon.core.transfer.TransferControlBlock
import org.irods.jargon.core.transfer.TransferStatusCallbackListener
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.io.FilenameFilter
import java.net.URI

// Semi-automatically generated code which wraps all iRODS calls with remapException
// Generation done via text-editor macros

private inline fun <T> remapException(call: () -> T): T {
    try {
        return call()
    } catch (ex: FileNotFoundException) {
        throw NotFoundException("object", "Unknown", ex.message ?: "Unknown")
    } catch (ex: org.irods.jargon.core.exception.FileNotFoundException) {
        throw NotFoundException("object", "Unknown", ex.message ?: "Unknown")
    } catch (ex: InvalidGroupException) {
        throw NotFoundException("usergroup", "Unknown", ex.message ?: "Unknown")
    } catch (ex: DuplicateDataException) {
        throw PermissionException("Cannot create new entry - Entry already exists. Cause: ${ex.message}")
    } catch (ex: JargonException) {
        if (ex.cause is FileNotFoundException) {
            throw NotFoundException("object", "Unknown", ex.message ?: "Unknown")
        }
        throw RuntimeException("Exception in iRODS. Cause is unknown.", ex)
    }
}

class UserAOWrapper(private val delegate: UserAO) : UserAO by delegate {
    override fun modifyAVUMetadata(p0: String?, p1: AvuData?) {
        return remapException { delegate.modifyAVUMetadata(p0, p1) }
    }

    override fun updateUser(p0: User?) {
        return remapException { delegate.updateUser(p0) }
    }

    override fun addAVUMetadata(p0: String?, p1: AvuData?) {
        return remapException { delegate.addAVUMetadata(p0, p1) }
    }

    override fun getTemporaryPasswordForConnectedUser(): String {
        return remapException { delegate.temporaryPasswordForConnectedUser }
    }

    override fun updateUserDN(p0: String?, p1: String?) {
        return remapException { delegate.updateUserDN(p0, p1) }
    }

    override fun removeUserDN(p0: String?, p1: String?) {
        return remapException { delegate.removeUserDN(p0, p1) }
    }

    override fun listUserMetadataForUserName(p0: String?): MutableList<AvuData> {
        return remapException { delegate.listUserMetadataForUserName(p0) }
    }

    override fun updateUserInfo(p0: String?, p1: String?) {
        return remapException { delegate.updateUserInfo(p0, p1) }
    }

    override fun findAll(): MutableList<User> {
        return remapException { delegate.findAll() }
    }

    override fun listUserMetadataForUserId(p0: String?): MutableList<AvuData> {
        return remapException { delegate.listUserMetadataForUserId(p0) }
    }

    override fun changeAUserPasswordByAnAdmin(p0: String?, p1: String?) {
        return remapException { delegate.changeAUserPasswordByAnAdmin(p0, p1) }
    }

    override fun getTemporaryPasswordForASpecifiedUser(p0: String?): String {
        return remapException { delegate.getTemporaryPasswordForASpecifiedUser(p0) }
    }

    override fun changeAUserPasswordByThatUser(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.changeAUserPasswordByThatUser(p0, p1, p2) }
    }

    override fun findUserNameLike(p0: String?): MutableList<String> {
        return remapException { delegate.findUserNameLike(p0) }
    }

    override fun deleteAVUMetadata(p0: String?, p1: AvuData?) {
        return remapException { delegate.deleteAVUMetadata(p0, p1) }
    }

    override fun findByName(p0: String?): User {
        return remapException { delegate.findByName(p0) }
    }

    override fun findWhere(p0: String?): MutableList<User> {
        return remapException { delegate.findWhere(p0) }
    }

    override fun findByIdInZone(p0: String?, p1: String?): User {
        return remapException { delegate.findByIdInZone(p0, p1) }
    }

    override fun addUser(p0: User?): User {
        return remapException { delegate.addUser(p0) }
    }

    override fun findById(p0: String?): User {
        return remapException { delegate.findById(p0) }
    }

    override fun deleteUser(p0: String?) {
        return remapException { delegate.deleteUser(p0) }
    }

    override fun retriveUserDNByUserId(p0: String?): String {
        return remapException { delegate.retriveUserDNByUserId(p0) }
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

class DataObjectsWrapper(private val delegate: DataObjectAO) : DataObjectAO by delegate {
    override fun isUserHasAccess(p0: String?, p1: String?): Boolean {
        return remapException { delegate.isUserHasAccess(p0, p1) }
    }

    override fun replicateIrodsDataObjectAsynchronously(p0: String?, p1: String?, p2: String?, p3: Int) {
        return remapException { delegate.replicateIrodsDataObjectAsynchronously(p0, p1, p2, p3) }
    }

    override fun findByCollectionNameAndDataName(p0: String?, p1: String?): DataObject {
        return remapException { delegate.findByCollectionNameAndDataName(p0, p1) }
    }

    override fun setAccessPermissionOwnInAdminMode(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.setAccessPermissionOwnInAdminMode(p0, p1, p2) }
    }

    override fun listFileResources(p0: String?): MutableList<Resource> {
        return remapException { delegate.listFileResources(p0) }
    }

    override fun findMetadataValuesByMetadataQuery(p0: MutableList<AVUQueryElement>?, p1: Int, p2: Boolean): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesByMetadataQuery(p0) }
    }

    override fun findMetadataValuesByMetadataQuery(p0: MutableList<AVUQueryElement>?): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesByMetadataQuery(p0) }
    }

    override fun findMetadataValuesByMetadataQuery(p0: MutableList<AVUQueryElement>?, p1: Int): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesByMetadataQuery(p0) }
    }

    override fun computeChecksumOnDataObject(p0: IRODSFile?): ChecksumValue {
        return remapException { delegate.computeChecksumOnDataObject(p0) }
    }

    override fun replicateIrodsDataObjectToAllResourcesInResourceGroup(p0: String?, p1: String?) {
        return remapException { delegate.replicateIrodsDataObjectToAllResourcesInResourceGroup(p0, p1) }
    }

    override fun listReplicationsForFileInResGroup(p0: String?, p1: String?, p2: String?): MutableList<DataObject> {
        return remapException { delegate.listReplicationsForFileInResGroup(p0, p1, p2) }
    }

    override fun modifyAvuValueBasedOnGivenAttributeAndUnit(p0: String?, p1: AvuData?) {
        return remapException { delegate.modifyAvuValueBasedOnGivenAttributeAndUnit(p0, p1) }
    }

    override fun listReplicationsForFile(p0: String?, p1: String?): MutableList<DataObject> {
        return remapException { delegate.listReplicationsForFile(p0, p1) }
    }

    override fun verifyChecksumBetweenLocalAndIrods(p0: IRODSFile?, p1: File?): Boolean {
        return remapException { delegate.verifyChecksumBetweenLocalAndIrods(p0, p1) }
    }

    override fun setAccessPermissionReadInAdminMode(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.setAccessPermissionReadInAdminMode(p0, p1, p2) }
    }

    override fun deleteBulkAVUMetadataFromDataObject(p0: String?, p1: MutableList<AvuData>?): MutableList<BulkAVUOperationResponse> {
        return remapException { delegate.deleteBulkAVUMetadataFromDataObject(p0, p1) }
    }

    override fun getHostForGetOperation(p0: String?, p1: String?): String {
        return remapException { delegate.getHostForGetOperation(p0, p1) }
    }

    override fun findMetadataValueForDataObjectById(p0: ObjStat?, p1: Int): MetaDataAndDomainData {
        return remapException { delegate.findMetadataValueForDataObjectById(p0, p1) }
    }

    override fun findMetadataValueForDataObjectById(p0: String?, p1: Int): MetaDataAndDomainData {
        return remapException { delegate.findMetadataValueForDataObjectById(p0, p1) }
    }

    override fun findMetadataValuesForDataObjectUsingAVUQuery(p0: MutableList<AVUQueryElement>?, p1: String?, p2: String?): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesForDataObjectUsingAVUQuery(p0, p1, p2) }
    }

    override fun findMetadataValuesForDataObjectUsingAVUQuery(p0: MutableList<AVUQueryElement>?, p1: String?): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesForDataObjectUsingAVUQuery(p0, p1) }
    }

    override fun findMetadataValuesForDataObjectUsingAVUQuery(p0: MutableList<AVUQueryElement>?, p1: String?, p2: String?, p3: Boolean): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesForDataObjectUsingAVUQuery(p0, p1, p2, p3) }
    }

    override fun removeAccessPermissionsForUser(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.removeAccessPermissionsForUser(p0, p1, p2) }
    }

    override fun removeAccessPermissionsForUserInAdminMode(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.removeAccessPermissionsForUserInAdminMode(p0, p1, p2) }
    }

    override fun instanceIRODSFileForPath(p0: String?): IRODSFile {
        return remapException { delegate.instanceIRODSFileForPath(p0) }
    }

    override fun listPermissionsForDataObject(p0: String?, p1: String?): MutableList<UserFilePermission> {
        return remapException { delegate.listPermissionsForDataObject(p0) }
    }

    override fun listPermissionsForDataObject(p0: String?): MutableList<UserFilePermission> {
        return remapException { delegate.listPermissionsForDataObject(p0) }
    }

    override fun findById(p0: Int): DataObject {
        return remapException { delegate.findById(p0) }
    }

    override fun trimDataObjectReplicas(p0: String?, p1: String?, p2: String?, p3: Int, p4: Int, p5: Boolean) {
        return remapException { delegate.trimDataObjectReplicas(p0, p1, p2, p3, p4, p5) }
    }

    override fun setAccessPermissionWriteInAdminMode(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.setAccessPermissionWriteInAdminMode(p0, p1, p2) }
    }

    override fun retrieveRestartInfoIfAvailable(p0: FileRestartInfo.RestartType?, p1: String?): FileRestartInfo {
        return remapException { delegate.retrieveRestartInfoIfAvailable(p0, p1) }
    }

    override fun modifyAVUMetadata(p0: String?, p1: AvuData?, p2: AvuData?) {
        return remapException { delegate.modifyAVUMetadata(p0, p1, p2) }
    }

    override fun modifyAVUMetadata(p0: String?, p1: String?, p2: AvuData?, p3: AvuData?) {
        return remapException { delegate.modifyAVUMetadata(p0, p1, p2, p3) }
    }

    override fun setAccessPermissionOwn(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.setAccessPermissionOwn(p0, p1, p2) }
    }

    override fun getTotalNumberOfReplsForDataObject(p0: String?, p1: String?): Int {
        return remapException { delegate.getTotalNumberOfReplsForDataObject(p0, p1) }
    }

    override fun addAVUMetadata(p0: String?, p1: AvuData?) {
        return remapException { delegate.addAVUMetadata(p0, p1) }
    }

    override fun addAVUMetadata(p0: String?, p1: String?, p2: AvuData?) {
        return remapException { delegate.addAVUMetadata(p0, p1, p2) }
    }

    override fun replicateIrodsDataObject(p0: String?, p1: String?) {
        return remapException { delegate.replicateIrodsDataObject(p0, p1) }
    }

    override fun findByAbsolutePath(p0: String?): DataObject {
        return remapException { delegate.findByAbsolutePath(p0) }
    }

    override fun setAccessPermissionWrite(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.setAccessPermissionWrite(p0, p1, p2) }
    }

    override fun findMetadataValuesForDataObject(p0: String?): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesForDataObject(p0) }
    }

    override fun findMetadataValuesForDataObject(p0: String?, p1: String?): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesForDataObject(p0) }
    }

    override fun findMetadataValuesForDataObject(p0: IRODSFile?): MutableList<MetaDataAndDomainData> {
        return remapException { delegate.findMetadataValuesForDataObject(p0) }
    }

    override fun findDomainByMetadataQuery(p0: MutableList<AVUQueryElement>?, p1: Int): MutableList<DataObject> {
        return remapException { delegate.findDomainByMetadataQuery(p0) }
    }

    override fun findDomainByMetadataQuery(p0: MutableList<AVUQueryElement>?, p1: Int, p2: Boolean): MutableList<DataObject> {
        return remapException { delegate.findDomainByMetadataQuery(p0) }
    }

    override fun findDomainByMetadataQuery(p0: MutableList<AVUQueryElement>?): MutableList<DataObject> {
        return remapException { delegate.findDomainByMetadataQuery(p0) }
    }

    override fun getHostForPutOperation(p0: String?, p1: String?): String {
        return remapException { delegate.getHostForPutOperation(p0, p1) }
    }

    override fun getPermissionForDataObject(p0: String?, p1: String?, p2: String?): FilePermissionEnum {
        return remapException { delegate.getPermissionForDataObject(p0, p1, p2) }
    }

    override fun deleteAllAVUForDataObject(p0: String?) {
        return remapException { delegate.deleteAllAVUForDataObject(p0) }
    }

    override fun setAccessPermission(p0: String?, p1: String?, p2: String?, p3: FilePermissionEnum?) {
        return remapException { delegate.setAccessPermission(p0, p1, p2, p3) }
    }

    override fun getPermissionForDataObjectForUserName(p0: String?, p1: String?, p2: String?): UserFilePermission {
        return remapException { delegate.getPermissionForDataObjectForUserName(p0, p1, p2) }
    }

    override fun getPermissionForDataObjectForUserName(p0: String?, p1: String?): UserFilePermission {
        return remapException { delegate.getPermissionForDataObjectForUserName(p0, p1) }
    }

    override fun addBulkAVUMetadataToDataObject(p0: String?, p1: MutableList<AvuData>?): MutableList<BulkAVUOperationResponse> {
        return remapException { delegate.addBulkAVUMetadataToDataObject(p0, p1) }
    }

    override fun setAccessPermissionRead(p0: String?, p1: String?, p2: String?) {
        return remapException { delegate.setAccessPermissionRead(p0, p1, p2) }
    }

    override fun findGivenObjStat(p0: ObjStat?): DataObject {
        return remapException { delegate.findGivenObjStat(p0) }
    }

    override fun getTotalNumberOfReplsInResourceGroupForDataObject(p0: String?, p1: String?, p2: String?): Int {
        return remapException { delegate.getTotalNumberOfReplsInResourceGroupForDataObject(p0, p1, p2) }
    }

    override fun computeMD5ChecksumOnDataObject(p0: IRODSFile?): String {
        return remapException { delegate.computeMD5ChecksumOnDataObject(p0) }
    }

    override fun deleteAVUMetadata(p0: String?, p1: AvuData?) {
        return remapException { delegate.deleteAVUMetadata(p0, p1) }
    }

    override fun getObjectStatForAbsolutePath(p0: String?): ObjStat {
        return remapException { delegate.getObjectStatForAbsolutePath(p0) }
    }

    override fun computeSHA1ChecksumOfIrodsFileByReadingDataFromStream(p0: String?): ByteArray {
        return remapException { delegate.computeSHA1ChecksumOfIrodsFileByReadingDataFromStream(p0) }
    }

    override fun getResourcesForDataObject(p0: String?, p1: String?): MutableList<Resource> {
        return remapException { delegate.getResourcesForDataObject(p0, p1) }
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

    override fun copy(p0: String?, p1: String?, p2: String?, p3: TransferStatusCallbackListener?, p4: Boolean, p5: TransferControlBlock?) {
        return remapException { delegate.copy(p0, p1, p2, p3, p4, p5) }
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