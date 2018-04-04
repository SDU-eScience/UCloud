package dk.sdu.cloud.storage.services.ext.irods

import dk.sdu.cloud.service.GuardedInputStream
import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.services.ext.*
import org.irods.jargon.core.exception.DataNotFoundException
import org.irods.jargon.core.exception.DuplicateDataException
import org.irods.jargon.core.exception.InvalidUserException
import org.irods.jargon.core.exception.JargonException
import org.irods.jargon.core.protovalues.FilePermissionEnum
import org.irods.jargon.core.protovalues.UserTypeEnum
import org.irods.jargon.core.pub.domain.AvuData
import org.irods.jargon.core.pub.domain.ObjStat
import org.irods.jargon.core.pub.domain.UserFilePermission
import org.irods.jargon.core.pub.domain.UserGroup
import org.irods.jargon.core.pub.io.IRODSFile
import org.irods.jargon.core.query.*
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry.ObjectType.COLLECTION
import org.irods.jargon.core.query.RodsGenQueryEnum.*
import org.irods.jargon.core.transfer.TransferStatus
import org.irods.jargon.core.transfer.TransferStatusCallbackListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class IRodsPathOperations(override val services: IRodsAccountServices) : PathOperations, IRodsOperationService {
    override val localRoot: StoragePath = StoragePath("/", services.connectionInformation.zone)
    override val homeDirectory: StoragePath = StoragePath("/home/${services.account.userName}", services.account.zone)

    override fun parseAbsolute(absolutePath: String, isMissingZone: Boolean): StoragePath {
        if (!absolutePath.startsWith("/")) throw IllegalArgumentException("Invalid iRODS path")
        val components = absolutePath.split("/").filter { it.isNotBlank() }
        return if (isMissingZone) {
            StoragePath(absolutePath, services.connectionInformation.zone)
        } else {
            StoragePath('/' + components.takeLast(components.size - 1).joinToString("/"), components.first())
        }
    }

}

