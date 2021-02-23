package dk.sdu.cloud.news

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.news.api.NewsServiceDescription
import dk.sdu.cloud.service.CommonServer

object NewsService : Service {
    override val description = NewsServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    NewsService.runAsStandalone(args)
}
