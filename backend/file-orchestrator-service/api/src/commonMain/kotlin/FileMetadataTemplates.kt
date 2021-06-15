package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
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
    override val owner: ResourceOwner,
    override val acl: List<ResourceAclEntry>,
    override val createdAt: Long,
    val public: Boolean,
    override val permissions: ResourcePermissions? = null,
) : Resource<Product, ProductSupport> {
    override val billing: ResourceBilling.Free = ResourceBilling.Free

    @Serializable
    data class Spec(
        @UCloudApiDoc("The unique ID for this template")
        val id: String,
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
        @UCloudApiDoc("Determines how this metadata template is namespaces\n\n" +
            "NOTE: This is required to not change between versions")
        val namespaceType: FileMetadataTemplateNamespaceType,
        val uiSchema: JsonObject? = null,
    ) : ResourceSpecification {
        override val product: ProductReference = ProductReference("", "", Provider.UCLOUD_CORE_PROVIDER)
    }

    @Serializable
    class Status(
        val oldVersions: List<String>,
        override var resolvedSupport: ResolvedSupport<Product, ProductSupport>? = null,
        override var resolvedProduct: Product? = null,
    ) : ResourceStatus<Product, ProductSupport>

    @Serializable
    data class Update(
        override val timestamp: Long,
        override val status: String?,
    ) : ResourceUpdate
}

@Serializable
@UCloudApiDoc("Determines how the metadata template is namespaces")
enum class FileMetadataTemplateNamespaceType {
    @UCloudApiDoc("""The template is namespaced to all collaborators
        
This means at most one metadata document can exist per file.""")
    COLLABORATORS,

    @UCloudApiDoc("""The template is namespaced to a single user
        
This means that a metadata document might exist for every user who has/had access to the file.""")
    PER_USER
}

typealias FileMetadataTemplatePermission = Permission

@Serializable
data class FileMetadataHistory(
    val templates: Map<String, FileMetadataTemplate>,
    val metadata: Map<String, List<FileMetadataOrDeleted>>
)

// ---

typealias FileMetadataTemplatesCreateRequest = BulkRequest<FileMetadataTemplate.Spec>
typealias FileMetadataTemplatesCreateResponse = BulkResponse<FindByStringId>

@Serializable
data class FileMetadataTemplatesBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias FileMetadataTemplatesBrowseResponse = PageV2<FileMetadataTemplate>

@Serializable
data class FileMetadataTemplatesRetrieveRequest(val id: String, val version: String? = null)
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
