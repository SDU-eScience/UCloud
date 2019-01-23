package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class FileLookupTest{

    @Test
    fun `test file lookup`() {
        val mirco = initializeMicro()
        val cloud = mirco.authenticatedCloud
        val fileLookUp = FileLookupService(cloud)

        CloudMock.mockCallSuccess(
            AuthDescriptions,
            {AuthDescriptions.tokenExtension},
            TokenExtensionResponse("token", null, null)
        )

        CloudMock.mockCallSuccess(
            FileDescriptions,
            {FileDescriptions.stat},
            StorageFile(
                FileType.FILE,
                "path/to/file",
                1234567,
                12345678,
                "user",
                1234,
                listOf(AccessEntry("entity", true, setOf(AccessRight.EXECUTE))),
                false,
                SensitivityLevel.PRIVATE,
                false,
                emptySet(),
                "1"
            )
        )

        val result = runBlocking {
            fileLookUp.lookupFile("path/to/file", "token", null)
        }

        assertEquals("1", result.fileId)
    }
}
