package dk.sdu.cloud.metadata.api

import org.junit.Test
import kotlin.test.assertEquals

class MetadataSchemaTest {

    @Test
    fun `Generate full schema`() {
        val keywordsList = listOf("keyword 1", "keyword 2")

        val contributorList = listOf(
            Creator(
                "Dan Sebastian Thrane",
                "SDU",
                "Orc11112",
                "gnd22223"
            )
        )

        val referenceList = listOf("here", "and", "there")

        val grantList = listOf(
            Grant("idOfGrant")
        )

        val subjectsList = listOf(
            Subject(
                "term",
                "12",
                "scheme"
            )
        )

        val metadata = ProjectMetadata(
            "title",
            "This is a description",
            "Abstyles",
            "1",
            keywordsList,
            "This is a note",
            contributorList,
            referenceList,
            grantList,
            subjectsList
        )

        assertEquals("1", metadata.projectId)
        assertEquals("title", metadata.title)
        assertEquals("This is a description", metadata.description)
        assertEquals("Abstyles", metadata.license)
        assertEquals("keyword 1", metadata.keywords?.get(0))
        assertEquals("here", metadata.references?.get(0))
        assertEquals("This is a note", metadata.notes)

        assertEquals("Dan Sebastian Thrane", metadata.contributors?.get(0)?.name)
        assertEquals("SDU", metadata.contributors?.get(0)?.affiliation)
        assertEquals("Orc11112", metadata.contributors?.get(0)?.orcId)
        assertEquals("gnd22223", metadata.contributors?.get(0)?.gnd)

        assertEquals("idOfGrant", metadata.grants?.get(0)?.id)

        assertEquals("12", metadata.subjects?.get(0)?.identifier)
        assertEquals("term", metadata.subjects?.get(0)?.term)
        assertEquals("scheme", metadata.subjects?.get(0)?.scheme)

    }

    @Test(expected = IllegalArgumentException::class)
    fun `Generate scheme - Invalid License - test`() {
        ProjectMetadata(
            "title",
            "A description",
            "NotALegalOne",
            "1"
        )
    }

}
