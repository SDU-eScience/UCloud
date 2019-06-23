package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.app.store.util.normToolDesc
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class HibernateModelTest{

    @Test
    fun `Simple Tool Entity test`() {
        val date = Date()
        val toolEntity = ToolEntity(
            "owner",
            date,
            date,
            normToolDesc,
            "original doc",
            EmbeddedNameAndVersion("tool", "2.2")
        )

        assertEquals(toolEntity.createdAt, date)
        assertEquals(toolEntity.modifiedAt, date)
        assertEquals(toolEntity.id.name, "tool")
        assertEquals(toolEntity.id.version, "2.2")
        assertEquals(toolEntity.originalDocument, "original doc")
        assertEquals(toolEntity.owner, "owner")
        assertEquals(toolEntity.tool.container, normToolDesc.container)
    }

    @Test
    fun `Simple Favorite Application Entity test`() {
        val fApplicationEntity = FavoriteApplicationEntity(
            "app name",
            "2.2",
            "user",
            123456
        )

        assertEquals(fApplicationEntity.applicationName, "app name")
        assertEquals(fApplicationEntity.applicationVersion, "2.2")
        assertEquals(fApplicationEntity.id, 123456)
        assertEquals(fApplicationEntity.user, "user")
    }


    @Test
    fun `Simple Application Entity test`() {
        val date = Date()
        val appEntity = ApplicationEntity(
            "owner",
            date,
            date,
            listOf("author1", "author2"),
            "title",
            "description",
            "website",
            listOf("tag1", "tag2"),
            ApplicationInvocationDescription(
                ToolReference("name", "2.2", null),
                emptyList(),
                emptyList(),
                emptyList()
            ),
            "name",
            "2.2",
            EmbeddedNameAndVersion("name", "2.2")
        )

        assertEquals("owner", appEntity.owner)
        assertEquals(date, appEntity.createdAt)
        assertEquals(date, appEntity.modifiedAt)
        assertEquals("author1", appEntity.authors.first())
        assertEquals("title", appEntity.title)
        assertEquals("description", appEntity.description)
        assertEquals("website", appEntity.website)
        assertEquals("tag2", appEntity.tags.last())
        assertEquals("name", appEntity.application.tool.name)
        assertEquals("name", appEntity.toolName)
        assertEquals("2.2", appEntity.toolVersion)
        assertEquals("name", appEntity.id.name)
    }
}
