package dk.sdu.cloud.provider.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.calls.DocStatLine
import dk.sdu.cloud.calls.DocVisualizable
import dk.sdu.cloud.calls.DocVisualization
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import dk.sdu.cloud.calls.UCloudApiStable
import dk.sdu.cloud.calls.visualizeValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface ResourceIncludeFlags {
    val includeOthers: Boolean
    val includeUpdates: Boolean
    val includeSupport: Boolean

    @UCloudApiDoc("Includes `specification.resolvedProduct`")
    val includeProduct: Boolean

    val filterCreatedBy: String?
    val filterCreatedAfter: Long?
    val filterCreatedBefore: Long?
    val filterProvider: String?
    val filterProductId: String?
    val filterProductCategory: String?
    val hideProductId: String?
    val hideProductCategory: String?
    val hideProvider: String?

    @UCloudApiDoc("Filters by the provider ID. The value is comma-separated.")
    val filterProviderIds: String?

    @UCloudApiDoc("Filters by the resource ID. The value is comma-separated.")
    val filterIds: String?
}

@Serializable
@UCloudApiOwnedBy(Resources::class)
data class SimpleResourceIncludeFlags(
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
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

@UCloudApiDoc("The owner of a `Resource`")
@Serializable
@UCloudApiStable
data class ResourceOwner(
    val createdBy: String,
    val project: String?,
) : DocVisualizable {
    override fun visualize(): DocVisualization {
        return if (project != null) {
            DocVisualization.Inline("$createdBy in $project")
        } else {
            DocVisualization.Inline(createdBy)
        }
    }
}

interface ResourceSpecification {
    @UCloudApiDoc("""A reference to the product which backs this `Resource`""")
    val product: ProductReference
}

@UCloudApiDoc(
    """Describes an update to the `Resource`

Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.
"""
)
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
interface ResourceUpdate {
    @UCloudApiDoc("A timestamp referencing when UCloud received this update")
    val timestamp: Long

    @UCloudApiDoc("A generic text message describing the current status of the `Resource`")
    val status: String?
}

@Serializable
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
data class ResourceUpdateAndId<U : ResourceUpdate>(
    val id: String,
    val update: U
)

@Serializable
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
data class ResourceAclEntry(val entity: AclEntity, val permissions: List<Permission>)

@Serializable
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
sealed class AclEntity {
    @Serializable
    @SerialName("project_group")
    @UCloudApiStable
    data class ProjectGroup(
        val projectId: String,
        val group: String
    ) : AclEntity()

    @Serializable
    @SerialName("user")
    @UCloudApiStable
    data class User(val username: String) : AclEntity()
}

@UCloudApiDoc(
    """Describes the current state of the `Resource`

The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.
"""
)
interface ResourceStatus<P : Product, Support : ProductSupport> {
    var resolvedSupport: ResolvedSupport<P, Support>?

    @UCloudApiDoc(
        "The resolved product referenced by `product`.\n\n" +
                "This attribute is not included by default unless `includeProduct` is specified."
    )
    var resolvedProduct: P?
}

@UCloudApiDoc(
    """A `Resource` is the core data model used to synchronize tasks between UCloud and Provider.

For more information go [here](/docs/developer-guide/orchestration/resources.md).
"""
)
interface Resource<P : Product, Support : ProductSupport> : DocVisualizable {
    @UCloudApiDoc(
        """A unique identifier referencing the `Resource`

The ID is unique across a provider for a single resource type."""
    )
    val id: String

    val specification: ResourceSpecification

    // ---

    @UCloudApiDoc("Timestamp referencing when the request for creation was received by UCloud")
    val createdAt: Long

    @UCloudApiDoc("Holds the current status of the `Resource`")
    val status: ResourceStatus<P, Support>

    @UCloudApiDoc(
        """Contains a list of updates from the provider as well as UCloud

Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
resource."""
    )
    val updates: List<ResourceUpdate>

    @UCloudApiDoc("Contains information about the original creator of the `Resource` along with project association")
    val owner: ResourceOwner

    @UCloudApiDoc(
        "Permissions assigned to this resource\n\n" +
                "A null value indicates that permissions are not supported by this resource type."
    )
    val permissions: ResourcePermissions?

    val providerGeneratedId: String? get() = id

    @OptIn(ExperimentalStdlibApi::class)
    override fun visualize(): DocVisualization {
        return DocVisualization.Card(
            "$id (${this::class.simpleName})",
            buildList {
                if (providerGeneratedId != null && providerGeneratedId != id) {
                    add(DocStatLine.of("providerGeneratedId" to visualizeValue(providerGeneratedId)))
                }

                add(DocStatLine.of("owner" to visualizeValue(owner)))
                add(DocStatLine.of("createdAt" to visualizeValue(createdAt)))
                add(DocStatLine.of("specification" to visualizeValue(specification)))
                if (updates.isNotEmpty()) {
                    add(DocStatLine.of("updates" to visualizeValue(updates)))
                }
                if (permissions != null) {
                    add(DocStatLine.of("permissions" to visualizeValue(permissions)))
                }

                add(DocStatLine.of("status" to visualizeValue(status)))
            },
            emptyList()
        )
    }
}
