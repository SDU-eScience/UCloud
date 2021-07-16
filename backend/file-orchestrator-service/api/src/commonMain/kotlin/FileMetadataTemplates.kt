package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Useful links:
//   - https://github.com/ginkgobioworks/react-json-schema-form-builder
//   - https://github.com/rjsf-team/react-jsonschema-form
//   - https://github.com/highsource/jsonix

@Serializable
data class FileMetadataTemplateNamespace(
    override val id: String,
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions?
) : Resource<Product, FileMetadataTemplateSupport> {
    override val billing = ResourceBilling.Free
    override val acl: List<ResourceAclEntry>? = null

    @Serializable
    data class Spec(
        val name: String,
        val namespaceType: FileMetadataTemplateNamespaceType
    ) : ResourceSpecification {
        override val product: ProductReference = ProductReference("", "", Provider.UCLOUD_CORE_PROVIDER)
    }

    @Serializable
    data class Status(
        val latestTitle: String? = null,
        override var resolvedSupport: ResolvedSupport<Product, FileMetadataTemplateSupport>? = null,
        override var resolvedProduct: Product? = null
    ) : ResourceStatus<Product, FileMetadataTemplateSupport>

    @Serializable
    data class Update(override val timestamp: Long, override val status: String?) : ResourceUpdate
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("""A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`""")
@Serializable
data class FileMetadataTemplate(
    @UCloudApiDoc("The ID of the namespace that this template belongs to")
    val namespaceId: String,
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
    val namespaceType: FileMetadataTemplateNamespaceType,
    val uiSchema: JsonObject? = null,

    val namespaceName: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class FileMetadataTemplateSupport(
    override val product: ProductReference = ProductReference("", "", Provider.UCLOUD_CORE_PROVIDER)
) : ProductSupport

@Serializable
@UCloudApiDoc("Determines how the metadata template is namespaces")
enum class FileMetadataTemplateNamespaceType {
    @UCloudApiDoc(
        """The template is namespaced to all collaborators
        
This means at most one metadata document can exist per file."""
    )
    COLLABORATORS,

    @UCloudApiDoc(
        """The template is namespaced to a single user
        
This means that a metadata document might exist for every user who has/had access to the file."""
    )
    PER_USER
}

@Serializable
data class FileMetadataHistory(
    val templates: Map<String, FileMetadataTemplate>,
    val metadata: Map<String, List<FileMetadataOrDeleted>>
)

// ---

@Serializable
data class FileMetadataTemplateNamespaceFlags(
    override val includeOthers: Boolean = false,
    override val includeUpdates: Boolean = false,
    override val includeSupport: Boolean = false,
    override val includeProduct: Boolean = false,
    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    val filterName: String? = null,
) : ResourceIncludeFlags

@Serializable
data class FileMetadataTemplateAndVersion(
    val id: String,
    val version: String,
)

@Serializable
data class FileMetadataTemplatesBrowseTemplatesRequest(
    val id: String,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

// ---

object FileMetadataTemplateNamespaces : ResourceApi<
    FileMetadataTemplateNamespace, FileMetadataTemplateNamespace.Spec, FileMetadataTemplateNamespace.Update,
    FileMetadataTemplateNamespaceFlags, FileMetadataTemplateNamespace.Status, Product,
    FileMetadataTemplateSupport>("files.metadataTemplates") {
    override val typeInfo = ResourceTypeInfo<FileMetadataTemplateNamespace, FileMetadataTemplateNamespace.Spec,
        FileMetadataTemplateNamespace.Update, FileMetadataTemplateNamespaceFlags, FileMetadataTemplateNamespace.Status,
        Product, FileMetadataTemplateSupport>()

    val createTemplate = call<BulkRequest<FileMetadataTemplate>, BulkResponse<FileMetadataTemplateAndVersion>,
        CommonErrorMessage>("createTemplate") {
        httpCreate(baseContext, "templates")
    }

    val retrieveLatest = call<FindByStringId, FileMetadataTemplate, CommonErrorMessage>("retrieveLatest") {
        httpRetrieve(baseContext, "latest")
    }

    val retrieveTemplate = call<FileMetadataTemplateAndVersion, FileMetadataTemplate,
        CommonErrorMessage>("retrieveTemplate") {
        httpRetrieve(baseContext, "templates")
    }

    val browseTemplates = call<FileMetadataTemplatesBrowseTemplatesRequest, PageV2<FileMetadataTemplate>,
        CommonErrorMessage>("browseTemplates") {
        httpBrowse(baseContext, "templates")
    }

    val deprecate = call<BulkRequest<FindByStringId>, BulkResponse<Unit>, CommonErrorMessage>("deprecate") {
        httpUpdate(baseContext, "deprecate")
    }
}
