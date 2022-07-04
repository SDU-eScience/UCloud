package dk.sdu.cloud.ipc

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.defaultMapper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

fun main(args: Array<String>) {
    if (args.firstOrNull() == "client") {
        runBlocking {
            val client = IpcClient("./ipc")
            client.connect()
            println(client.sendRequest(JsonRpcRequest("test", JsonObject(emptyMap()))))
            println(client.sendRequest(JsonRpcRequest("fie", JsonObject(emptyMap()))))
        }
    } else {
        File("./ipc").mkdir()
        val fakeClient = RpcClient()
        val authenticatedClient = AuthenticatedClient(fakeClient, OutgoingHttpCall, {}, {})
        val ipcServer = IpcServer("./ipc", null, authenticatedClient, RpcServer())
        ipcServer.addHandler(IpcHandler("test") { user, request ->
            println(user)
            println(request)
            JsonObject(mapOf("fie" to JsonPrimitive("hund")))
        })
        ipcServer.addHandler(IpcHandler("fie") { user, request ->
            println(user)
            println(request)
            JsonObject(mapOf("fie2" to JsonPrimitive("jpg")))
        })
        ipcServer.runServer()
    }
}