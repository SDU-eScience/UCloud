package dk.sdu.cloud.app.license.rpc

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.app.license.services.acl.EntityType
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import org.hibernate.Session

class AppLicenseController(appLicenseService: AppLicenseService<Session>) : Controller {
    private val licenseService = appLicenseService
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppLicenseDescriptions.get) {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            try {
                val licenseServer = licenseService.getLicenseServer(request.licenseId, entity)
                ok(
                    ApplicationLicenseServer(
                        licenseServer.name,
                        licenseServer.version,
                        licenseServer.address,
                        licenseServer.license
                    )
                )
            } catch (e: RPCException) {
                when (e.httpStatusCode) {
                    HttpStatusCode.NotFound -> error(
                        CommonErrorMessage("Problems fetching the application license server"),
                        HttpStatusCode.NotFound
                    )
                    HttpStatusCode.Unauthorized -> error(
                        CommonErrorMessage("Problems fetching the application license server"),
                        HttpStatusCode.Unauthorized
                    )
                }
            }
        }

        implement(AppLicenseDescriptions.updateAcl) {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            licenseService.updateAcl(request, entity)
        }

        implement(AppLicenseDescriptions.save) {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            try {
                licenseService.saveLicenseServer(request, entity)
            } catch (e: RPCException) {
                when (e.httpStatusCode) {
                    HttpStatusCode.Unauthorized ->
                        error(
                            CommonErrorMessage("Unauthorized to save application license server"),
                            HttpStatusCode.Unauthorized
                        )
                }
            }

        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}