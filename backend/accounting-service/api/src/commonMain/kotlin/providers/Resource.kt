package dk.sdu.cloud.provider.api

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.UCloudApiDoc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@UCloudApiDoc("""Contains information related to the accounting/billing of a `Resource`

Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
to be charged a different price than newly launched products.""")
interface ResourceBilling {
    @UCloudApiDoc("The price per unit. This can differ from current price of `Product`")
    val pricePerUnit: Long
    @UCloudApiDoc("Amount of credits charged in total for this `Resource`")
    val creditsCharged: Long

    @Serializable
    object Free : ResourceBilling {
        override val creditsCharged: Long = 0L
        override val pricePerUnit: Long = 0L
    }
}

@UCloudApiDoc("The owner of a `Resource`")
interface ResourceOwner {
    val createdBy: String
    val project: String?
}

@Serializable
data class SimpleResourceOwner(
    override val createdBy: String,
    override val project: String?,
) : ResourceOwner

interface ResourceSpecification {
    @UCloudApiDoc("""A reference to the product which backs this `Resource`

All `Resource`s must be backed by a `Product`, even `Resource`s which are free to consume. If a `Resource` is free to
consume the backing `Product` should simply have a `pricePerUnit` of 0.""")
    val product: ProductReference?

}

@UCloudApiDoc("""Describes an update to the `Resource`

Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.
""")
interface ResourceUpdate {
    @UCloudApiDoc("A timestamp referencing when UCloud received this update")
    val timestamp: Long
    @UCloudApiDoc("A generic text message describing the current status of the `Resource`")
    val status: String?
}

@Serializable
open class ResourceAclEntry<Permission>(val entity: AclEntity, val permissions: List<Permission>)

@Serializable
sealed class AclEntity {
    @Serializable
    @SerialName("project_group")
    data class ProjectGroup(
        val projectId: String,
        val group: String
    ) : AclEntity()

    @Serializable
    @SerialName("user")
    data class User(val username: String) : AclEntity()
}

@UCloudApiDoc("""Describes the current state of the `Resource`

The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.
""")
interface ResourceStatus

@UCloudApiDoc("""A `Resource` is the core data model used to synchronize tasks between UCloud and a [provider](/backend/provider-service/README.md).

`Resource`s provide instructions to providers on how they should complete a given task. Examples of a `Resource`
include: [Compute jobs](/backend/app-orchestrator-service/README.md), HTTP ingress points and license servers. For
example, a (compute) `Job` provides instructions to the provider on how to start a software computation. It also gives
the provider APIs for communicating the status of the `Job`.

All `Resource` share a common interface and data model. The data model contains a specification of the `Resource`, along
with metadata, such as: ownership, billing and status.

`Resource`s are created in UCloud when a user requests it. This request is verified by UCloud and forwarded to the
provider. It is then up to the provider to implement the functionality of the `Resource`.

![](/backend/provider-service/wiki/resource_create.svg)

__Figure:__ UCloud orchestrates with the provider to create a `Resource`
""")
interface Resource<Permission> {
    @UCloudApiDoc("""A unique identifier referencing the `Resource`

The ID is unique across a provider for a single resource type.""")
    val id: String

    val specification: ResourceSpecification

    // ---

    @UCloudApiDoc("Timestamp referencing when the request for creation was received by UCloud")
    val createdAt: Long

    @UCloudApiDoc("Holds the current status of the `Resource`")
    val status: ResourceStatus

    @UCloudApiDoc("""Contains a list of updates from the provider as well as UCloud

Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
resource.""")
    val updates: List<ResourceUpdate>

    // ---
    @UCloudApiDoc("Contains information related to billing information for this `Resource`")
    val billing: ResourceBilling

    // ---

    @UCloudApiDoc("Contains information about the original creator of the `Resource` along with project association")
    val owner: ResourceOwner

    @UCloudApiDoc("An ACL for this `Resource`")
    @Deprecated("Replace with permissions")
    val acl: List<ResourceAclEntry<Permission>>?

    @UCloudApiDoc("Permissions assigned to this resource\n\n" +
        "A null value indicates that permissions are not supported by this resource type.")
    val permissions: ResourcePermissions?
}
