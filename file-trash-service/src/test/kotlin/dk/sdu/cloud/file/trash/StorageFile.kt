package dk.sdu.cloud.file.trash

import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile

internal val storageFile = StorageFile(
    FileType.FILE,
    "path",
    10000,
    20000,
    "Owner",
    1234,
    listOf(AccessEntry("entity", true, setOf(AccessRight.EXECUTE))),
    false,
    SensitivityLevel.PRIVATE,
    false,
    setOf("P"),
    "ID"
)

