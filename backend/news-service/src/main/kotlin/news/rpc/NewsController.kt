package dk.sdu.cloud.news.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.news.NewsService
import dk.sdu.cloud.news.api.News
import dk.sdu.cloud.news.api.NewsServiceDescription
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import org.slf4j.Logger

class NewsController : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(News.postMessage) {

            ok(Unit)
        }

        implement(News.hideMessage) {
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}