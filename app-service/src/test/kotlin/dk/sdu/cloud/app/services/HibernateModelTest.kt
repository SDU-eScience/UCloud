package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class HibernateModelTest {
    //Job entity is covered in another test
    @Test
    fun `simple create Tag, Tool and Application Entity test`() {
        val normToolDesc = NormalizedToolDescription(
            NameAndVersion("name", "2.2"),
            "container",
            2,
            2,
            SimpleDuration(0, 1, 0),
            listOf("Module1"),
            listOf("Author"),
            "title",
            "description",
            ToolBackend.UDOCKER
        )


        val currentDate = Date()
        val tool = ToolEntity(
            "owner",
            currentDate,
            currentDate,
            normToolDesc,
            "original doc",
            EmbeddedNameAndVersion("name", "2.2")
        )

        assertEquals("owner", tool.owner)

        assertEquals(currentDate, tool.createdAt)

        assertEquals(currentDate, tool.modifiedAt)

        assertEquals(normToolDesc.description, tool.tool.description)

        assertEquals("original doc", tool.originalDocument)

        assertEquals("name", tool.id.name)

        val normAppDesc = NormalizedApplicationDescription(
            NameAndVersion("name", "2.2"),
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "app description",
            mockk(relaxed = true),
            mockk(relaxed = true),
            listOf("glob"),
            listOf()
        )

        val app = ApplicationEntity(
            "owner",
            currentDate,
            currentDate,
            normAppDesc,
            "original doc",
            tool,
            EmbeddedNameAndVersion("name", "2.2")
        )

        assertEquals("app description", app.application.description)
        assertEquals(currentDate, app.createdAt)
        assertEquals(currentDate, app.modifiedAt)
        assertEquals("name", app.id.name)
        assertEquals("owner", app.owner)
        assertEquals("original doc", app.tool.originalDocument)
        assertEquals("original doc", app.originalDocument)

        val tag = ApplicationTagEntity(
            app,
            "tag1"
        )
        assertEquals(app, tag.application)
        assertEquals("tag1", tag.tag)
    }

}
