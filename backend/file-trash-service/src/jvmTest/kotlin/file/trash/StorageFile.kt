package dk.sdu.cloud.file.trash

import dk.sdu.cloud.file.api.*

internal val storageFile: StorageFileImpl = StorageFileImpl(
    fileTypeOrNull = FileType.FILE,
    pathOrNull = "path",
    createdAtOrNull = 10000,
    modifiedAtOrNull = 20000,
    ownerNameOrNull = "Owner",
    sizeOrNull = 1234,
    aclOrNull = listOf(AccessEntry(ACLEntity.User("entity"), setOf(AccessRight.READ))),
    sensitivityLevelOrNull = SensitivityLevel.PRIVATE,
    ownSensitivityLevelOrNull = SensitivityLevel.PRIVATE
)

