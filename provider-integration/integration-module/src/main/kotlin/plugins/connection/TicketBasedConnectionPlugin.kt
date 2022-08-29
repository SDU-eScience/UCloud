package dk.sdu.cloud.plugins.connection

import dk.sdu.cloud.callBlocking
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.*
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.IntegrationControl
import dk.sdu.cloud.provider.api.IntegrationControlApproveConnectionRequest
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.secureToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject

@Serializable
data class TicketApprovalRequest(
    val ticket: String,
    val localIdentity: String,
)

class TicketBasedConnectionPlugin : ConnectionPlugin {
    override val pluginTitle: String = "Ticket"
    private lateinit var pluginConfig: ConfigSchema.Plugins.Connection.Ticket

    override fun configure(config: ConfigSchema.Plugins.Connection) {
        this.pluginConfig = config as ConfigSchema.Plugins.Connection.Ticket
    }

    override suspend fun PluginContext.initialize() {
        val log = Logger("TicketBasedConnectionPlugin")
        if (config.shouldRunServerCode()) {
            ipcServer.addHandler(
                IpcHandler("connect.approve") { user, jsonRequest ->
                    log.debug("Asked to approve connection!")
                    if (user.uid != 0) {
                        throw RPCException("Only root can use these endpoints", HttpStatusCode.Unauthorized)
                    }

                    val req = runCatching {
                        defaultMapper.decodeFromJsonElement(TicketApprovalRequest.serializer(), jsonRequest.params)
                    }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                    var ucloudId: String? = null
                    dbConnection.withSession { connection ->
                        connection.prepareStatement(
                            //language=SQLite
                            """
                                update ticket_connections
                                set completed_at = datetime()
                                where ticket = :ticket and completed_at is null
                                returning ucloud_id
                            """
                        ).useAndInvoke({ bindString("ticket", req.ticket) }) {
                            ucloudId = it.getString(0)
                        }

                        val capturedId =
                            ucloudId ?: throw RPCException("Invalid ticket supplied", HttpStatusCode.BadRequest)

                        val uid = req.localIdentity.toIntOrNull() ?: throw RPCException(
                            "Invalid UID supplied: '$${req.localIdentity}'",
                            HttpStatusCode.BadRequest
                        )

                        /*
                        // NOTE(Brian): Warn if user does not exist
                        val systemUid = getpwuid(uid.toUInt())?.pointed?.pw_name?.toKStringFromUtf8()

                        if (systemUid == null) {
                            log.warn("User with uid $uid was not found on the system. A mapping between the recently " +
                                    "connected UCloud user and the uid will however still be made.")
                        }
                        // TODO
                         */

                        UserMapping.insertMapping(
                            capturedId,
                            uid,
                            this,
                            pluginConnectionId = null,
                            ctx = connection,
                        )

                        IntegrationControl.approveConnection.callBlocking(
                            IntegrationControlApproveConnectionRequest(capturedId),
                            rpcClient
                        ).orThrow()
                    }

                    JsonObject(emptyMap())
                }
            )
        }

        commandLineInterface?.addHandler(
            CliHandler("connect") { args ->
                val usageMessage = "Usage: ucloud connect approve <ticket> <localIdentity>"
                val ipcClient = ipcClient ?: error("No ipc client")

                when (args.getOrNull(0)) {
                    "approve" -> {
                        val ticket = args.getOrNull(1)
                            ?: throw RPCException(usageMessage, HttpStatusCode.BadRequest)
                        val localId = args.getOrNull(2)
                            ?: throw RPCException(usageMessage, HttpStatusCode.BadRequest)

                        ipcClient.sendRequestBlocking(
                            JsonRpcRequest(
                                "connect.approve",
                                defaultMapper.encodeToJsonElement(
                                    TicketApprovalRequest.serializer(),
                                    TicketApprovalRequest(ticket, localId)
                                ) as JsonObject
                            )
                        ).orThrow(Unit.serializer())
                    }

                    else -> {
                        throw RPCException("Unknown command!\n$usageMessage", HttpStatusCode.BadRequest)
                    }
                }
            }
        )
    }

    override suspend fun RequestContext.initiateConnection(username: String): ConnectionResponse {
        val ticket = secureToken(64)
        dbConnection.withSession { connection ->
            connection.prepareStatement(
                //language=SQLite
                """
                    insert into ticket_connections (ticket, ucloud_id, created_at, completed_at)
                    values (:ticket, :ucloud_id, datetime(), null)
                """
            ).useAndInvokeAndDiscard {
                bindString("ticket", ticket)
                bindString("ucloud_id", username)
            }
        }
        return ConnectionResponse.ShowInstructions(mapOf("ticket" to listOf(ticket)))
    }

    override suspend fun PluginContext.requireMessageSigning(): Boolean = false

    override suspend fun RequestContext.showInstructions(query: Map<String, List<String>>): HTML {
        val ticket = query["ticket"]?.firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        var ucloudUsername: String? = null
        dbConnection.withSession { connection ->
            connection.prepareStatement(
                //language=SQLite
                """
                    select ucloud_id
                    from ticket_connections
                    where ticket = :ticket and completed_at is null
                """
            ).useAndInvoke(
                prepare = { bindString("ticket", ticket) },
                readRow = { ucloudUsername = it.getString(0)!! }
            )
        }

        val username = ucloudUsername ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return HTML(
            //language=HTML
            """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                     <meta charset="UTF-8">
                     <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
                     <meta http-equiv="X-UA-Compatible" content="ie=edge">
                     <title>Connect ${HTML.escape(config.core.providerId)} to UCloud</title>
                     <link rel="preconnect" href="https://fonts.gstatic.com">
                     <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;700&family=JetBrains+Mono&display=swap" rel="stylesheet"> 
                     <style>
                        body {
                            font-family: 'IBM Plex Sans', sans-serif;
                            font-size: 16px;
                            margin: 2em auto;
                            padding: 1em;
                            max-width: 1000px;
                            line-height: 1.5;
                            color: #454545;
                        }
                        
                        a, a:visited {
                            color: #0077aa;
                        }
                        
                        .box {
                            border: 2px rgb(102, 102, 102) dashed;
                            padding: 16px;
                            border-radius: 5px;
                            font-family: 'JetBrains Mono', monospace;
                        }
                     </style>
                </head>
                <body>
                    <h1>Connect your UCloud user to ${HTML.escape(config.core.providerId)}</h1>
                    
                    <p>
                        Hi <b>${HTML.escape(username)}</b>!
                        
                        You are currently connecting or re-authenticating your UCloud user with
                        <b>${HTML.escape(config.core.providerId)}</b>. Please follow the instructions below to continue.
                    </p>
                    
                    <h2>1. New User</h2>
                    <p>
                        If you have never used <b>${HTML.escape(config.core.providerId)}</b> before then we kindly ask
                        you to send an email to <b>foo@bar</b> and include the following snippet in your email:
                    </p>
                    
                    <div class='box'>
                        ${HTML.escape(ticket)}
                    </div>
                    
                    <h2>2. Existing user</h2>
                    <p>
                        If you have completed this step before for <b>${HTML.escape(config.core.providerId)}</b> then
                        simply connect to the system using SSH and run the following command:
                    </p>
                    
                    <div class='box'>
                        ucloud connect approve ${HTML.escape(ticket)}
                    </div>
                </body>
                </html>
            """.trimIndent()
        )
    }
}