class IRodsFileOperations(
    override val services: IRodsAccountServices
) : FileOperations, IRodsOperationService {
    override val usesTrashCan: Boolean = true

    companion object {
        const val BUFFER_SIZE = 1024 * 4096
    }

    override fun createDirectory(path: StoragePath, recursive: Boolean) {
        services.fileSystem.mkdir(path.toIRods(), recursive)
    }

    override fun put(path: StoragePath, source: InputStream) {
        source.transferTo(services.files.instanceIRODSFileOutputStream(path.toIRods()))
    }

    override fun put(path: StoragePath, localFile: File) {
        val callback = transferCallback()
        services.dataTransfer.putOperation(localFile, path.toIRods(), callback, transferControlBlock())
        val caughtException = callback.caughtException
        if (caughtException != null) {
            throw remapException(caughtException)
        }
    }

    override fun get(path: StoragePath, localFile: File) {
        val callback = transferCallback()
        services.dataTransfer.getOperation(path.toIRods(), localFile, callback, transferControlBlock())
        val caughtException = callback.caughtException
        if (caughtException != null) {
            throw remapException(caughtException)
        }
    }

    override fun bundlePut(path: StoragePath, source: ZipInputStream) {
        source.use { ins ->
            var currentEntry: ZipEntry? = ins.nextEntry
            while (currentEntry != null) {
                val entry = currentEntry
                val outputPath = path.pushRelative(entry.name)
                val entryStream = GuardedInputStream(ins)
                entryStream.transferTo(services.files.instanceIRODSFileOutputStream(outputPath.toIRods()))
                ins.closeEntry()
                currentEntry = ins.nextEntry
            }
        }
    }

    override fun get(path: StoragePath, output: OutputStream) {
        services.files.instanceIRODSFileInputStream(path.toIRods()).transferTo(output)
    }

    override fun get(path: StoragePath): InputStream {
        return services.files.instanceIRODSFileInputStream(path.toIRods())
    }

    private fun InputStream.transferTo(output: OutputStream, bufferSize: Int = BUFFER_SIZE) {
        output.use { out ->
            this.use { ins ->
                val buffer = ByteArray(bufferSize)
                var hasMoreData = true
                while (hasMoreData) {
                    var ptr = 0
                    while (ptr < buffer.size && hasMoreData) {
                        val read = ins.read(buffer, ptr, buffer.size - ptr)
                        if (read <= 0) {
                            hasMoreData = false
                            break
                        }
                        ptr += read
                    }
                    out.write(buffer, 0, ptr)
                }
            }
        }
    }

    override fun delete(path: StoragePath, recursive: Boolean) {
        val file = path.toIRods()
        if (file.isDirectory) {
            if (recursive) {
                services.fileSystem.directoryDeleteNoForce(file)
            } else {
                services.fileSystem.directoryDeleteForce(file)
            }
        } else {
            file.delete()
        }
    }

    override fun move(from: StoragePath, to: StoragePath) {
        val origFile = from.toIRods()
        val newFile = to.toIRods()
        if (origFile.isDirectory) {
            services.fileSystem.renameDirectory(origFile, newFile)
        } else {
            services.fileSystem.renameFile(origFile, newFile)
        }
    }

    override fun copy(from: StoragePath, to: StoragePath) {
        val callback = transferCallback()
        services.dataTransfer.copy(from.toIRods(), to.toIRods(), callback, transferControlBlock())
        val caughtException = callback.caughtException
        if (caughtException != null) {
            throw remapException(caughtException)
        }
    }

    private fun transferControlBlock() = services.dataTransfer.buildDefaultTransferControlBlockBasedOnJargonProperties()

    class OverwriteAndSaveExceptionCallbackListener : TransferStatusCallbackListener {
        var caughtException: Throwable? = null
            private set

        override fun transferAsksWhetherToForceOperation(
            p0: String?,
            p1: Boolean
        ): TransferStatusCallbackListener.CallbackResponse {
            return TransferStatusCallbackListener.CallbackResponse.YES_FOR_ALL
        }

        override fun overallStatusCallback(p0: TransferStatus?) {}

        override fun statusCallback(status: TransferStatus?): TransferStatusCallbackListener.FileStatusCallbackResponse {
            val exception = status?.transferException
            if (exception != null) {
                caughtException = exception
                // This doesn't do what we want it to do. The client of this class will need to manually rethrow the
                // caught exception (hence why we save it here)
            }
            return TransferStatusCallbackListener.FileStatusCallbackResponse.CONTINUE
        }

    }

    private fun transferCallback() = OverwriteAndSaveExceptionCallbackListener()

}

class IRodsMetadataOperations(
    override val services: IRodsAccountServices,
    private val fileQueryOperations: FileQueryOperations
) : MetadataOperations, IRodsOperationService {
    override fun updateMetadata(
        path: StoragePath, newOrUpdatesAttributes: Metadata,
        attributesToDeleteIfExists: Metadata
    ) {
        val absolutePath = path.toIRodsAbsolute()
        val stat = fileQueryOperations.stat(path)

        when (stat.type) {
            FileType.FILE -> {
                if (newOrUpdatesAttributes.isNotEmpty()) {
                    services.dataObjects.addBulkAVUMetadataToDataObject(
                        absolutePath,
                        newOrUpdatesAttributes.map { it.toIRods() }.toMutableList()
                    )
                }

                if (attributesToDeleteIfExists.isNotEmpty()) {
                    services.dataObjects.deleteBulkAVUMetadataFromDataObject(
                        absolutePath,
                        attributesToDeleteIfExists.map { it.toIRods() }.toMutableList()
                    )
                }
            }

            FileType.DIRECTORY -> {
                if (newOrUpdatesAttributes.isNotEmpty()) {
                    services.collections.addBulkAVUMetadataToCollection(
                        absolutePath,
                        newOrUpdatesAttributes.map { it.toIRods() }.toMutableList()
                    )
                }

                if (attributesToDeleteIfExists.isNotEmpty()) {
                    services.collections.deleteBulkAVUMetadataFromCollection(
                        absolutePath,
                        attributesToDeleteIfExists.map { it.toIRods() }.toMutableList()
                    )
                }
            }
        }
    }

    override fun removeAllMetadata(path: StoragePath) {
        val absolutePath = path.toIRodsAbsolute()
        val stat = fileQueryOperations.stat(path)
        when (stat.type) {
            FileType.FILE -> services.dataObjects.deleteAllAVUForDataObject(absolutePath)
            FileType.DIRECTORY -> services.collections.deleteAllAVUMetadata(absolutePath)
        }
    }
}

