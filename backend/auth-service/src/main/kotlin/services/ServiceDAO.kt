package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.Loggable

enum class ServiceMode {
    WEB,
    APPLICATION
}

data class Service(
    val name: String,
    val endpoint: String,
    val serviceMode: ServiceMode = ServiceMode.WEB,
    val refreshTokenExpiresAfter: Long? = null
)

object ServiceDAO : Loggable {
    private val inMemoryDb = HashMap<String, Service>()
    override val log = logger()

    fun insert(service: Service): Boolean {
        if (service.name !in inMemoryDb) {
            inMemoryDb[service.name] = service
            return true
        }
        return false
    }

    fun findByName(name: String): Service? {
        return inMemoryDb[name]
    }
}
