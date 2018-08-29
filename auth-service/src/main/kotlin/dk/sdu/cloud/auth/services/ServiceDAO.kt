package dk.sdu.cloud.auth.services

import org.slf4j.LoggerFactory

data class Service(val name: String, val endpoint: String)

object ServiceDAO {
    private val inMemoryDb = HashMap<String, Service>()
    private val log = LoggerFactory.getLogger(ServiceDAO::class.java)

    init {
        insert(Service("web", "https://cloud.sdu.dk/api/auth-callback"))
        insert(Service("sync", "https://cloud.sdu.dk/api/sync-callback"))
        insert(Service("local-dev", "http://localhost:9000/api/auth-callback"))

        insert(Service("web-csrf", "https://cloud.sdu.dk/api/auth-callback-csrf"))
        insert(Service("local-dev-csrf", "http://localhost:9000/api/auth-callback-csrf"))
    }

    fun insert(service: Service): Boolean {
        log.debug("insert($service)")
        if (service.name !in inMemoryDb) {
            inMemoryDb[service.name] = service
            return true
        }
        return false
    }

    fun findByName(name: String): Service? {
        log.debug("findByName($name)")
        return inMemoryDb[name].also { log.debug("Returning $it") }
    }
}

