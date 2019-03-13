package dk.sdu.cloud.file.stats

import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile

internal val storageFile = StorageFile(
    FileType.FILE,
    "path",
    20000,
    10000,
    "owner",
    123456,
    listOf(AccessEntry("entity", true, setOf(AccessRight.READ))),
    SensitivityLevel.PRIVATE,
    false,
    setOf("P"),
    "id",
    "creator",
    SensitivityLevel.PRIVATE
)

internal val storageFile2 = StorageFile(
    FileType.FILE,
    "path",
    20000,
    10000,
    "owner",
    123456,
    listOf(AccessEntry("entity", true, setOf(AccessRight.READ))),
    SensitivityLevel.PRIVATE,
    false,
    setOf("P"),
    "id2",
    "creator",
    SensitivityLevel.PRIVATE
)
