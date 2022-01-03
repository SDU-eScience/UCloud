package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.WithStringId
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ---

typealias FileMetadataAddMetadataRequest = BulkRequest<FileMetadataAddRequestItem>

@Serializable
data class FileMetadataAddRequestItem(
    val fileId: String,
    val metadata: FileMetadataDocument.Spec,
)
typealias FileMetadataAddMetadataResponse = BulkResponse<FindByStringId>

typealias FileMetadataMoveRequest = BulkRequest<FileMetadataMoveRequestItem>

@Serializable
data class FileMetadataMoveRequestItem(
    val oldFileId: String,
    val newFileId: String,
)
typealias FileMetadataMoveResponse = Unit

typealias FileMetadataDeleteRequest = BulkRequest<FileMetadataDeleteRequestItem>
@Serializable
data class FileMetadataDeleteRequestItem(
    override val id: String,
    val changeLog: String,
) : WithStringId
typealias FileMetadataDeleteResponse = Unit

@Serializable
data class FileMetadataRetrieveAllRequest(
    val fileId: String,
)

@Serializable
data class FileMetadataRetrieveAllResponse(
    val metadata: List<FileMetadataAttached>
)

@Serializable
data class FileMetadataAttached(val path: String, val metadata: FileMetadataDocument)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class FileMetadataBrowseRequest(
    @UCloudApiDoc("Filters on the `templateId` attribute of metadata documents")
    val filterTemplate: String? = null,
    @UCloudApiDoc("Filters on the `version` attribute of metadata documents." +
            "Requires `filterTemplate` to be specified`")
    val filterVersion: String? = null,
    @UCloudApiDoc("Determines if this should only fetch document which have status `not_required` or `approved`")
    val filterActive: Boolean = true,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2 {
    init {
        if (filterVersion != null && filterTemplate == null) {
            throw RPCException("filterVersion cannot be specified without filterTemplate", HttpStatusCode.BadRequest)
        }
    }
}

typealias FileMetadataBrowseResponse = PageV2<FileMetadataAttached>

// ---

object FileMetadata : CallDescriptionContainer("files.metadata") {
    const val baseContext = "/api/files/metadata"

    init {
        description = """
            Metadata documents form the foundation of data management in UCloud.
            
            UCloud supports arbitrary of files. This feature is useful for general data management. It allows users to 
            tag documents at a glance and search through them.

            This feature consists of two parts:

            1. __Metadata templates (previous section):__ Templates specify the schema. You can think of this as a way of 
               defining _how_ your documents should look. We use them to generate user interfaces and visual 
               representations of your documents.
            2. __Metadata documents (you are here):__ Documents fill out the values of a template. When you create a 
               document you must attach it to a file also.
        """.trimIndent()
    }

    @OptIn(UCloudApiExampleValue::class)
    override fun documentation() {
        useCase(
            "sensitivity",
            "Sensitivity Document",
            flow = {
                val user = basicUser()

                comment("In this example, we will show how to create a metadata document and attach it to a file.")
                comment("We already have a metadata template in the catalog:")

                success(
                    FileMetadataTemplateNamespaces.retrieveLatest,
                    FindByStringId("15123"),
                    sensitivityExample,
                    user
                )

                comment("Using this, we can create a metadata document and attach it to our file")

                val metadata = FileMetadataDocument.Spec(
                    "15123",
                    "1.0.0",
                    JsonObject(
                        mapOf(
                            "sensitivity" to JsonPrimitive("SENSITIVE")
                        )
                    ),
                    "New sensitivity"
                )
                success(
                    create,
                    bulkRequestOf(
                        FileMetadataAddRequestItem(
                            "/51231/my/file",
                            metadata
                        )
                    ),
                    FileMetadataAddMetadataResponse(listOf(FindByStringId("651233"))),
                    user
                )

                comment("This specific template requires approval from a workspace admin. We can do this by calling " +
                        "approve.")

                success(
                    approve,
                    bulkRequestOf(FindByStringId("651233")),
                    Unit,
                    user
                )

                comment("We can view the metadata by adding includeMetadata = true when requesting any file")

                success(
                    Files.retrieve,
                    ResourceRetrieveRequest(UFileIncludeFlags(includeMetadata = true), "51231"),
                    UFile(
                        "/51231/my/file",
                        UFileSpecification(
                            "51231",
                            ProductReference("example-ssd", "example-ssd", "example")
                        ),
                        1635151675465L,
                        UFileStatus(
                            metadata = FileMetadataHistory(
                                mapOf("sensitivity" to sensitivityExample),
                                mapOf(
                                    "sensitivity" to listOf(
                                        FileMetadataDocument(
                                            "651233",
                                            metadata,
                                            1635151675465L,
                                            FileMetadataDocument.Status(
                                                FileMetadataDocument.ApprovalStatus.Approved("user")
                                            ),
                                            "user"
                                        )
                                    )
                                )
                            )
                        ),
                        ResourceOwner("user", null),
                        ResourcePermissions(listOf(Permission.ADMIN), emptyList())
                    ),
                    user
                )
            }
        )
    }

    val create = call<FileMetadataAddMetadataRequest, FileMetadataAddMetadataResponse,
        CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val move = call<FileMetadataMoveRequest, FileMetadataMoveResponse,
        CommonErrorMessage>("moveMetadata") {
        httpUpdate(baseContext, "move")
    }

    val delete = call<FileMetadataDeleteRequest, FileMetadataDeleteResponse,
        CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val retrieveAll = call<FileMetadataRetrieveAllRequest, FileMetadataRetrieveAllResponse,
        CommonErrorMessage>("retrieveAll") {
        httpRetrieve(baseContext, "all")
    }

    val approve = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("approve") {
        httpUpdate(baseContext, "approve")
    }

    val reject = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("reject") {
        httpUpdate(baseContext, "reject")
    }

    val browse = call<FileMetadataBrowseRequest, FileMetadataBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)

        documentation {
            summary = "Browses metadata documents"
            description = """
                    Browses in all accessible metadata documents of a user. These are potentially filtered via the flags
                    provided in the request, such as filtering for specific templates. This endpoint should consider any
                    `FileCollection` that the user has access to in the currently active project. Note that this endpoint
                    can only return information about the metadata documents and not the file contents itself. Clients
                    should generally present the output of this has purely metadata documents, they can link to the real
                    files if needed. This should eventually result in either a `browse` or `retrieve` call in the files API.
                """.trimIndent()
        }
    }


    /*
    // TODO Interface TBD
    val search = call<FileMetadataSearchRequest, FileMetadataSearchResponse, CommonErrorMessage>("search") {
        httpSearch(baseContext)
    }
     */
}
