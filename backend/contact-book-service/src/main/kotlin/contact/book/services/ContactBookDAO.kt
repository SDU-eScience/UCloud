package dk.sdu.cloud.contact.book.services

import org.elasticsearch.search.SearchHits

interface ContactBookDAO {
    fun insertContact(
        fromUser: String,
        toUser: String,
        serviceOrigin: String
    )

    fun deleteContact(
        fromUser: String,
        toUser: String,
        serviceOrigin: String
    )

    fun getAllContactsForUser(
        fromUser: String,
        serviceOrigin: String
    ): SearchHits

    fun queryContacts(
        fromUser: String,
        query: String,
        serviceOrigin: String
    ): SearchHits

    fun insertContactsBulk(
        fromUser: String,
        toUser: List<String>,
        serviceOrigin: String
    )
}
