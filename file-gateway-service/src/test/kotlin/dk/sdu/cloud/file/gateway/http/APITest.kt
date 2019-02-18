package dk.sdu.cloud.file.gateway.http

import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.gateway.api.StorageFileWithMetadata
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.parseSuccessful
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test

class APITest {
    val items = listOf(
        StorageFile(
            fileId = "fileA",
            fileType = FileType.FILE,
            path = "/home/user/f",
            ownerName = "user"
        )
    )

    @Test
    fun `test list at path`(): Unit = withKtorTest(
        setup = {
            val userCloudService = UserCloudService(ClientMock.authenticatedClient)
            val fileAnnotationService: FileAnnotationService = mockk()
            coEvery { fileAnnotationService.annotate(any(), any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                val files = invocation.args[1] as List<StorageFile>
                files.map { StorageFileWithMetadata(it, null) }
            }

            ClientMock.mockCallSuccess(
                FileDescriptions.listAtPath,
                Page(2, 10, 0, items)
            )

            listOf(FileController(userCloudService, fileAnnotationService))
        },

        test = {
            val json = sendRequest(
                HttpMethod.Get,
                "/api/files",
                TestUsers.user,
                params = mapOf(
                    "path" to "/home/user"
                )
            ).parseSuccessful<JsonNode>()

            val itemsNode = json["items"]
            assertThatPropertyEquals(itemsNode, { it.size() }, items.size)
            assertThatPropertyEquals(itemsNode[0], { it["path"].asText() }, items.first().path)
            assertThatProperty(itemsNode[0], { it["favorited"] }, matcher = { it.isNull })
        }
    )
}
