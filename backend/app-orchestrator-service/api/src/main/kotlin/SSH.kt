package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A public key belonging to a UCloud user intended for SSH purposes")
data class SSHKey(
    @UCloudApiDoc("An opaque and unique identifier representing this SSH key")
    val id: String,

    @UCloudApiDoc("The UCloud username of the user who owns this key")
    val owner: String,

    @UCloudApiDoc("Timestamp for when this key was created in UCloud")
    val createdAt: Long,

    @UCloudApiDoc("""
        A fingerprint of the key
        
        This is used to aid end-users identify the key more easily. The fingerprint will, in most cases, contain a
        cryptographic hash along with any additional comments the key might have associated with it. The format of this
        property is not stable.
    """)
    val fingerprint: String,

    @UCloudApiDoc("Contains the user-specified part of the key")
    val specification: Spec,
) {
    @Serializable
    data class Spec(
        @UCloudApiDoc("Single line human-readable description, not unique")
        val title: String,
        @UCloudApiDoc("The public part of an SSH key")
        val key: String,
    ) {
        init {
            checkSingleLine(this::title, title)
            checkSingleLine(this::key, key)
        }
    }
}

@Serializable
data class SSHKeysBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

object SSHKeys : CallDescriptionContainer("ssh_keys") {
    const val baseContext = "/api/ssh"

    val create = call(
        "create",
        BulkRequest.serializer(SSHKey.Spec.serializer()),
        BulkResponse.serializer(FindByStringId.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpCreate(baseContext)
    }

    val retrieve = call(
        "retrieve",
        FindByStringId.serializer(),
        SSHKey.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpRetrieve(baseContext)
    }

    val browse = call(
        "browse",
        SSHKeysBrowseRequest.serializer(),
        PageV2.serializer(SSHKey.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpBrowse(baseContext)
    }

    val delete = call(
        "delete",
        BulkRequest.serializer(FindByStringId.serializer()),
        Unit.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpDelete(baseContext)
    }
}

@Serializable
data class SSHKeysControlBrowseRequest(
    val usernames: List<String>,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

// NOTE(Dan): Providers can also retrieve the keys that are relevant for a job by going through
// JobsControl.browseSshKeys.
object SSHKeysControl : CallDescriptionContainer("ssh_keys.control") {
    const val baseContext = "/api/ssh/control"

    val browse = call(
        "browse",
        SSHKeysControlBrowseRequest.serializer(),
        PageV2.serializer(SSHKey.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(baseContext, "browse", roles = Roles.PROVIDER)
    }
}
