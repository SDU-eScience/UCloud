package dk.sdu.cloud.metadata

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.metadata.http.MetadataController
import dk.sdu.cloud.metadata.http.ProjectsController
import dk.sdu.cloud.metadata.services.ElasticMetadataService
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import kotlin.system.exitProcess

class Server(
    private val configuration: ElasticHostAndPort,
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        log.info("Creating core services")
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val projectService = ProjectService(client)
        val elasticMetadataService =
            with(configuration) {
                ElasticMetadataService(
                    RestHighLevelClient(RestClient.builder(HttpHost(host, port, "http"))),
                    client
                )
            }

        if (micro.commandLineArguments.contains("--init-elastic")) {
            log.info("Initializing elastic search")
            elasticMetadataService.initializeElasticSearch()
            exitProcess(0)
        }

        with(micro.server) {
            configureControllers(
                ProjectsController(
                    projectService,
                    elasticMetadataService
                ),
                MetadataController(
                    elasticMetadataService,
                    elasticMetadataService,
                    elasticMetadataService
                )
            )

            log.info("HTTP server successfully configured!")
        }

        startServices()
    }
}
