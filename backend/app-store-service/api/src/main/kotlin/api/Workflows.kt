package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
enum class WorkflowLanguage {
    JINJA2,
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class Workflow(
    val id: String,
    val createdAt: Long,
    val owner: Owner,
    val specification: Specification,
    val status: Status,
    val permissions: Permissions,
) {
    @Serializable
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    data class Owner(
        val createdBy: String,
        val project: String?,
    )

    @Serializable
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    data class Specification(
        @UCloudApiDoc("""
            The application name this workflow applies to.
            
            This value is only meaningful in the context of create, retrieve and browse and can be empty in all other
            contexts.
        """)
        val applicationName: String,
        val language: WorkflowLanguage = WorkflowLanguage.JINJA2,
        val init: String? = null,
        val job: String? = null,
        val inputs: List<ApplicationParameter> = emptyList(),
        val readme: String? = null,
    )

    @Serializable
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    data class Permissions(
        var openToWorkspace: Boolean,
        var myself: List<Permission>,
        var others: List<AclEntry>,
    )

    @Serializable
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    enum class Permission {
        READ,
        WRITE,
        ADMIN
    }

    @Serializable
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    data class AclEntry(
        val group: String,
        val permission: Permission,
    )

    @Serializable
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    class Status(
        val path: String,
    )
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object Workflows : CallDescriptionContainer("hpc.apps.workflows") {
    const val baseContext = "/api/hpc/workflows/"

    val create = Create.request
    val browse = Browse.request
    val rename = Rename.request
    val delete = Delete.request
    val updateAcl = UpdateAcl.request
    val retrieve = Retrieve.request

    object Create {
        @Serializable
        data class Request(
            val path: String,
            val allowOverwrite: Boolean,
            val specification: Workflow.Specification,
        )

        val request = call(
            "create",
            BulkRequest.serializer(Request.serializer()),
            BulkResponse.serializer(FindByStringId.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpCreate(baseContext)
            }
        )
    }

    object Browse {
        @Serializable
        data class Request(
            val filterApplicationName: String,
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : WithPaginationRequestV2

        val request = call(
            "browse",
            Request.serializer(),
            PageV2.serializer(Workflow.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpBrowse(baseContext)
            }
        )
    }

    object Rename {
        @Serializable
        data class Request(
            val id: String,
            val newPath: String,
            val allowOverwrite: Boolean,
        )

        val request = call(
            "rename",
            BulkRequest.serializer(Request.serializer()),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "rename")
            }
        )
    }

    object Delete {
        val request = call(
            "delete",
            BulkRequest.serializer(FindByStringId.serializer()),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpDelete(baseContext)
            }
        )
    }

    object UpdateAcl {
        @Serializable
        data class Request(
            val id: String,
            val isOpenForWorkspace: Boolean,
            val entries: List<Workflow.AclEntry>,
        )

        val request = call(
            "updateAcl",
            BulkRequest.serializer(Request.serializer()),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateAcl")
            }
        )
    }

    object Retrieve {
        val request = call(
            "retrieve",
            FindByStringId.serializer(),
            Workflow.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext)
            }
        )
    }
}
