package dk.sdu.cloud.debug

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.*
import kotlinx.serialization.json.JsonNull
import java.io.File

class DebugSystemFeature : MicroFeature {
    private var enabled: Boolean = false
    lateinit var system: DebugSystem

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        enabled = ctx.developmentModeEnabled
        enabled = ctx.configuration.requestChunkAtOrNull<Boolean>("debugEnabled") ?: enabled
        var logLocation = "."
        val potentialDirectories = listOf("/var/log/ucloud/structured", "/tmp", "./")
        for (loc in potentialDirectories) {
            val dir = File(loc)
            if (!dir.exists()) {
                if (dir.mkdirs()) continue
            }

            val success = runCatching {
                File(dir, "test.log")
                    .also { it.writeText("Test") }
                    .also { it.delete() }
            }.isSuccess

            if (!success) continue

            logLocation = loc
            break
        }

        system = DebugSystem(logLocation, serviceDescription.name, enabled = enabled)
        system.start(ctx.backgroundScope)

        if (ctx.featureOrNull(ClientFeature) != null) {
            ctx.client.debugSystem = system
            system.installCommon(ctx.client)
        }

        if (ctx.featureOrNull(ServerFeature) != null) {
            ctx.server.attachFilter(object : IngoingCallFilter.AfterParsing() {
                override fun canUseContext(ctx: IngoingCall): Boolean = true
                override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>, request: Any) {
                    @Suppress("UNCHECKED_CAST")
                    call as CallDescription<Any, Any, Any>

                    system.serverRequest(
                        MessageImportance.THIS_IS_NORMAL,
                        call.fullName,
                        defaultMapper.encodeToJsonElement(call.requestType, request)
                    )
                }
            })

            ctx.server.attachFilter(object : IngoingCallFilter.AfterResponse() {
                override fun canUseContext(ctx: IngoingCall): Boolean = true
                override suspend fun run(
                    context: IngoingCall,
                    call: CallDescription<*, *, *>,
                    request: Any?,
                    result: OutgoingCallResponse<*, *>,
                    responseTimeMs: Long
                ) {
                    @Suppress("UNCHECKED_CAST")
                    call as CallDescription<Any, Any, Any>

                    system.serverResponse(
                        when {
                            responseTimeMs >= 300 || result.statusCode.value in 500..599 ->
                                MessageImportance.THIS_IS_WRONG

                            responseTimeMs >= 150 || result.statusCode.value in 400..499 ->
                                MessageImportance.THIS_IS_ODD

                            else ->
                                MessageImportance.THIS_IS_NORMAL
                        },
                        call.fullName,
                        when (val res = result) {
                            is OutgoingCallResponse.Ok -> {
                                defaultMapper.encodeToJsonElement(call.successType, res.result)
                            }

                            is OutgoingCallResponse.Error -> {
                                if (res.error != null) {
                                    defaultMapper.encodeToJsonElement(call.errorType, res.error)
                                } else {
                                    JsonNull
                                }
                            }

                            else -> JsonNull
                        },
                        result.statusCode,
                        responseTimeMs
                    )
                }
            })
        }
    }

    companion object : MicroFeatureFactory<DebugSystemFeature, Unit>, Loggable {
        override val log = logger()
        override fun create(config: Unit): DebugSystemFeature = DebugSystemFeature()
        override val key = MicroAttributeKey<DebugSystemFeature>("debug-system")
    }
}