class IRodsAccessControlOperations(
    override val services: IRodsAccountServices
) : AccessControlOperations,
    IRodsOperationService {
    override fun updateACL(path: StoragePath, rights: AccessControlList, recursive: Boolean) {
        val localZone = services.account.zone

        for (it in rights) {
            // TODO Clean this up
            val entityName: String
            val zone: String
            if (it.entity.name.contains('#')) {
                val split = it.entity.name.split('#')
                if (split.size != 2) {
                    throw IllegalArgumentException("Invalid entity name '${it.entity.name}'")
                }
                entityName = split[0]
                zone = split[1]
            } else {
                entityName = it.entity.name
                zone = localZone
            }

            services.dataObjects.setAccessPermission(
                zone,
                path.toIRodsAbsolute(),
                entityName,
                it.right.toIRods()
            )
        }
    }

    override fun listAt(path: StoragePath): AccessControlList {
        val listPermissionsForDataObject = services.dataObjects.listPermissionsForDataObject(path.toIRodsAbsolute())
        return listPermissionsForDataObject.map { it.toStorage() }
    }

    override fun getMyPermissionAt(path: StoragePath): AccessRight {
        val connectedUser = with(services.account) { User("$userName#zone", userName, zone) }

        return services.dataObjects.getPermissionForDataObject(
            path.toIRodsAbsolute(),
            connectedUser.displayName,
            connectedUser.zone
        ).toStorage()
    }
}

inline fun <T> doTime(name: String, log: Logger = LoggerFactory.getLogger("Timer"), block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()
    log.debug("Timing for $name took ${System.currentTimeMillis() - start} ms")
    return result
}

