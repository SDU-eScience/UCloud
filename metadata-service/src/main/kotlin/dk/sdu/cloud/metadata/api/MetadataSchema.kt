package dk.sdu.cloud.metadata.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.metadata.util.Licenses

interface UserEditableProjectMetadata {
    val title: String?
    val description: String?
    val license: String?
    val keywords: List<String>?
    val contributors: List<Creator>?
    val references: List<String>?
    val grants: List<Grant>?
    val subjects: List<Subject>?
    val notes: String?
}

data class ProjectMetadataEditRequest(
    val id: String,
    override val title: String? = null,
    override val description: String? = null,
    override val license: String? = null,
    override val keywords: List<String>? = null,
    override val contributors: List<Creator>? = null,
    override val references: List<String>? = null,
    override val grants: List<Grant>? = null,
    override val subjects: List<Subject>? = null,
    override val notes: String? = null
) : UserEditableProjectMetadata

data class ProjectMetadata(
    /**
     * The title of this project
     */
    override val title: String,

    /**
     * A description of the project.
     */
    override val description: String,

    /**
     * The license of the project
     */
    override val license: String?,

    val projectId: String,

    override val keywords: List<String>? = null,
    override val notes: String? = null,
    override val contributors: List<Creator>? = null,
    override val references: List<String>? = null,
    override val grants: List<Grant>? = null,

    override val subjects: List<Subject>? = null
) : UserEditableProjectMetadata {
    init {
        if (title.isBlank()) throw IllegalArgumentException("Metadata 'title' cannot be blank")
        if (description.isBlank()) throw IllegalArgumentException("Metadata 'description' cannot be blank")
        if (!license.isNullOrBlank() && !Licenses.isValidLicenseIdentifier(license)) {
            throw IllegalArgumentException("Metadata 'license' is invalid")
        }
    }
}

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
