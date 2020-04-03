package dk.sdu.cloud.app.license.rpc

import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.app.license.services.AppLicenseService
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
            val licenseServer =
                licenseService.getLicenseServer(ctx.securityPrincipal, request.serverId, ctx.securityPrincipal)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(
                LicenseServerWithId(
                    licenseServer.id,
                    licenseServer.name,
                    licenseServer.address,
                    licenseServer.port,
                    licenseServer.license
                )
            )
        }

        implement(AppLicenseDescriptions.updateAcl) {
            /*request.changes.forEach {
                if (it.entity.type == EntityType.PROJECT_GROUP) {
                    ProjectGroups.groupExists(GroupExistsRequest(
                        "project-name",
                        it.entity.id

                    ))
                }
            }*/

            ok(licenseService.updateAcl(request, ctx.securityPrincipal))
        }

        implement(AppLicenseDescriptions.listAcl) {
            ok(licenseService.listAcl(request, ctx.securityPrincipal))
        }

        implement(AppLicenseDescriptions.list) {
            ok(licenseService.listServers(request.tags, ctx.securityPrincipal))
        }

        implement(AppLicenseDescriptions.listAll) {
            ok(licenseService.listAllServers(ctx.securityPrincipal))
        }


        implement(AppLicenseDescriptions.update) {
            ok(
                UpdateServerResponse(
                    licenseService.updateLicenseServer(
                        ctx.securityPrincipal,
                        request,
                        ctx.securityPrincipal
                    )
                )
            )
        }

        implement(AppLicenseDescriptions.delete) {
            ok(licenseService.deleteLicenseServer(ctx.securityPrincipal, request, ctx.securityPrincipal))
        }

        implement(AppLicenseDescriptions.new) {
            ok(NewServerResponse(licenseService.createLicenseServer(request, ctx.securityPrincipal)))
        }

        implement(TagDescriptions.add) {
            ok(licenseService.addTag(request.tag, request.serverId))
        }

        implement(TagDescriptions.delete) {
            ok(licenseService.deleteTag(request.tag, request.serverId))
        }

        implement(TagDescriptions.list) {
            ok(ListTagsResponse(licenseService.listTags(request.serverId)))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
