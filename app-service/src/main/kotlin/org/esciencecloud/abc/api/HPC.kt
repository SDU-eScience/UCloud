package org.esciencecloud.abc.api

import io.netty.handler.codec.http.HttpMethod
import org.esciencecloud.client.AuthenticatedCloud
import org.esciencecloud.client.KafkaCallDescriptionBundle
import org.esciencecloud.client.RESTDescriptions
import org.esciencecloud.client.bindEntireRequestFromBody

object HPCApplications : RESTDescriptions() {
    val baseContext = "/hpc/apps/"

    data class FindAllByName(val name: String) {
        companion object {
            val description = callDescription<FindAllByName, List<ApplicationDescription>, List<ApplicationDescription>> {
                path {
                    using(baseContext)
                    +boundTo(FindAllByName::name)
                }
            }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
    }

    data class FindByNameAndVersion(val name: String, val version: String) {
        companion object {
            val description = callDescription<FindByNameAndVersion, ApplicationDescription, String> {
                path {
                    using(baseContext)
                    +boundTo(FindByNameAndVersion::name)
                    +boundTo(FindByNameAndVersion::version)
                }
            }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
    }

    object ListAll {
        // TODO Pagination will be required for this
        val description = callDescription<Unit, List<ApplicationDescription>, List<ApplicationDescription>> {
            path { using(baseContext) }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(Unit).call(cloud)
    }

    enum class TestEnum {
        A,
        B,
        C,
        D
    }

    data class Test(val name: String, val notRequired: Int?, val enum: TestEnum?) {
        companion object {
            val description = callDescription<Test, Test, Map<String, Any?>> {
                path {
                    using(baseContext)
                    +"test"
                    +boundTo(Test::name)
                    +boundTo(Test::notRequired)
                    +boundTo(Test::enum)
                }
            }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
    }

    sealed class AppRequest {
        data class Start(
                val application: NameAndVersion,
                val parameters: Map<String, Any>
        ) : AppRequest() {
            companion object {
                val description = kafkaDescription<AppRequest.Start> {
                    method = HttpMethod.POST

                    path {
                        using(baseContext)
                        +"jobs"
                    }

                    body {
                        bindEntireRequestFromBody()
                    }
                }
            }

            suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
        }

        data class Cancel(val jobId: Long) : AppRequest() {
            companion object {
                val description = kafkaDescription<AppRequest.Cancel> {
                    method = HttpMethod.DELETE

                    path {
                        using(baseContext)
                        +"jobs"
                        +boundTo(Cancel::jobId)
                    }
                }
            }

            suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
        }

        companion object {
            // TODO This is not a robust solution. Will crash if not lazy
            val descriptions: KafkaCallDescriptionBundle<AppRequest> by lazy {
                listOf(AppRequest.Start.description, AppRequest.Cancel.description)
            }
        }
    }
}

object HPCJobs {

}
