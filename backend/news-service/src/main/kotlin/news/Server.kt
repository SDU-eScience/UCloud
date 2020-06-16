package dk.sdu.cloud.news 

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.news.rpc.NewsController
import dk.sdu.cloud.news.services.NewsService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        with(micro.server) {
            configureControllers(
                NewsController(db, NewsService())
            )
        }
        
        startServices()
    }
}