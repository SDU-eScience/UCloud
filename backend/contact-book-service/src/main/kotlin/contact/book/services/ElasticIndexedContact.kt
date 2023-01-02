package dk.sdu.cloud.contact.book.services

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable

@Serializable
data class ElasticIndexedContact(
    @JsonProperty("fromUser")
    val fromUser: String,
    @JsonProperty("toUser")
    val toUser: String,
    @JsonProperty("createdAt")
    val createdAt: Long,
    @JsonProperty("serviceOrigin")
    val serviceOrigin: String
) {
    companion object {
        val FROM_USER_FIELD = ElasticIndexedContact::fromUser
        val TO_USER_FIELD = ElasticIndexedContact::toUser
        val DATE_FIELD = ElasticIndexedContact::createdAt
        val SERVICE_FIELD = ElasticIndexedContact::serviceOrigin
    }
}
