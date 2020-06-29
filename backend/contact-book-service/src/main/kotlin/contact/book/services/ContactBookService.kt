package dk.sdu.cloud.contact.book.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.contact.book.api.ServiceOrigin
import dk.sdu.cloud.defaultMapper
import io.ktor.http.HttpStatusCode

class ContactBookService(private val elasticDao: ContactBookElasticDao) {

    fun insertContact(fromUser: String, toUser: List<String>, serviceOrigin: ServiceOrigin) {
        val sanitizedList = toUser.filter { !it.isNullOrBlank() }
        when {
            sanitizedList.isEmpty() -> throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            sanitizedList.size == 1 -> elasticDao.insertContact(fromUser, sanitizedList.first(), serviceOrigin.name)
            else -> elasticDao.insertContactsBulk(fromUser, sanitizedList, serviceOrigin.name)
        }
    }

    fun deleteContact(fromUser: String, toUser: String, serviceOrigin: ServiceOrigin) {
        elasticDao.deleteContact(fromUser, toUser, serviceOrigin.name)
    }

    fun listAllContactsForUser(fromUser: String, serviceOrigin: ServiceOrigin): List<String> {
        val allContacts = elasticDao.getAllContactsForUser(fromUser, serviceOrigin.name)
        return allContacts.hits.map {
            val hit = defaultMapper.readValue<ElasticIndexedContact>(it.sourceAsString)
            hit.toUser
        }
    }

    fun queryUserContacts(fromUser: String, query: String, serviceOrigin: ServiceOrigin): List<String> {
        //Removes all whitespace from string
        val normalizedQuery = query.replace("\\s".toRegex(), "")
        if (normalizedQuery.isNullOrBlank()) {
            return emptyList()
        }
        val matchingContacts = elasticDao.queryContacts(fromUser, normalizedQuery, serviceOrigin.name)
        return matchingContacts.hits.map {
            val hit = defaultMapper.readValue<ElasticIndexedContact>(it.sourceAsString)
            hit.toUser
        }
    }
}
