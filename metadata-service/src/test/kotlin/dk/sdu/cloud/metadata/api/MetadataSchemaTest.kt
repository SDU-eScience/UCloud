package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.storage.api.FileType
import org.junit.Test
import kotlin.test.assertEquals

class MetadataSchemaTest {

    private val filesList = listOf(
        FileDescriptionForMetadata(
            "1",
            FileType.FILE,
            "home"
        )
    )

    private val creatorList = listOf(
        Creator(
            "Henrik Schulz",
            "SDU",
            "Orc11111",
            "gnd22222"
        )
    )

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

        val supervisorList = listOf(
            Creator(
                "Jonas Hinchely",
                "SDU",
                "Orc11114",
                "gnd22225"
            )
        )

        val subjectsList = listOf(
            Subject(
                "term",
                "12",
                "scheme"
            )
        )

        val relatedIdentifiersList = listOf(
            RelatedIdentifier.CitedBy("id"),
            RelatedIdentifier.AlternativeIdentifier("id"),
            RelatedIdentifier.Cites("id"),
            RelatedIdentifier.SupplementTo("id"),
            RelatedIdentifier.SupplementedBy("id"),
            RelatedIdentifier.NewVersionOf("oldversion"),
            RelatedIdentifier.PreviousVersionOf("new Version"),
            RelatedIdentifier.PartOf("this"),
            RelatedIdentifier.HasPart("in other"),
            RelatedIdentifier.CompiledBy("me"),
            RelatedIdentifier.Compiles("true"),
            RelatedIdentifier.IdenticalTo("something")
        )

        val metadata = ProjectMetadata(
            "",
            "title",
            filesList,
            creatorList,
            "This is a description",
            "Abstyles",
            "1",
            899929,
            "Open",
            keywordsList,
            "This is a note",
            contributorList,
            referenceList,
            grantList,
            JournalInformation(
                "Jtitle",
                "2",
                "22",
                "2-99"
            ),
            ConferenceInformation(
                "HH",
                "22-2-01",
                "moscow",
                "www",
                "1",
                "2"
            ),
            ImprintInformation(
                "SDU",
                "222-2-2-2-22",
                "Berlin"
            ),
            PartOfInformation(
                "Part of a project",
                "22-29"
            ),
            ThesisInformation(
                supervisorList,
                "Princeton"
            ),
            subjectsList,
            relatedIdentifiersList
        )

        assertEquals("", metadata.sduCloudRoot)
        assertEquals("1", metadata.id)
        assertEquals("title", metadata.title)
        assertEquals("This is a description", metadata.description)
        assertEquals("Abstyles", metadata.license)
        assertEquals("keyword 1", metadata.keywords?.get(0))
        assertEquals("here", metadata.references?.get(0))
        assertEquals("This is a note", metadata.notes)
        assertEquals("Open", metadata.accessConditions)
        assertEquals(899929, metadata.embargoDate)

        assertEquals("1", metadata.files[0].id)
        assertEquals("home", metadata.files[0].path)
        assertEquals(FileType.FILE, metadata.files[0].type)

        assertEquals("Henrik Schulz", metadata.creators[0].name)
        assertEquals("SDU", metadata.creators[0].affiliation)
        assertEquals("Orc11111", metadata.creators[0].orcId)
        assertEquals("gnd22222", metadata.creators[0].gnd)

        assertEquals("Dan Sebastian Thrane", metadata.contributors?.get(0)?.name)
        assertEquals("SDU", metadata.contributors?.get(0)?.affiliation)
        assertEquals("Orc11112", metadata.contributors?.get(0)?.orcId)
        assertEquals("gnd22223", metadata.contributors?.get(0)?.gnd)

        assertEquals("idOfGrant", metadata.grants?.get(0)?.id)

        assertEquals("12", metadata.subjects?.get(0)?.identifier)
        assertEquals("term", metadata.subjects?.get(0)?.term)
        assertEquals("scheme", metadata.subjects?.get(0)?.scheme)

        assertEquals("isCitedBy", metadata.relatedIdentifiers?.get(0)?.relation)
        assertEquals("id", metadata.relatedIdentifiers?.get(0)?.identifier)

        assertEquals("Jtitle", metadata.journalInformation?.title)
        assertEquals("2", metadata.journalInformation?.volume)
        assertEquals("22", metadata.journalInformation?.issue)
        assertEquals("2-99", metadata.journalInformation?.pages)

        assertEquals("HH", metadata.conferenceInformation?.acronym)
        assertEquals("22-2-01", metadata.conferenceInformation?.dates)
        assertEquals("moscow", metadata.conferenceInformation?.place)
        assertEquals("www", metadata.conferenceInformation?.url)
        assertEquals("1", metadata.conferenceInformation?.session)
        assertEquals("2", metadata.conferenceInformation?.sessionPart)

        assertEquals("SDU", metadata.imprintInformation?.publisher)
        assertEquals("222-2-2-2-22", metadata.imprintInformation?.isbn)
        assertEquals("Berlin", metadata.imprintInformation?.place)

        assertEquals("Part of a project", metadata.partOfInformation?.title)
        assertEquals("22-29", metadata.partOfInformation?.pages)

        assertEquals("Princeton", metadata.thesisInformation?.university)
        assertEquals("Jonas Hinchely", metadata.thesisInformation?.supervisors?.get(0)?.name)
        assertEquals("SDU", metadata.thesisInformation?.supervisors?.get(0)?.affiliation)
        assertEquals("Orc11114", metadata.thesisInformation?.supervisors?.get(0)?.orcId)
        assertEquals("gnd22225", metadata.thesisInformation?.supervisors?.get(0)?.gnd)

    }

    @Test(expected = IllegalArgumentException::class)
    fun `Generate scheme - Invalid License - test`() {
        ProjectMetadata(
            "",
            "title",
            filesList,
            creatorList,
            "A description",
            "NotALegalOne",
            "1"
        )
    }

}