class IRodsFileQueryOperations(
    val connectedUser: User,
    val paths: PathOperations,
    override val services: IRodsAccountServices
) : FileQueryOperations, IRodsOperationService {
    private fun IRODSGenQueryBuilder.select(vararg columns: RodsGenQueryEnum) {
        columns.forEach { addSelectAsGenQueryValue(it) }
    }

    private fun IRODSGenQueryBuilder.where(column: RodsGenQueryEnum, op: QueryConditionOperators, value: Long) {
        addConditionAsGenQueryField(column, op, value)
    }

    private fun IRODSGenQueryBuilder.where(column: RodsGenQueryEnum, op: QueryConditionOperators, value: Int) {
        addConditionAsGenQueryField(column, op, value)
    }

    private fun IRODSGenQueryBuilder.where(column: RodsGenQueryEnum, op: QueryConditionOperators, value: String) {
        addConditionAsGenQueryField(column, op, value)
    }

    private inline operator fun <reified T> IRODSQueryResultRow.get(column: RodsGenQueryEnum): T {
        when (T::class.java) {
            Date::class.java -> {
                return this.getColumnAsDateOrNull(column.getName())!! as T
            }

            Int::class.java, java.lang.Integer::class.java -> {
                return this.getColumn(column.getName()).toInt() as T
            }

            Long::class.java, java.lang.Long::class.java -> {
                return this.getColumn(column.getName()).toLong() as T
            }

            String::class.java -> {
                return this.getColumn(column.getName()) as T
            }

            else -> {
                throw IllegalArgumentException("Invalid return type: ${T::class.java}")
            }
        }
    }

    private fun irodsQuery(
        desiredResults: Int = 1024 * 256,
        body: IRODSGenQueryBuilder.() -> Unit
    ): IRODSQueryResultSet {
        return IRODSGenQueryBuilder(true, null).apply(body).exportIRODSQueryFromBuilder(desiredResults).let {
            services.queryExecutor.executeIRODSQueryWithPaging(it, 0)
        }
    }

    private fun mapAccessRight(value: Int) = when (value) {
        1050 -> AccessRight.READ
        1200 -> AccessRight.OWN
        1120 -> AccessRight.READ_WRITE
        else -> AccessRight.NONE
    }

    override fun treeAt(path: StoragePath, modifiedAfter: Long?): List<InternalFile> {
        val directories = irodsQuery {
            select(
                COL_COLL_PARENT_NAME,
                COL_COLL_NAME,
                COL_COLL_MODIFY_TIME,

                COL_USER_NAME,
                COL_COLL_ACCESS_USER_ID,
                COL_COLL_ACCESS_TYPE
            )

            where(COL_COLL_PARENT_NAME, QueryConditionOperators.LIKE, "${path.toIRodsAbsolute()}%")
            if (modifiedAfter != null) {
                where(COL_COLL_MODIFY_TIME, QueryConditionOperators.GREATER_THAN, "0${modifiedAfter / 1000L}")
            }
        }.results.mapNotNull { it ->
            val parent: String = it[COL_COLL_PARENT_NAME]
            val name: String = it[COL_COLL_NAME]
            val modifiedAt: Date = it[COL_COLL_MODIFY_TIME]
            val username: String = it[COL_USER_NAME]
            val accessType: Int = it[COL_COLL_ACCESS_TYPE]

            if (username != connectedUser.displayName) return@mapNotNull null

            InternalFile(name, parent, true, modifiedAt.time, mapAccessRight(accessType))
        }.distinctBy { Pair(it.parent, it.fileName) }

        val files = irodsQuery {
            select(
                COL_COLL_NAME,
                COL_DATA_NAME,
                COL_D_MODIFY_TIME,
                COL_D_DATA_PATH,

                COL_USER_NAME,
                COL_DATA_ACCESS_USER_ID,
                COL_DATA_ACCESS_TYPE
            )

            where(COL_COLL_NAME, QueryConditionOperators.LIKE, "${path.toIRodsAbsolute()}%")
            if (modifiedAfter != null) {
                where(COL_D_MODIFY_TIME, QueryConditionOperators.GREATER_THAN, "0${modifiedAfter / 1000L}")
            }
        }.results.mapNotNull { it ->
            val parent: String = it[COL_COLL_NAME]
            val name: String = it[COL_DATA_NAME]
            val modifiedAt: Date = it[COL_D_MODIFY_TIME]
            val physicalPath: String = it[COL_D_DATA_PATH]
            val username: String = it[COL_USER_NAME]
            val accessType: Int = it[COL_DATA_ACCESS_TYPE]

            if (username != connectedUser.displayName) return@mapNotNull null

            InternalFile(name, parent, false, modifiedAt.time, mapAccessRight(accessType), physicalPath)
        }.distinctBy { Pair(it.parent, it.fileName) }

        return directories + files
    }

    override fun listAt(path: StoragePath): List<StorageFile> {
        val effectiveAbsolutePath = path.toIRodsAbsolute()

        val mappedResults = HashMap<String, StorageFile>()

        run {
            // Search for all collections
            val query = IRODSGenQueryBuilder(true, null).apply {
                // The parent truly doesn't matter, as the entry itself contains the full name. That is stupid.
                addSelectAsGenQueryValue(COL_COLL_PARENT_NAME)
                addSelectAsGenQueryValue(COL_COLL_NAME)
                addSelectAsGenQueryValue(COL_COLL_CREATE_TIME)
                addSelectAsGenQueryValue(COL_COLL_MODIFY_TIME)
                addSelectAsGenQueryValue(COL_COLL_ID)

                addSelectAsGenQueryValue(COL_COLL_OWNER_NAME)

                addSelectAsGenQueryValue(COL_COLL_ACCESS_TYPE)
                addSelectAsGenQueryValue(COL_COLL_ACCESS_USER_NAME)
                addSelectAsGenQueryValue(COL_COLL_ACCESS_USER_ZONE)

                addConditionAsGenQueryField(COL_COLL_PARENT_NAME, QueryConditionOperators.EQUAL, effectiveAbsolutePath)
            }.exportIRODSQueryFromBuilder(1024 * 32)

            val result = doTime("coll query") {
                services.queryExecutor.executeIRODSQueryWithPaging(query, 0)
            }

            result.results.forEach {
                val pathToEntry = it.getColumn(COL_COLL_NAME.getName())
                val row = mappedResults[pathToEntry] ?: StorageFile(
                    FileType.DIRECTORY,
                    paths.parseAbsolute(pathToEntry),
                    it.getColumnAsDateOrNull(COL_COLL_CREATE_TIME.getName())?.time ?: 0,
                    it.getColumnAsDateOrNull(COL_COLL_MODIFY_TIME.getName())?.time ?: 0,
                    it[COL_COLL_OWNER_NAME],
                    0,
                    arrayListOf(),
                    false,
                    SensitivityLevel.CONFIDENTIAL
                )

                (row.acl as MutableList<AccessEntry>).add(
                    AccessEntry(
                        IRodsUser.fromUsernameAndZone(
                            it.getColumn(COL_COLL_ACCESS_USER_NAME.getName()),
                            it.getColumn(COL_COLL_ACCESS_USER_ZONE.getName())
                        ),
                        mapAccessRight(it.getColumnAsIntOrZero(COL_COLL_ACCESS_TYPE.getName()))
                    )
                )

                mappedResults[pathToEntry] = row
            }
        }

        run {
            // Retrieve all files
            val query = IRODSGenQueryBuilder(true, null).apply {
                addSelectAsGenQueryValue(COL_COLL_NAME)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_DATA_NAME)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_D_CREATE_TIME)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_D_MODIFY_TIME)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_D_DATA_ID)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_DATA_SIZE)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_USER_NAME)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_DATA_ACCESS_USER_ID)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_DATA_ACCESS_TYPE)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_USER_TYPE)
                addSelectAsGenQueryValue(RodsGenQueryEnum.COL_USER_ZONE)
                addSelectAsGenQueryValue(COL_D_OWNER_NAME)

                addConditionAsGenQueryField(
                    COL_COLL_NAME, QueryConditionOperators.EQUAL,
                    effectiveAbsolutePath
                )
            }.exportIRODSQueryFromBuilder(1024 * 32)
            val result = doTime("data query") {
                services.queryExecutor.executeIRODSQueryWithPaging(query, 0)
            }

            result.results.forEach {
                val collectionName = it.getColumn(COL_COLL_NAME.getName())
                val dataName = it.getColumn(COL_DATA_NAME.getName())
                val pathToEntry = "$collectionName/$dataName"

                val row = mappedResults[pathToEntry] ?: StorageFile(
                    FileType.FILE,
                    paths.parseAbsolute(pathToEntry),
                    it.getColumnAsDateOrNull(COL_D_CREATE_TIME.getName())?.time ?: 0,
                    it.getColumnAsDateOrNull(COL_D_MODIFY_TIME.getName())?.time ?: 0,
                    it[COL_D_OWNER_NAME],
                    it.getColumnAsLongOrZero(COL_DATA_SIZE.getName()),
                    arrayListOf(),
                    false,
                    SensitivityLevel.CONFIDENTIAL
                )

                (row.acl as MutableList<AccessEntry>).add(
                    AccessEntry(
                        IRodsUser.fromUsernameAndZone(
                            it.getColumn(COL_USER_NAME.getName()),
                            it.getColumn(COL_USER_ZONE.getName())
                        ),
                        mapAccessRight(it.getColumnAsIntOrZero(COL_DATA_ACCESS_TYPE.getName()))
                    )
                )

                mappedResults[pathToEntry] = row
            }
        }

        fun queryMetaAndUpdateRows(type: FileType) {
            log.debug("queryMetaAndUpdateRows(type = $type)")
            val isCollectionQuery = type == FileType.DIRECTORY
            assert(isCollectionQuery || type == FileType.FILE)

            val metaNameColl = if (isCollectionQuery) COL_META_COLL_ATTR_NAME else COL_META_DATA_ATTR_NAME
            val metaValueColl = if (isCollectionQuery) COL_META_COLL_ATTR_VALUE else COL_META_DATA_ATTR_VALUE
            val results = irodsQuery {
                val queryColumns =
                    if (isCollectionQuery) arrayOf(
                        COL_COLL_NAME
                    )
                    else arrayOf(
                        COL_COLL_NAME,
                        COL_DATA_NAME
                    )

                select(*queryColumns)
                select(metaNameColl, metaValueColl)

                if (isCollectionQuery) {
                    where(COL_COLL_PARENT_NAME, QueryConditionOperators.EQUAL, effectiveAbsolutePath)
                } else {
                    where(COL_COLL_NAME, QueryConditionOperators.EQUAL, effectiveAbsolutePath)
                }
            }.results

            log.debug("Retrieved ${results.size} results")

            results.forEach {
                val pathToEntry = if (isCollectionQuery) {
                    it[COL_COLL_NAME]
                } else {
                    val collectionName: String = it[COL_COLL_NAME]
                    val dataName: String = it[COL_DATA_NAME]

                    "$collectionName/$dataName"
                }

                var row = mappedResults[pathToEntry] ?: return@forEach

                val name: String = it[metaNameColl]
                val value: String = it[metaValueColl]

                log.debug("Processing: $name, $value, $pathToEntry")

                when (name) {
                    "sensitivity" -> {
                        val level = try {
                            SensitivityLevel.valueOf(value)
                        } catch (_: Exception) {
                            SensitivityLevel.CONFIDENTIAL
                        }
                        row = row.copy(sensitivityLevel = level)
                        log.debug("New sensitivity: $row")
                    }

                    "favorited_${services.account.userName}" -> {
                        val parsedValue = when (value) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }

                        if (parsedValue != null) {
                            row = row.copy(favorited = parsedValue)
                            log.debug("New favorited: $row")
                        }
                    }
                }

                mappedResults[pathToEntry] = row
            }
        }

        queryMetaAndUpdateRows(FileType.DIRECTORY)
        queryMetaAndUpdateRows(FileType.FILE)

        if (mappedResults.isEmpty()) {
            // TODO This will not fill ACL, sensitivity and favorite fields
            return listOf(stat(path))
        }
        return mappedResults.values.toList()
    }

    override fun statBulk(vararg paths: StoragePath): List<StorageFile?> {
        return paths.map { path ->
            try {
                services.fileSystem.getObjStat(path.toIRodsAbsolute()).toStorage()
            } catch (_: JargonException) {
                null
            } catch (_: StorageException) {
                null
            }
        }
    }

    private fun ObjStat.toStorage(): StorageFile =
        StorageFile(
            if (objectType == COLLECTION) FileType.DIRECTORY else FileType.FILE,
            paths.parseAbsolute(this.absolutePath),
            this.createdAt.time,
            this.modifiedAt.time,
            "${this.ownerName}#${this.ownerZone}",
            this.objSize
        )

    companion object {
        private val log = LoggerFactory.getLogger(IRodsFileQueryOperations::class.java)
    }
}

