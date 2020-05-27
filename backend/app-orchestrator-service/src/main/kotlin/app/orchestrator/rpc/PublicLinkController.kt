package app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.PublicLinks
import dk.sdu.cloud.app.orchestrator.services.JobQueryService
import dk.sdu.cloud.app.orchestrator.services.PublicLinkService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext

class PublicLinkController(
    private val db: DBContext,
    private val jobQueryService: JobQueryService,
    private val publicLinks: PublicLinkService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(PublicLinks.create) {
            publicLinks.create(db, ctx.securityPrincipal.username, request.url)
            ok(Unit)
        }

        implement(PublicLinks.delete) {
            publicLinks.delete(db, ctx.securityPrincipal.username, request.url)
            ok(Unit)
        }

        implement(PublicLinks.list) {
            ok(jobQueryService.listPublicUrls(db, ctx.securityPrincipal.username, request.normalize()))
        }

        Unit
    }
}