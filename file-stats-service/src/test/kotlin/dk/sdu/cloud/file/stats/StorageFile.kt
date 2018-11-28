package dk.sdu.cloud.file.stats

import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.stats.api.SearchResult

internal val storageFile = EventMaterializedStorageFile(
    "id",
    "path",
    "owner",
    FileType.FILE,
    Timestamps(
        20000,
        10000,
        20000
    ),
    123456,
    FileChecksum(
        "SHA-2",
        "checksum"
    ),
    false,
    null,
    null,
    setOf("P"),
    SensitivityLevel.PRIVATE
)