class IRodsUserOperations(override val services: IRodsAccountServices) : UserOperations, IRodsOperationService {
    override fun modifyMyPassword(currentPassword: String, newPassword: String) {
        services.users.changeAUserPasswordByThatUser(services.account.userName, currentPassword, newPassword)
    }
}

class IRodsGroupOperations(override val services: IRodsAccountServices) : GroupOperations, IRodsOperationService {
    override fun createGroup(name: String) {
        if (name.isEmpty()) throw IllegalArgumentException("name cannot be empty")

        val userGroup = UserGroup().apply {
            userGroupName = name
            zone = services.connectionInformation.zone
        }

        try {
            services.userGroups.addUserGroup(userGroup)
        } catch (_: DuplicateDataException) {
            throw StorageException.Duplicate("Group '$name' already exists!")
        }
    }

    override fun deleteGroup(name: String, force: Boolean) {
        if (name.isEmpty()) throw IllegalArgumentException("name is empty")

        val userGroup = services.userGroups.findByName(name)
        // If not empty
        try {
            val groupMembers = listGroupMembers(name)
            if (!force && groupMembers.isNotEmpty()) {
                throw IllegalArgumentException("Cannot remove non-empty group without force flag")
            }
        } catch (_: StorageException.NotFound) {
            return
        }
        services.userGroups.removeUserGroup(userGroup)
    }

