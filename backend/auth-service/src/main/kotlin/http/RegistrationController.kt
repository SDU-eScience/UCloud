package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.Registration
import dk.sdu.cloud.auth.api.Registrations
import dk.sdu.cloud.auth.services.RegistrationService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.service.Controller
import io.ktor.server.request.*
import io.ktor.util.*

class RegistrationController(
    private val registrationService: RegistrationService,
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(Registrations.retrieve) {
            audit(Unit)

            ok(
                registrationService.findRegistration(request.id)?.toApiModel()
                    ?: throw RPCException("Invalid link. Please try to login again!", HttpStatusCode.NotFound)
            )
        }

        implement(Registrations.complete) {
            audit(Unit)

            val ctx = ctx as HttpCall
            val params = try {
                ctx.call.receiveParameters().toMap()
            } catch (ex: Exception) {
                emptyMap()
            }

            val sessionId = params["sessionId"]?.firstOrNull() ?: ""
            val firstNames = params["firstNames"]?.firstOrNull()
            val lastName = params["lastName"]?.firstOrNull()
            val email = params["email"]?.firstOrNull()

            val organizationFullName = params["organizationFullName"]?.firstOrNull()
            val department = params["department"]?.firstOrNull()
            val researchField = params["researchField"]?.firstOrNull()
            val position = params["position"]?.firstOrNull()

            val request = Registration(
                sessionId,
                firstNames,
                lastName,
                email,
                organizationFullName,
                department,
                researchField,
                position
            )
            registrationService.completeRegistration(request, this)
        }

        implement(Registrations.verifyEmail) {
            audit(Unit)

            registrationService.verifyEmail(request.id, this)
        }

        implement(Registrations.resendVerificationEmail) {
            audit(Unit)

            registrationService.requestNewVerificationEmail(request.id, this)
        }

        return@with
    }
}
