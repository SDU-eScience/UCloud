package dk.sdu.cloud.provider.api

import dk.sdu.cloud.calls.ExperimentalLevel
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PermissionSerializer::class)
@UCloudApiDoc("""Base type for all permissions of the UCloud authorization model
    
This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of standard
permissions that can be applied to a resource and its associated operations.

1. `READ`: Grants an entity access to all read-based operations. Read-based operations must not alter the state of a
resource. Typical examples include the `browse` and `retrieve*` endpoints.
2. `EDIT`: Grants an entity access to all write-based operations. Write-based operations are allowed to alter the state
of a resource. This permission is required for most `update*` endpoints.
3. `ADMIN`: Grants an entity access to special privileged operations. This permission will allow the entity to perform
any action on the resource, unless the operation specifies otherwise. This operation is, for example, used for updating
the permissions attached to a resource.

Apart from the standard permissions, a resource may define additional permissions. These are documented along with
the resource and related operations.
""")
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
sealed class Permission(val name: String) {
    open val canBeGranted: Boolean = true
    object Read : Permission("READ")
    object Edit : Permission("EDIT")

    object Admin : Permission("ADMIN") {
        override val canBeGranted = false
    }

    object Provider : Permission("PROVIDER") {
        override val canBeGranted = false
    }

    companion object {
        fun fromString(name: String): Permission {
            return when (name) {
                Read.name -> Read
                Edit.name -> Edit
                Admin.name -> Admin
                Provider.name -> Provider
                else -> throw IllegalStateException("Unknown permission: $name")
            }
        }
    }
}

object PermissionSerializer : KSerializer<Permission> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Permission", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Permission {
        return Permission.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Permission) {
        encoder.encodeString(value.name)
    }
}

@Serializable
data class ResourcePermissions(
    @UCloudApiDoc("The permissions that the requesting user has access to")
    val myself: List<Permission>,
    @UCloudApiDoc("The permissions that other users might have access to\n\n" +
        "This value typically needs to be included through the `includeFullPermissions` flag")
    val others: List<ResourceAclEntry>?
)

@Serializable
data class UpdatedAcl(
    val id: String,
    val added: List<ResourceAclEntry>,
    val deleted: List<AclEntity>,
)

@Serializable
data class UpdatedAclWithResource<Res : Resource<*, *>>(
    val resource: Res,
    val added: List<ResourceAclEntry>,
    val deleted: List<AclEntity>,
)