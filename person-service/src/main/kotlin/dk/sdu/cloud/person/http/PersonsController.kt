package dk.sdu.cloud.person.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.person.api.PersonDescriptions
import dk.sdu.cloud.person.services.PersonsDAO
import dk.sdu.cloud.person.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class PersonsController(private val projects: PersonsDAO) {
    fun configure(route: Route): Unit = with(route) {
        implement(PersonDescriptions.findMyPersons) {
            val who = call.request.validatedPrincipal.subject
            ok(projects.findAllMyProjects(who))
        }

        implement(PersonDescriptions.findById) {
            val person = projects.findById(it.id)
            if (person == null) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            } else {
                ok(person)
            }
        }

        implement(PersonDescriptions.findByIdWithMembers) {
            val personWithMembers = projects.findByIdWithMembers(it.id)
            if (personWithMembers == null) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            } else {
                ok(personWithMembers)
            }
        }
    }
}