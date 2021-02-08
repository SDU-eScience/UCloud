package dk.sdu.cloud.file.orchestrator

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.WithPaginationRequestV2

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FileMetadataFieldType.Integer::class, name = "int"),
    JsonSubTypes.Type(value = FileMetadataFieldType.FloatingPoint::class, name = "floating_point"),
    JsonSubTypes.Type(value = FileMetadataFieldType.Boolean::class, name = "boolean"),
    JsonSubTypes.Type(value = FileMetadataFieldType.Enum::class, name = "enum"),
    JsonSubTypes.Type(value = FileMetadataFieldType.Text::class, name = "text"),
    JsonSubTypes.Type(value = FileMetadataFieldType.Document::class, name = "document"),
)
sealed class FileMetadataFieldType {
    class Integer(
        val min: Long? = null,
        val max: Long? = null,
        val step: Long? = null,
    ) : FileMetadataFieldType()

    class FloatingPoint(
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
    ) : FileMetadataFieldType()

    class Boolean() : FileMetadataFieldType()

    class Enum(
        val options: List<String>,
    ) : FileMetadataFieldType() {
        init {
            require(options.isNotEmpty())
        }
    }

    class Text(
        val minimumLength: Int? = null,
        val maximumLength: Int? = null,
    ) : FileMetadataFieldType() {
        init {
            require(minimumLength == null || minimumLength in 0..(1024 * 1024 * 2))
            require(maximumLength == null || maximumLength in 0..(1024 * 1024 * 2))
            if (minimumLength != null && maximumLength != null) {
                require(maximumLength <= minimumLength)
            }
        }
    }

    class Document(val reference: String) : FileMetadataFieldType()
}

data class FileMetadataField(
    val name: String,
    val type: FileMetadataFieldType,
    val minimumEntries: Int,
    val maximumEntries: Int,
    val searchable: Boolean,
)

data class FileMetadataTemplate(
    override val id: String,
    override val specification: Spec,
    override val status: ResourceStatus,
    override val updates: List<ResourceUpdate>,
    override val billing: ResourceBilling,
    override val owner: ResourceOwner,
    override val acl: List<ResourceAclEntry<Nothing?>>?,
    override val createdAt: Long,
) : Resource<Nothing?> {
    data class Spec(
        val title: String,
        val version: String,
        val documents: Map<String, List<FileMetadataField>>,
        val rootDocument: String,
        val inheritable: Boolean,
        val description: String,
        val changeLog: String,
    ) : ResourceSpecification {
        override val product: Nothing? = null
    }
}

// ---

typealias FileMetadataTemplatesCreateRequest = BulkRequest<FileMetadataTemplate.Spec>
typealias FileMetadataTemplatesCreateResponse = BulkResponse<FindByStringId>

data class FileMetadataTemplatesBrowseRequest(
    override val itemsPerPage: Int?,
    override val next: String?,
    override val consistency: PaginationRequestV2Consistency?,
    override val itemsToSkip: Long?,
) : WithPaginationRequestV2
typealias FileMetadataTemplatesBrowseResponse = PageV2<FileMetadataTemplate>

typealias FileMetadataTemplatesRetrieveRequest = FindByStringId
typealias FileMetadataTemplatesRetrieveResponse = FileMetadataTemplate

typealias FileMetadataTemplatesDeprecateRequest = BulkRequest<FindByStringId>
typealias FileMetadataTemplatesDeprecateResponse = Unit

// ---

object FileMetadataTemplates : CallDescriptionContainer("file.metadata_template") {
    const val baseContext = "/api/files/metadataTemplate"

    init {
        title = "Metadata Templates for Files"
        description = "No documentation."
    }

    val create = call<FileMetadataTemplatesCreateRequest, FileMetadataTemplatesCreateResponse,
        CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val browse = call<FileMetadataTemplatesBrowseRequest, FileMetadataTemplatesBrowseResponse,
        CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val retrieve = call<FileMetadataTemplatesRetrieveRequest, FileMetadataTemplatesRetrieveResponse,
        CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val deprecate = call<FileMetadataTemplatesDeprecateRequest, FileMetadataTemplatesDeprecateResponse,
        CommonErrorMessage>("deprecate") {
        httpUpdate(baseContext, "deprecate")
    }
}
