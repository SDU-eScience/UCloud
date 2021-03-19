package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// TODO Most metadata formats are probably XSD which is also annoying. It is probably doable to do a conversion to
//  json-schema, however, this still leads to annoying cases where we cannot reliably export to valid XML (maybe?)
//  Useful links:
//    - https://github.com/ginkgobioworks/react-json-schema-form-builder
//    - https://github.com/rjsf-team/react-jsonschema-form
//    - https://github.com/highsource/jsonix
// TODO Searchable fields

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("""A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`""")
@Serializable
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

    @Serializable
    data class Spec(
        @UCloudApiDoc("The title of this template. It does not have to be unique.")
        val title: String,
        @UCloudApiDoc("Version identifier for this version. It must be unique within a single template group.")
        val version: String,
        @UCloudApiDoc("JSON-Schema for this document")
        val schema: JsonObject,
        @UCloudApiDoc("Makes this template inheritable by descendants of the file that the template is attached to")
        val inheritable: Boolean,
        @UCloudApiDoc("If `true` then a user with `ADMINISTRATOR` rights must approve all changes to metadata")
        val requireApproval: Boolean,
        @UCloudApiDoc("Description of this template. Markdown is supported.")
        val description: String,
        @UCloudApiDoc("A description of the change since last version. Markdown is supported.")
        val changeLog: String,
    ) : ResourceSpecification {
        @Contextual
        override val product: Nothing? = null

        init {
            /*
            if (!JsonSchemaFactory.byDefault().syntaxValidator.schemaIsValid(schema)) {
                throw RPCException("Schema is not a valid JSON-schema", HttpStatusCode.BadRequest)
            }
             */
        }
    }

    @Serializable
    class Status(
        val oldVersions: List<Spec>,
    ) : ResourceStatus

    @Serializable
    data class Update(
        override val timestamp: Long,
        override val status: String?,
    ) : ResourceUpdate
}

@Serializable
enum class FileMetadataTemplatePermission {
    READ,
    WRITE
}

// ---

typealias FileMetadataTemplatesCreateRequest = BulkRequest<FileMetadataTemplate.Spec>
typealias FileMetadataTemplatesCreateResponse = BulkResponse<FindByStringId>

@Serializable
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
