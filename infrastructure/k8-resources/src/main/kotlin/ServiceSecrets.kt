package dk.sdu.cloud.k8

import com.fasterxml.jackson.module.kotlin.readValue
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Secret
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.util.toByteArray
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

class ServiceSecrets(val name: String) : KubernetesResource {
    override fun DeploymentContext.isUpToDate(): Boolean =
        client.secrets().inNamespace(namespace).withName("$name-psql").get() != null

    override fun DeploymentContext.create() {
        runBlocking {
            // Refresh token
            val jwt = fetchJwt(environment)
            val tokenAndHost = config.getValue(environment)

            val resp = httpClient.post<HttpResponse>("${tokenAndHost.host}/auth/users/register") {
                header("Authorization", "Bearer $jwt")
                body = TextContent(
                    defaultMapper.writeValueAsString(
                        mapOf(
                            "username" to "_$name",
                            "password" to "not-used",
                            "email" to "not-used@example.com",
                            "role" to "SERVICE"
                        )
                    ), ContentType.Application.Json
                )
            }

            if (!resp.status.isSuccess()) throw IllegalStateException("User registration failed! ${resp.status}")
            val refreshToken =
                defaultMapper.readValue<List<Map<String, Any?>>>(resp.content.toByteArray())[0]["refreshToken"] as String

            client.secrets().inNamespace(namespace).create(Secret().apply {
                metadata = ObjectMeta().apply {
                    name = "${this@ServiceSecrets.name}-refresh-token"
                }

                stringData = mapOf(
                    "refresh.yml" to """
                        ---
                        refreshToken: $refreshToken
                    """.trimIndent()
                )
            })
        }

        run {
            // Database secrets
            val proxyPod = client.pods().inNamespace("stolon").list().items.find { it.metadata.name.contains("proxy") }
                ?: throw IllegalStateException("Could not find stolon proxy")

            val stolonPassword =
                client.secrets()
                    .inNamespace("stolon")
                    .withName("stolon")
                    .get()
                    ?.data
                    ?.get("pg_su_password")
                    ?.let { Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }

            val dbUser = name.replace('-', '_')
            val schema = dbUser
            val generatedPassword = UUID.randomUUID().toString()

            fun executeStatement(statement: String) {
                val exec =
                    client.pods().inNamespace("stolon").withName(proxyPod.metadata.name).execWithDefaultListener(
                        listOf(
                            "psql",
                            "-c",
                            statement,
                            "postgresql://stolon:${stolonPassword}@localhost/postgres"
                        ),
                        attachStderr = true,
                        attachStdout = true
                    )

                println(exec.stdout?.bufferedReader()?.readText())
                println(exec.stderr?.bufferedReader()?.readText())
            }

            executeStatement("drop owned by \"$dbUser\" cascade;")
            executeStatement("drop schema \"$schema\";")
            executeStatement("drop user \"$schema\";")
            executeStatement("create user \"$dbUser\" password '$generatedPassword';")
            executeStatement("create schema \"$schema\" authorization \"$dbUser\";")

            client.secrets().inNamespace(namespace).create(Secret().apply {
                metadata = ObjectMeta().apply {
                    name = "${this@ServiceSecrets.name}-psql"
                }

                stringData = mapOf(
                    "db.yml" to """
                        ---
                        hibernate:
                            database:
                                profile: PERSISTENT_POSTGRES
                                credentials:
                                    username: $dbUser
                                    password: $generatedPassword
                                    
                    """.trimIndent()
                )
            })
        }
    }

    override fun DeploymentContext.delete() {
        TODO("not implemented")
    }

    override fun toString() = "ServiceSecrets($name)"

    companion object {
        data class TokenAndHost(val refreshToken: String, val host: String)

        private val httpClient = HttpClient(Apache)

        private val k8ConfigFile = File(System.getProperty("user.home"), ".sducloud/k8-config.json")
        private var config: Map<Environment, TokenAndHost> = emptyMap()

        private fun fetchJwt(environment: Environment): String {
            if (environment !in config) {
                config = try {
                    defaultMapper.readValue(k8ConfigFile)
                } catch (ex: Throwable) {
                    // File probably not found
                    ex.printStackTrace()
                    emptyMap()
                }
            }

            val tokenAndHost = config[environment]
            if (tokenAndHost == null) {
                val scanner = Scanner(System.`in`)
                print("Hostname is required for service creation (ex: https://cloud.sdu.dk): ")
                val hostName = scanner.nextLine().removePrefix("https://").removePrefix("http://").let { "https://$it" }
                print("Refresh token is required for service creation: ")
                val refreshToken = scanner.nextLine()

                config = config + mapOf(environment to TokenAndHost(refreshToken, hostName))
                k8ConfigFile.writeText(defaultMapper.writeValueAsString(config))
                return fetchJwt(environment)
            }

            return runBlocking {
                val resp = httpClient.post<HttpResponse>("${tokenAndHost.host}/auth/refresh") {
                    header("Authorization", "Bearer ${tokenAndHost.refreshToken}")
                }

                if (resp.status == HttpStatusCode.Forbidden) {
                    throw IllegalStateException("Bad refreshToken found in configuration: $tokenAndHost")
                }

                defaultMapper.readValue<Map<String, Any?>>(resp.content.toByteArray())["accessToken"] as String
            }
        }
    }
}
