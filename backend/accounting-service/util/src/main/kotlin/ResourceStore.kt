package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.service.Time
import kotlinx.serialization.json.JsonElement

data class ResourceDocumentUpdate(
    val update: String?,
    val extra: JsonElement? = null,
    val createdAt: Long = Time.now(),
)

data class ResourceDocument<T>(
    var id: Long = 0L,
    var createdAt: Long = 0L,
    var createdBy: Int = 0,
    var project: Int = 0,
    var product: Int = 0,
    var providerId: String? = null,
    var acl: ArrayList<AclEntry> = ArrayList(8),
    var update: Array<ResourceDocumentUpdate?> = arrayOfNulls(64),
    var data: T? = null,
) {
    data class AclEntry(val entity: Int, val isUser: Boolean, val permission: Permission)
}

sealed class IdCard {
    data class User(
        val uid: Int,
        val groups: IntArray,
        val adminOf: IntArray,
        val activeProject: Int,
    ) : IdCard()

    data class Provider(
        val name: String,
        val providerOf: IntArray,
    ) : IdCard()

    object System : IdCard()
}

