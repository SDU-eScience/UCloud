package org.esciencecloud.abc.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.netty.handler.codec.http.HttpMethod
import org.esciencecloud.client.*
import org.esciencecloud.service.KafkaRequest

object HPCApplicationDescriptions : RESTDescriptions() {
    val baseContext = "/api/hpc/apps/"

    init {
        register(FindAllByName.description)
        register(FindByNameAndVersion.description)
        register(ListAll.description)
        register(AppRequest.Start.description)
        register(AppRequest.Cancel.description)
    }

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

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = KafkaRequest.TYPE_PROPERTY)
    @JsonSubTypes(
            JsonSubTypes.Type(value = AppRequest.Start::class, name = "start"),
            JsonSubTypes.Type(value = AppRequest.Cancel::class, name = "cancel"))
    sealed class AppRequest {
        data class Start(val application: NameAndVersion, val parameters: Map<String, Any>) : AppRequest() {
            companion object {
                val description = kafkaDescription<Start> {
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
                val description = kafkaDescription<Cancel> {
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
            val descriptions: KafkaCallDescriptionBundle<AppRequest> by lazy {
                listOf(Start.description, Cancel.description)
            }
        }
    }
}
