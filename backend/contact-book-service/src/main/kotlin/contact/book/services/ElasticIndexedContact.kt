package dk.sdu.cloud.contact.book.services

data class ElasticIndexedContact(
    val fromUser: String,
    val toUser: String,
    val createdAt: Long,
    val serviceOrigin: String
) {
    companion object {
        val FROM_USER_FIELD = ElasticIndexedContact::fromUser
        val TO_USER_FIELD = ElasticIndexedContact::toUser
        val DATE_FIELD = ElasticIndexedContact::createdAt
        val SERVICE_FIELD = ElasticIndexedContact::serviceOrigin
    }
}
