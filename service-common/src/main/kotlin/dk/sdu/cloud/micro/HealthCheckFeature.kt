package dk.sdu.cloud.micro

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.client.RequestOptions
import java.io.IOException
import java.lang.Exception
import kotlin.system.exitProcess
import kotlin.test.assertEquals

private object HealthCheckDescriptions : CallDescriptionContainer("healthcheck") {
    const val baseContext = "/status"

    val status = call<Unit, Unit, CommonErrorMessage>("status") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }
        }
    }

}

class HealthCheckFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val serverFeature = ctx.featureOrNull(ServerFeature) ?: return
        with(serverFeature.server) {
            implement(HealthCheckDescriptions.status) {
                val redisFeature = ctx.featureOrNull(RedisFeature)
                val hibernateFeature = ctx.featureOrNull(HibernateFeature)
                val elasticFeature = ctx.featureOrNull(ElasticFeature)

                if (redisFeature != null) {
                    log.info("Testing Redis")
                    var response = ""
                    try {
                        val conn = ctx.redisConnectionManager.getConnection()
                        response = conn.ping().get()
                    } catch (ex: Exception) {
                        throw RPCException("Redis is not working: EX: ${ex.stackTraceToString()}",
                            HttpStatusCode.InternalServerError
                        )
                    }
                    log.info(response)
                    if ( response != "PONG" ) {
                        throw RPCException("Redis is not working", HttpStatusCode.InternalServerError)
                    }
                }

                if (hibernateFeature != null) {
                    log.info("Testing Hibernate")
                    val result = try {
                        ctx.hibernateDatabase.withTransaction { session ->
                            session.createNativeQuery(
                                "SELECT 1"
                            ).resultList
                        }
                    } catch (ex: Exception) {
                        throw RPCException("Hibernate is not working, EX: ${ex.stackTraceToString()}",
                            HttpStatusCode.InternalServerError
                        )
                    }
                    log.info(result.first().toString())
                    if (result.isEmpty()) {
                        throw RPCException("Hibernate is not working", HttpStatusCode.InternalServerError)                    }
                }

                if (elasticFeature != null) {
                    log.info("Testing Elastic")
                    val client = ctx.elasticHighLevelClient
                    val request = ClusterHealthRequest()
                    try {
                        val response = client.cluster().health(request, RequestOptions.DEFAULT)
                        log.info(response.status.name)
                    } catch (ex: Exception) {
                        log.error(ex.stackTraceToString())
                        throw RPCException("Elastic is not working", HttpStatusCode.InternalServerError)                    }
                }
                ok(Unit)
            }
        }
    }

    companion object Feature : MicroFeatureFactory<HealthCheckFeature, Unit>, Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<HealthCheckFeature> = MicroAttributeKey("health-check-feature")
        override fun create(config: Unit): HealthCheckFeature = HealthCheckFeature()
    }
}
