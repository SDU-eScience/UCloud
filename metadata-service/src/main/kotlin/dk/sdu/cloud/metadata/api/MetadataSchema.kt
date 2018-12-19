package dk.sdu.cloud.metadata.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.metadata.util.Licenses
import dk.sdu.cloud.service.TYPE_PROPERTY

interface UserEditableProjectMetadata {
    val title: String?
    val description: String?
    val license: String?
    val keywords: List<String>?
    val contributors: List<Creator>?
    val references: List<String>?
    val grants: List<Grant>?
    val subjects: List<Subject>?
    val relatedIdentifiers: List<RelatedIdentifier>?
    val notes: String?
}

data class ProjectMetadataEditRequest(
    val id: Long,
    override val title: String? = null,
    override val description: String? = null,
    override val license: String? = null,
    override val keywords: List<String>? = null,
    override val contributors: List<Creator>? = null,
    override val references: List<String>? = null,
    override val grants: List<Grant>? = null,
    override val subjects: List<Subject>? = null,
    override val notes: String? = null,
    override val relatedIdentifiers: List<RelatedIdentifier>? = null
) : UserEditableProjectMetadata

data class ProjectMetadata(
    /**
     * The SDUCloud FSRoot this metadata belongs to (i.e. project)
     */
    val sduCloudRoot: String,

    /**
     * The SDUCloud FSRoot ID
     */
    val sduCloudRootId: String,

    /**
     * The title of this project
     */
    override val title: String,

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
    override val license: String?,

    val id: String,

    val embargoDate: Long? = null,
    // access conditions for embargo
    val accessConditions: String? = null,

    override val keywords: List<String>? = null,
    override val notes: String? = null,
    override val contributors: List<Creator>? = null,
    override val references: List<String>? = null,
    override val grants: List<Grant>? = null,

    val journalInformation: JournalInformation? = null,
    val conferenceInformation: ConferenceInformation? = null,
    val imprintInformation: ImprintInformation? = null,
    val partOfInformation: PartOfInformation? = null,
    val thesisInformation: ThesisInformation? = null,

    override val subjects: List<Subject>? = null,
    override val relatedIdentifiers: List<RelatedIdentifier>? = null
) : UserEditableProjectMetadata {
    init {
        if (title.isBlank()) throw IllegalArgumentException("Metadata 'title' cannot be blank")
        if (description.isBlank()) throw IllegalArgumentException("Metadata 'description' cannot be blank")
        if (!license.isNullOrBlank() && license != null && !Licenses.isValidLicenseIdentifier(license)) {
            throw IllegalArgumentException("Metadata 'license' is invalid")
        }
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

data class Grant(val id: String) {
    init {
        if (id.isBlank()) throw IllegalArgumentException("Grant 'id' cannot be blank")
    }
}

data class Subject(val term: String, val identifier: String, val scheme: String?) {
    init {
        if (term.isBlank()) throw IllegalArgumentException("Subject 'term' cannot be blank")
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "relation"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RelatedIdentifier.CitedBy::class, name = "isCitedBy"),
    JsonSubTypes.Type(value = RelatedIdentifier.Cites::class, name = "cites"),
    JsonSubTypes.Type(value = RelatedIdentifier.SupplementTo::class, name = "isSupplementTo"),
    JsonSubTypes.Type(value = RelatedIdentifier.SupplementedBy::class, name = "isSupplementedBy"),
    JsonSubTypes.Type(value = RelatedIdentifier.NewVersionOf::class, name = "isNewVersionOf"),
    JsonSubTypes.Type(value = RelatedIdentifier.PreviousVersionOf::class, name = "isPreviousVersionOf"),
    JsonSubTypes.Type(value = RelatedIdentifier.PartOf::class, name = "isPartOf"),
    JsonSubTypes.Type(value = RelatedIdentifier.HasPart::class, name = "hasPart"),
    JsonSubTypes.Type(value = RelatedIdentifier.Compiles::class, name = "compiles"),
    JsonSubTypes.Type(value = RelatedIdentifier.CompiledBy::class, name = "isCompiledBy"),
    JsonSubTypes.Type(value = RelatedIdentifier.IdenticalTo::class, name = "isIdenticalTo"),
    JsonSubTypes.Type(value = RelatedIdentifier.AlternativeIdentifier::class, name = "IsAlternateIdentifier")
)
sealed class RelatedIdentifier {
    abstract val identifier: String

    data class CitedBy(override val identifier: String) : RelatedIdentifier()
    data class Cites(override val identifier: String) : RelatedIdentifier()
    data class SupplementTo(override val identifier: String) : RelatedIdentifier()
    data class SupplementedBy(override val identifier: String) : RelatedIdentifier()
    data class NewVersionOf(override val identifier: String) : RelatedIdentifier()
    data class PreviousVersionOf(override val identifier: String) : RelatedIdentifier()
    data class PartOf(override val identifier: String) : RelatedIdentifier()
    data class HasPart(override val identifier: String) : RelatedIdentifier()
    data class Compiles(override val identifier: String) : RelatedIdentifier()
    data class CompiledBy(override val identifier: String) : RelatedIdentifier()
    data class IdenticalTo(override val identifier: String) : RelatedIdentifier()
    data class AlternativeIdentifier(override val identifier: String) : RelatedIdentifier()
}

data class Creator(
    val name: String,
    val affiliation: String? = null,
    val orcId: String? = null,
    val gnd: String? = null
) {
    init {
        if (name.isBlank()) throw IllegalArgumentException("Creator 'name' cannot be blank")
    }
}
