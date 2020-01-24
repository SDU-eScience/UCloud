package dk.sdu.cloud.app.license.rpc

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

            val licenseServer = licenseService.getLicenseServer(request.serverId, entity)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(
                LicenseServerWithId(
                    licenseServer.id,
                    licenseServer.tag,
                    licenseServer.address,
                    licenseServer.port,
                    licenseServer.license
                )
            )
        }

        implement(AppLicenseDescriptions.updateAcl) {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            ok(licenseService.updateAcl(request, entity))
        }

        implement(AppLicenseDescriptions.listAcl)  {
            ok(licenseService.listAcl(request, ctx.securityPrincipal))
        }

        implement(AppLicenseDescriptions.list)  {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            ok(licenseService.listServers(request.tags, entity))
        }

        implement(AppLicenseDescriptions.listAll)  {
            ok(licenseService.listAllServers(ctx.securityPrincipal))
        }


        implement(AppLicenseDescriptions.update) {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            ok(UpdateServerResponse(licenseService.updateLicenseServer(request, entity)))
        }

        implement(AppLicenseDescriptions.delete) {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            ok(licenseService.deleteLicenseServer(request, entity))
        }

        implement(AppLicenseDescriptions.new) {
            val entity = UserEntity(
                ctx.securityPrincipal.username,
                EntityType.USER
            )

            ok(NewServerResponse(licenseService.createLicenseServer(request, entity)))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
