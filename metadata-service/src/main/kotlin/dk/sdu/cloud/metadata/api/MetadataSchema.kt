package dk.sdu.cloud.metadata.api

enum class AccessRight {
    OPEN_ACCESS,
    EMBARGOED,
    RESTRICTED,
    CLOSED
}

interface UserEditableProjectMetadata {
    val title: String
    val description: String
    val license: String
    val keywords: List<String>?
    val contributors: List<Creator>?
    val references: List<String>?
    val grants: List<Grant>?
    val subjects: List<Subject>?
    val relatedIdentifiers: List<RelatedIdentifier>?
}

data class ProjectMetadata(
    /**
     * The SDUCloud FSRoot this metadata belongs to (i.e. project)
     */
    val sduCloudRoot: String,

    /**
     * The title of this project
     */
    override val title: String,

    /**
     * A list of files in this project
     */
    val files: List<FileDescriptionForMetadata>,

    /**
     * A list of creators of this project (defaults to users in project)
     */
    val creators: List<Creator>,

    /**
     * A description of the project.
     */
    override val description: String,

    /**
     * The license of the project
     */
    override val license: String,

    val id: String,

    val embargoDate: Long? = null,
    // access conditions for embargo
    val accessConditions: String? = null,

    override val keywords: List<String>? = null,
    val notes: String? = null,
    override val contributors: List<Creator>? = null,
    override val references: List<String>? = null,
    override val grants: List<Grant>? = null,

    val journalInformation: JournalInformation? = null,
    val conferenceInformation: ConferenceInformation? = null,
    val imprintInformation: ImprintInformation? = null,
    val partOfInformation: PartOfInformation? = null,
    val thesisInformation: ThesisInformation? = null,

    val subjects: List<Subject>? = null,
    override val relatedIdentifiers: List<RelatedIdentifier>? = null
): UserEditableProjectMetadata {
    init {
        if (title.isBlank()) throw IllegalArgumentException("title cannot be blank")
    }
}

data class JournalInformation(
    val title: String?,
    val volume: String?,
    val issue: String?,
    val pages: String?
)

data class ConferenceInformation(
    val acronym: String?,
    val dates: String?,
    val place: String?,
    val url: String?,
    val session: String?,
    val sessionPart: String?
)

data class ImprintInformation(
    val publisher: String?,
    val isbn: String?,
    val place: String?
)

data class PartOfInformation(
    val title: String?,
    val pages: String?
)

data class ThesisInformation(
    val supervisors: List<Creator>?,
    val university: String?
)

data class Grant(val id: String)

data class Subject(val term: String, val identifier: String, val scheme: String?)

sealed class RelatedIdentifier(val relation: String) {
    abstract val identifier: String

    data class CitedBy(override val identifier: String) : RelatedIdentifier("isCitedBy")
    data class Cites(override val identifier: String) : RelatedIdentifier("cites")
    data class SupplementTo(override val identifier: String) : RelatedIdentifier("isSupplementTo")
    data class SupplementedBy(override val identifier: String) : RelatedIdentifier("isSupplementedBy")
    data class NewVersionOf(override val identifier: String) : RelatedIdentifier("isNewVersionOf")
    data class PreviousVersionOf(override val identifier: String) : RelatedIdentifier("isPreviousVersionOf")
    data class PartOf(override val identifier: String) : RelatedIdentifier("isPartOf")
    data class HasPart(override val identifier: String) : RelatedIdentifier("hasPart")
    data class Compiles(override val identifier: String) : RelatedIdentifier("compiles")
    data class CompiledBy(override val identifier: String) : RelatedIdentifier("isCompiledBy")
    data class IdenticalTo(override val identifier: String) : RelatedIdentifier("isIdenticalTo")
    data class AlternativeIdentifier(override val identifier: String) : RelatedIdentifier("IsAlternateIdentifier")
}

data class Creator(
    val name: String,
    val affiliation: String? = null,
    val orcId: String? = null,
    val gnd: String? = null
)