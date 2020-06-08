package dk.sdu.cloud.news.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.news.api.News
import dk.sdu.cloud.news.services.NewsService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext

class NewsController(
    private val db: DBContext,
    private val newsService: NewsService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(News.newPost) {
            newsService.createNewsPost(db, "UCloud", request.title, request.subtitle, request.body, request.showFrom, request.hideFrom, request.category)
            ok(Unit)
        }

        implement(News.togglePostHidden) {
            ok(newsService.togglePostHidden(db, request.id))
        }

        implement(News.listPosts) {
            ok(newsService.listNewsPosts(db, request.normalize(), request.filter, request.withHidden))
        }

        implement(News.listCategories) {
            ok(newsService.listCategories(db))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}