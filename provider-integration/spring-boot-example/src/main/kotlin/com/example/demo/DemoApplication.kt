package com.example.demo

import JobsController
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.RetrieveAllFromProviderRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.providers.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.*
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

const val PROVIDER_ID = "mytestprovider"

@SpringBootApplication
@ComponentScan("com.example.demo", "dk.sdu.cloud.providers")
class DemoApplication {
    @Bean
    fun client(
        @Value("\${ucloud.refreshToken}")
        refreshToken: String,

        @Value("\${ucloud.host}")
        host: String,

        @Value("\${ucloud.tls:false}")
        tls: Boolean,

        @Value("\${ucloud.port:#{null}}")
        port: Int?,
    ) = UCloudClient(refreshToken, host, tls, port)
}

@Configuration
@RestController
class SimpleCompute(
    private val client: UCloudClient,
    wsDispatcher: UCloudWsDispatcher,
) : JobsController(PROVIDER_ID, wsDispatcher) {
    override fun create(request: BulkRequest<Job>) {
        log.info("Creating some jobs: $request")
        threadPool.execute {
            Thread.sleep(5000) // TODO Status is not set if we change this too quickly (https://github.com/SDU-eScience/UCloud/issues/2303)
            JobsControl.update.call(
                bulkRequestOf(request.items.map { job ->
                    JobsControlUpdateRequestItem(
                        job.id,
                        JobState.RUNNING,
                        "We are now running!"
                    )
                }),
                client
            ).orThrow()
        }
    }

    override fun delete(request: BulkRequest<Job>) {
        log.info("Deleting some jobs: $request")
        JobsControl.update.call(
            bulkRequestOf(request.items.map { job ->
                JobsControlUpdateRequestItem(
                    job.id,
                    JobState.SUCCESS,
                    "We are no longer running!"
                )
            }),
            client
        ).orThrow()
    }

    override fun extend(request: BulkRequest<JobsProviderExtendRequestItem>) {
        log.info("Extending some jobs: $request")
        JobsControl.update.call(
            bulkRequestOf(request.items.map { requestItem ->
                JobsControlUpdateRequestItem(
                    requestItem.job.id,
                    status = "We have extended your requestItem with ${requestItem.requestedTime}"
                )
            }),
            client
        ).orThrow()
    }

    private val threadPool = Executors.newCachedThreadPool()
    private val tasks = HashMap<String, AtomicBoolean>()

    override fun follow(
        request: JobsProviderFollowRequest,
        wsContext: UCloudWsContext<JobsProviderFollowRequest, JobsProviderFollowResponse, CommonErrorMessage>,
    ) {
        when (request) {
            is JobsProviderFollowRequest.Init -> {
                val isRunning = AtomicBoolean(true)
                val streamId = UUID.randomUUID().toString()
                synchronized(tasks) {
                    tasks[streamId] = isRunning
                }

                threadPool.execute {
                    wsContext.sendMessage(JobsProviderFollowResponse(streamId, -1))

                    var counter = 0
                    while (isRunning.get() && wsContext.session.isOpen) {
                        wsContext.sendMessage(JobsProviderFollowResponse(streamId, 0, "Hello, World! $counter\n"))
                        counter++
                        Thread.sleep(1000)
                    }

                    wsContext.sendResponse(JobsProviderFollowResponse("", -1), 200)
                }
            }
            is JobsProviderFollowRequest.CancelStream -> {
                synchronized(tasks) {
                    tasks.remove(request.streamId)?.set(false)
                    wsContext.sendResponse(JobsProviderFollowResponse("", -1), 200)
                }
            }
        }
    }

    override fun openInteractiveSession(
        request: BulkRequest<JobsProviderOpenInteractiveSessionRequestItem>,
    ): JobsProviderOpenInteractiveSessionResponse {
        log.info("open interactive session $request")
        TODO()
    }

    private val knownProducts = listOf(
        ProductReference("test1", "test", PROVIDER_ID),
        ProductReference("test2", "test", PROVIDER_ID),
        ProductReference("test3", "test", PROVIDER_ID),
    )

    override fun retrieveProducts(request: Unit): JobsProviderRetrieveProductsResponse {
        log.info("Retrieving products")
        return JobsProviderRetrieveProductsResponse(
            knownProducts.map { productRef ->
                ComputeProductSupport(
                    productRef,
                    ComputeSupport(
                        ComputeSupport.Docker(
                            enabled = true,
                            terminal = true,
                            logs = true,
                            timeExtension = false
                        ),
                        ComputeSupport.VirtualMachine(
                            enabled = false,
                        )
                    )
                )
            }
        )
    }

    override fun retrieveUtilization(request: Unit): JobsProviderUtilizationResponse {
        log.info("Retrieving utilization")
        return JobsProviderUtilizationResponse(CpuAndMemory(0.0, 0L), CpuAndMemory(0.0, 0L), QueueStatus(0, 0))
    }

    override fun suspend(request: BulkRequest<Job>) {
        log.info("suspend jobs: $request")
    }

    override fun verify(request: BulkRequest<Job>) {
        log.info("verify jobs: $request")
    }

    companion object : Loggable {
        override val log = logger()
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
