package dk.sdu.cloud.storage.model

import dk.sdu.cloud.service.JsonSerde.jsonSerde
import org.apache.kafka.common.serialization.Serdes

object AccessControlProcessor {
    /**
     * Updates the ACL of a single storage-entry.
     *
     * The [UpdateACLRequest.path] specifies which entry this update should apply to. If the entry is a
     * [FileType.DIRECTORY] then the [UpdateACLRequest.recursive] flag may be used to indicate that this action should
     * also apply to all of its children. If the entry is not a [FileType.DIRECTORY] then the
     * [UpdateACLRequest.recursive] flag will be ignored.
     *
     * The ACL will be updated using the entry in [UpdateACLRequest.newEntry]. An entry with a value of
     * [AccessRight.NONE] will remove the rights from a given [Entity]. Any other [AccessRight] indicates that the
     * user should have its permissions updated in the list. The list sent in this update request does not have to
     * include all entries. As a result, any existing entry in the active list, not mentioned in the update request,
     * will simply remain as it currently is.
     */
    val UpdateACL = RequestResponseStream<String, UpdateACLRequest>(
            "acl.update",
            Serdes.String(), jsonSerde(), jsonSerde()
    )
}

data class UpdateACLRequest(
        val path: String,
        val newEntry: AccessEntry,
        val recursive: Boolean
)