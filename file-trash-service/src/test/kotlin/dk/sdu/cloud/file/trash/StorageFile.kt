package dk.sdu.cloud.file.trash

import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.StorageFileImpl

internal val storageFile: StorageFileImpl = StorageFileImpl(
    fileTypeOrNull = FileType.FILE,
    pathOrNull = "path",
    createdAtOrNull = 10000,
    modifiedAtOrNull = 20000,
    ownerNameOrNull = "Owner",
    sizeOrNull = 1234,
    aclOrNull = listOf(AccessEntry("entity", true, setOf(AccessRight.EXECUTE))),
    sensitivityLevelOrNull = SensitivityLevel.PRIVATE,
    linkOrNull = false,
    annotationsOrNull = setOf("P"),
    fileIdOrNull = "ID",
    creatorOrNull = "Owner",
    ownSensitivityLevelOrNull = SensitivityLevel.PRIVATE
)

