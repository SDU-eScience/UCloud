package dk.sdu.cloud.metadata.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class License(val name: String, val link: String, val identifier: String)

object Licenses {
    private val mapper = jacksonObjectMapper()
    private val licenses: List<License> by lazy {
        mapper.readValue<List<License>>(
            Licenses.javaClass.classLoader.getResourceAsStream("licenses.json")
        )
    }

    private val licensesByIdentifier = licenses.associateBy { it.identifier }

    operator fun get(identifier: String): License? = getLicenseByIdentifier(identifier)

    fun getLicenseByIdentifier(identifier: String): License? {
        return licensesByIdentifier[identifier]
    }

    fun isValidLicenseIdentifier(identifier: String): Boolean = getLicenseByIdentifier(identifier) != null
}
