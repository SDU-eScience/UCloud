package dk.sdu.cloud.news.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.news.api.News
import dk.sdu.cloud.news.services.NewsService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.async.DBContext

class NewsController(
    private val db: DBContext,
    private val newsService: NewsService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(News.newPost) {
            newsService.createNewsPost(
                db,
                "UCloud",
                request.title,
                request.subtitle,
                request.body,
                request.showFrom,
                request.hideFrom,
                request.category
            )
            ok(Unit)
        }

        implement(News.togglePostHidden) {
            ok(newsService.togglePostHidden(db, request.id))
        }

        implement(News.listPosts) {
            ok(
                newsService.listNewsPosts(
                    db,
                    request.normalize(),
                    request.filter,
                    request.withHidden,
                    ctx.securityPrincipal.role in Roles.PRIVILEGED
                )
            )
        }

        implement(News.listCategories) {
            ok(newsService.listCategories(db))
        }

        implement(News.listDowntimes) {
            ok(
                newsService.listNewsPosts(
                    db, NormalizedPaginationRequest(10, 0), "downtime",
                    withHidden = false,
                    userIsAdmin = false
                )
            )
        }

        implement(News.getPostById) {
            ok(newsService.getPostById(db, request.id))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}