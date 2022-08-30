package dk.sdu.cloud.provider.api

import dk.sdu.cloud.calls.ExperimentalLevel
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@UCloudApiDoc(
    """
        The UCloud permission model

        This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of
        standard permissions that can be applied to a resource and its associated operations.
    """
)
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiOwnedBy(Resources::class)
enum class Permission(val canBeGranted: Boolean) {
    @UCloudApiDoc(
        """
            Grants an entity access to all read-based operations
            
            Read-based operations must not alter the state of a resource. Typical examples include the `browse` and
            `retrieve*` endpoints.
        """
    )
    READ(true),
    @UCloudApiDoc(
        """
            Grants an entity access to all write-based operations
            
            Write-based operations are allowed to alter the state of a resource. This permission is required for most
            `update*` endpoints.
        """
    )
    EDIT(true),
    @UCloudApiDoc(
        """
            Grants an entity access to special privileged operations
            
            This permission will allow the entity to perform any action on the resource, unless the operation
            specifies otherwise. This operation is, for example, used for updating the permissions attached to a
            resource.
        """
    )
    ADMIN(false),
    @UCloudApiDoc(
        """
            Grants an entity access to special privileged operations specific to a provider
        """
    )
    PROVIDER(false)
}

@Serializable
data class ResourcePermissions(
    @UCloudApiDoc("The permissions that the requesting user has access to")
    var myself: List<Permission>,
    @UCloudApiDoc("The permissions that other users might have access to\n\n" +
        "This value typically needs to be included through the `includeFullPermissions` flag")
    var others: List<ResourceAclEntry>?
)

@Serializable
data class UpdatedAcl(
    val id: String,
    val added: List<ResourceAclEntry>,
    val deleted: List<AclEntity>,
)

@Serializable
@UCloudApiOwnedBy(Resources::class)
data class UpdatedAclWithResource<Res : Resource<*, *>>(
    val resource: Res,
    val added: List<ResourceAclEntry>,
    val deleted: List<AclEntity>,
)
