package dk.sdu.cloud.file.trash

import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.StorageFileImpl

internal val storageFile: StorageFileImpl = StorageFileImpl(
    fileType = FileType.FILE,
    path = "path",
    createdAt = 10000,
    modifiedAt = 20000,
    ownerName = "Owner",
    size = 1234,
    acl = listOf(AccessEntry("entity", true, setOf(AccessRight.EXECUTE))),
    sensitivityLevel = SensitivityLevel.PRIVATE,
    link = false,
    annotations = setOf("P"),
    fileId = "ID",
    creator = "Owner"
)