    override fun addUserToGroup(groupName: String, username: String) {
        val user = IRodsUser.parse(services, username)
        try {
            services.userGroups.addUserToGroup(groupName, user.displayName, user.zone)
        } catch (_: StorageException.Duplicate) {
            // Ignored
        }
    }

    override fun removeUserFromGroup(groupName: String, username: String) {
        val user = IRodsUser.parse(services, username)
        if (services.userGroups.findByName(groupName) == null) {
            throw StorageException.NotFound("group", groupName)
        }
        services.userGroups.removeUserFromGroup(groupName, user.displayName, user.zone)
    }

    override fun listGroupMembers(groupName: String): List<User> {
        if (services.userGroups.findByName(groupName) == null) {
            throw StorageException.NotFound("group", groupName)
        }
        return (services.userGroups.listUserGroupMembers(groupName).map { it.toStorage() })
    }
}

private typealias JargonUser = org.irods.jargon.core.pub.domain.User

class IRodsUserAdminOperations(override val services: IRodsAccountServices) : UserAdminOperations, IRodsOperationService {
    override fun createUser(username: String, password: String?, type: UserType) {
        val user = JargonUser().apply {
            name = username
            userType = type.toIRods()
        }

        services.users.addUser(user)
        if (password != null) modifyPassword(user, password)
    }

