package dk.sdu.cloud.file.orchestrator

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.main.JsonSchemaFactory
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.WithPaginationRequestV2
import io.ktor.http.*

// TODO Most metadata formats are probably XSD which is also annoying. It is probably doable to do a conversion to
//  json-schema, however, this still leads to annoying cases where we cannot reliably export to valid XML (maybe?)
//  Useful links:
//    - https://github.com/ginkgobioworks/react-json-schema-form-builder
//    - https://github.com/rjsf-team/react-jsonschema-form
//    - https://github.com/highsource/jsonix
// TODO Searchable fields

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("""A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`""")
data class FileMetadataTemplate(
    override val id: String,
    override val specification: Spec,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: SimpleResourceOwner,
    override val acl: List<ResourceAclEntry<FileMetadataTemplatePermission>>?,
    override val createdAt: Long,
) : Resource<FileMetadataTemplatePermission> {
    override val billing: ResourceBilling = ResourceBilling.Free

    data class Spec(
        @UCloudApiDoc("The title of this template. It does not have to be unique.")
        val title: String,
        @UCloudApiDoc("Version identifier for this version. It must be unique within a single template group.")
        val version: String,
        @UCloudApiDoc("JSON-Schema for this document")
        val schema: JsonNode,
        @UCloudApiDoc("Makes this template inheritable by descendants of the file that the template is attached to")
        val inheritable: Boolean,
        @UCloudApiDoc("If `true` then a user with `ADMINISTRATOR` rights must approve all changes to metadata")
        val requireApproval: Boolean,
        @UCloudApiDoc("Description of this template. Markdown is supported.")
        val description: String,
        @UCloudApiDoc("A description of the change since last version. Markdown is supported.")
        val changeLog: String,
    ) : ResourceSpecification {
        override val product: Nothing? = null

        init {
            if (!JsonSchemaFactory.byDefault().syntaxValidator.schemaIsValid(schema)) {
                throw RPCException("Schema is not a valid JSON-schema", HttpStatusCode.BadRequest)
            }
        }
    }

    class Status(
        val oldVersions: List<Spec>,
    ) : ResourceStatus

    data class Update(
        override val timestamp: Long,
        override val status: String?,
    ) : ResourceUpdate
}

enum class FileMetadataTemplatePermission {
    READ,
    WRITE
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
