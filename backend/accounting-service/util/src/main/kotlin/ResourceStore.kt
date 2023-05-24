package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceIncludeFlags
import kotlinx.serialization.json.JsonObject

data class ResourceDocument<T>(
    var id: Long = 0L,
    var createdAt: Long = 0L,
    var createdBy: Int = 0,
    var project: Int = 0,
    var product: Int = 0,
    var providerId: String? = null,
    var acl: Array<AclEntry> = emptyArray(),
    var data: T? = null,
) {
    data class AclEntry(val entity: Int, val permission: Permission)
}

data class IdCard(
    val uid: Int,
    val groups: IntArray,
    val adminOf: IntArray,
    val activeProject: Int,
)

interface ResourceStore<T> {
    data class BrowseResult(val count: Int, val next: String?)
    data class Update(val update: String, val extra: JsonObject? = null)

    suspend fun create(
        idCard: IdCard,
        product: Int,
        data: T,
        output: ResourceDocument<T>? = null
    ): Long

    suspend fun retrieveBulk(
        idCard: IdCard,
        ids: LongArray,
        output: Array<ResourceDocument<T>>,
    ): Int

    suspend fun retrieve(
        idCard: IdCard,
        id: Long
    ): ResourceDocument<T>? {
        val buf = Array(1) { ResourceDocument<T>() }
        val count = retrieveBulk(idCard, longArrayOf(id), buf)
        if (count == 0) return null
        return buf[0]
    }

    suspend fun browse(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        next: String? = null,
        flags: ResourceIncludeFlags,
    ): BrowseResult

    suspend fun search(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        query: String,
        next: String? = null,
        flags: ResourceIncludeFlags,
    ): BrowseResult

    suspend fun delete(
        idCard: IdCard,
        ids: LongArray,
    )

    suspend fun addUpdate(
        idCard: IdCard,
        id: Long,
        updates: List<Update>,
    )

    suspend fun updateProviderId(
        id: Long,
        providerId: String?,
    )
}


interface IdCardService {
    suspend fun fetchIdCard(actorAndProject: ActorAndProject): IdCard
}
