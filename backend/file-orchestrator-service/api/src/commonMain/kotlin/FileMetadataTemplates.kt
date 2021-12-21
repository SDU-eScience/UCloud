package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.typeOf

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
    @UCloudApiOwnedBy(FileMetadataTemplateNamespaces::class)
    data class Update(override val timestamp: Long, override val status: String?) : ResourceUpdate
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("""A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`""")
@Serializable
@UCloudApiOwnedBy(FileMetadata::class)
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
@UCloudApiOwnedBy(FileMetadata::class)
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
    override val filterProviderIds: String? = null,
    override val filterIds: String? = null,
    val filterName: String? = null,
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
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

@UCloudApiExampleValue
internal val sensitivityExample = FileMetadataTemplate(
    "sensitivity",
    "Sensitivity",
    "1.0.0",
    defaultMapper.decodeFromString(
        """
            {"type": "object", "title": "UCloud File Sensitivity", "required": ["sensitivity"], 
            "properties": {"sensitivity": {"enum": ["SENSITIVE", "CONFIDENTIAL", "PRIVATE"], 
            "type": "string", "title": "File Sensitivity", 
            "enumNames": ["Sensitive", "Confidential", "Private"]}}, "dependencies": {}}
        """.trimIndent()
    ),
    inheritable = true,
    requireApproval = true,
    "File sensitivity for files",
    changeLog = "Initial version",
    FileMetadataTemplateNamespaceType.COLLABORATORS,
    uiSchema = defaultMapper.decodeFromString(
        """
           {"ui:order": ["sensitivity"]} 
        """.trimIndent()
    ),
)

object FileMetadataTemplateNamespaces : ResourceApi<
    FileMetadataTemplateNamespace, FileMetadataTemplateNamespace.Spec, FileMetadataTemplateNamespace.Update,
    FileMetadataTemplateNamespaceFlags, FileMetadataTemplateNamespace.Status, Product,
    FileMetadataTemplateSupport>("files.metadataTemplates") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        FileMetadataTemplateNamespace.serializer(),
        typeOf<FileMetadataTemplateNamespace>(),
        FileMetadataTemplateNamespace.Spec.serializer(),
        typeOf<FileMetadataTemplateNamespace.Spec>(),
        FileMetadataTemplateNamespace.Update.serializer(),
        typeOf<FileMetadataTemplateNamespace.Update>(),
        FileMetadataTemplateNamespaceFlags.serializer(),
        typeOf<FileMetadataTemplateNamespaceFlags>(),
        FileMetadataTemplateNamespace.Status.serializer(),
        typeOf<FileMetadataTemplateNamespace.Status>(),
        FileMetadataTemplateSupport.serializer(),
        typeOf<FileMetadataTemplateSupport>(),
        Product.serializer(),
        typeOf<Product>(),
    )

    init {
        description = """
Metadata templates define the schema for metadata documents.

${Resources.readMeFirst}

UCloud supports arbitrary of files. This feature is useful for general data management. It allows users to 
tag documents at a glance and search through them.

This feature consists of two parts:

1. __Metadata templates (you are here):__ Templates specify the schema. You can think of this as a way of 
   defining _how_ your documents should look. We use them to generate user interfaces and visual 
   representations of your documents.
2. __Metadata documents (next section):__ Documents fill out the values of a template. When you create a 
   document you must attach it to a file also.

At a technical level, we implement metadata templates using [JSON schema](https://json-schema.org/). 
This gives you a fair amount of flexibility to control the format of documents. Of course, not everything 
is machine-checkable. To mitigate this, templates can require that changes go through an approval process.
Only administrators of a workspace can approve such changes.
        """.trimIndent()
    }

    @OptIn(UCloudApiExampleValue::class)
    override fun documentation() {
        useCase(
            "sensitivity",
            "The Sensitivity Template",
            flow = {
                val user = basicUser()
                val template = sensitivityExample

                success(
                    createTemplate,
                    bulkRequestOf(template),
                    BulkResponse(listOf(FileMetadataTemplateAndVersion("15123", "1.0.0"))),
                    user
                )

                success(
                    retrieveLatest,
                    FindByStringId("15123"),
                    template,
                    user
                )

                success(
                    browseTemplates,
                    FileMetadataTemplatesBrowseTemplatesRequest("15123"),
                    PageV2(50, listOf(template), null),
                    user
                )

                success(
                    browse,
                    ResourceBrowseRequest(FileMetadataTemplateNamespaceFlags()),
                    PageV2(
                        50,
                        listOf(
                            FileMetadataTemplateNamespace(
                                "15123",
                                FileMetadataTemplateNamespace.Spec(
                                    "sensitivity",
                                    FileMetadataTemplateNamespaceType.COLLABORATORS
                                ),
                                1635151675465L,
                                FileMetadataTemplateNamespace.Status("Sensitivity"),
                                emptyList(),
                                ResourceOwner("user", null),
                                ResourcePermissions(listOf(Permission.ADMIN), emptyList())
                            )
                        ),
                        null
                    ),
                    user
                )
            }
        )
    }

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

    override val create get() = super.create!!
}
