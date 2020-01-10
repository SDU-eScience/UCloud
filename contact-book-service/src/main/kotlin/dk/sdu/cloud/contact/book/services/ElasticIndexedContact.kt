package dk.sdu.cloud.contact.book.services

import java.util.*

data class ElasticIndexedContact(
    val fromUser: String,
    val toUser: String,
    val createdAt: Date,
    val serviceOrigin: String
) {
    companion object {
        val FROM_USER_FIELD = ElasticIndexedContact::fromUser
        val TO_USER_FIELD = ElasticIndexedContact::toUser
        val DATE_FIELD = ElasticIndexedContact::createdAt
        val SERVICE_FIELD = ElasticIndexedContact::serviceOrigin
    }
}