    override fun deleteUser(username: String) {
        try {
            services.users.deleteUser(username)
        } catch (_: DataNotFoundException) {
        } catch (_: InvalidUserException) {
        }
    }

    override fun modifyPassword(username: String, newPassword: String) {
        val user = services.users.findByName(username)
        return modifyPassword(user, newPassword)
    }

    private fun modifyPassword(user: JargonUser, newPassword: String) {
        services.users.changeAUserPasswordByAnAdmin(user.nameWithZone, newPassword)
    }

    override fun findByUsername(username: String): User =
        services.users.findByName(username).toStorage()
}

interface IRodsOperationService {
    val services: IRodsAccountServices

    fun FilePermissionEnum.toStorage(): AccessRight = when (this) {
        FilePermissionEnum.OWN -> AccessRight.OWN
        FilePermissionEnum.WRITE -> AccessRight.READ_WRITE
        FilePermissionEnum.READ -> AccessRight.READ
        FilePermissionEnum.NULL -> AccessRight.NONE
        else -> AccessRight.NONE
    }

    fun UserFilePermission.toStorage(): AccessEntry =
        AccessEntry(IRodsUser.parse(services, this.nameWithZone), this.filePermissionEnum.toStorage())

    fun MetaDataAndDomainData.toStorage(): MetadataEntry =
        MetadataEntry(this.avuAttribute, this.avuValue)

    fun org.irods.jargon.core.pub.domain.User.toStorage(): User {
        return IRodsUser.parse(services, this.nameWithZone)
    }

    fun StoragePath.toIRods(): IRODSFile = services.files.instanceIRODSFile(this.toIRodsAbsolute())
    fun StoragePath.toIRodsAbsolute(): String = "/$host$path"

    fun AccessRight.toIRods(): FilePermissionEnum = when (this) {
        AccessRight.NONE -> FilePermissionEnum.NONE
        AccessRight.READ -> FilePermissionEnum.READ
        AccessRight.READ_WRITE -> FilePermissionEnum.WRITE
        AccessRight.OWN -> FilePermissionEnum.OWN
    }

    fun UserType.toIRods(): UserTypeEnum = when (this) {
        UserType.USER -> UserTypeEnum.RODS_USER
        UserType.ADMIN -> UserTypeEnum.RODS_ADMIN
        UserType.GROUP_ADMIN -> UserTypeEnum.RODS_GROUP // TODO I think?
    }

    fun MetadataEntry.toIRods(): AvuData = AvuData(this.key, this.value, "")
}
