package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.Loggable

data class Service(
    val name: String,
    val endpoint: String,
    val refreshTokenExpiresAfter: Long? = null,
    val endpointAcceptsStateViaCookie: Boolean = false
)

object ServiceDAO : Loggable {
    private val inMemoryDb = HashMap<String, Service>()
    override val log = logger()

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

