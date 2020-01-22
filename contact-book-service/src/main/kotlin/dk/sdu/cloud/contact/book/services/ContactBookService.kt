package dk.sdu.cloud.contact.book.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import io.ktor.http.HttpStatusCode

class ContactBookService(private val elasticDAO: ContactBookElasticDAO) {

    fun insertContact(fromUser: String, toUser: List<String>, serviceOrigin: String) {
        val sanitizedList = toUser.filter { !it.isNullOrBlank() }
        when {
            sanitizedList.isEmpty() -> throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            sanitizedList.size == 1 -> elasticDAO.insertContact(fromUser, sanitizedList.first(), serviceOrigin)
            else -> elasticDAO.insertContactsBulk(fromUser, sanitizedList, serviceOrigin)
        }
    }

    fun deleteContact(fromUser: String, toUser: String, serviceOrigin: String) {
        elasticDAO.deleteContact(fromUser, toUser, serviceOrigin)
    }

    fun listAllContactsForUser(fromUser: String, serviceOrigin: String): List<String> {
        val allContacts = elasticDAO.getAllContactsForUser(fromUser, serviceOrigin)
        return allContacts.hits.map {
            val hit = defaultMapper.readValue<ElasticIndexedContact>(it.sourceAsString)
            hit.toUser
        }
    }

    fun queryUserContacts(fromUser: String, query: String, serviceOrigin: String): List<String> {
        //Removes all whitespace from string
        val normalizedQuery = query.replace("\\s".toRegex(), "")
        if (normalizedQuery.isNullOrBlank()) {
            return emptyList()
        }
        val matchingContacts = elasticDAO.queryContacts(fromUser, normalizedQuery, serviceOrigin)
        return matchingContacts.hits.map {
            val hit = defaultMapper.readValue<ElasticIndexedContact>(it.sourceAsString)
            hit.toUser
        }
    }
}
