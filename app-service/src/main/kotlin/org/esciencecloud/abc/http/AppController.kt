package org.esciencecloud.abc.http

import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.esciencecloud.abc.api.HPCApplicationDescriptions
import org.esciencecloud.abc.services.ApplicationDAO
import org.esciencecloud.service.implement

class AppController(private val source: ApplicationDAO) {
    fun configure(routing: Route) = with(routing) {
        implement(HPCApplicationDescriptions.FindByNameAndVersion.description) {
            val result = source.findByNameAndVersion(it.name, it.version)

            if (result == null) error("Not found", HttpStatusCode.NotFound)
            else ok(result)
        }

        implement(HPCApplicationDescriptions.FindAllByName.description) {
            val result = source.findAllByName(it.name)

            if (result.isEmpty()) error(emptyList(), HttpStatusCode.NotFound)
            else ok(result)
        }

        implement(HPCApplicationDescriptions.ListAll.description) {
            ok(source.all())
        }
    }
}
