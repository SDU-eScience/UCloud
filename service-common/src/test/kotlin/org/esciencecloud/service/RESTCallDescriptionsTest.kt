package org.esciencecloud.service

import io.netty.handler.codec.http.HttpMethod
import org.esciencecloud.client.AuthenticatedCloud
import org.esciencecloud.client.RESTDescriptions
import org.esciencecloud.client.bindEntireRequestFromBody

class RESTCallDescriptionsTest {
    // TODO Actually write tests for these things
}

object Descriptions : RESTDescriptions() {
    val baseContext = "/foo"

    // Model
    data class DummyModel(val text: String)

    enum class TestEnum {
        A,
        B,
        C,
        D
    }

    // Query and command objects
    data class FindByNameAndVersion(val name: String, val version: String) {
        companion object {
            val description = callDescription<FindByNameAndVersion, DummyModel, DummyModel> {
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
        val description = callDescription<Unit, List<DummyModel>, List<DummyModel>> {
            path { using(baseContext) }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(Unit).call(cloud)
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
                val application: DummyModel,
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
    }
}