package dk.sdu.cloud.plugins

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.secureToken
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

class TicketBasedConnectionPlugin : ConnectionPlugin {
    override fun initialize(configuration: JsonElement) {

    }

    override fun PluginContext.initiateConnection(username: String): ConnectionResponse {
        val connection = dbConnection ?: error("Server mode required for TicketBasedConnectionPlugin")
        val ticket = secureToken(64)
        connection.withTransaction {
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

    override fun PluginContext.showInstructions(query: Map<String, List<String>>): HTML {
        val connection = dbConnection ?: error("Server mode required for TicketBasedConnectionPlugin")
        val ticket = query["ticket"]?.firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        var ucloudUsername: String? = null
        connection.withTransaction {
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
                        ucloud connect ${HTML.escape(ticket)}
                    </div>
                </body>
                </html>
            """.trimIndent()
        )
    }
}
