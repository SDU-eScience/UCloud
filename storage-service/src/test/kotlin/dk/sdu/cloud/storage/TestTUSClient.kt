package dk.sdu.cloud.storage

import dk.sdu.cloud.client.*
import dk.sdu.cloud.storage.api.TusDescriptions
import dk.sdu.cloud.storage.api.UploadCreationCommand
import dk.sdu.cloud.storage.api.start
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.net.ConnectException

// TODO Write a proper test
fun main(args: Array<String>) {
    val context = object : CloudContext {
        override fun resolveEndpoint(namespace: String): String {
            return "http://localhost:8080"
        }

        override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
            return false
        }
    }
    val token = File("/tmp/token").readText().lines().first()
    val cloud = context.jwtAuth(token)

    val file = File("/tmp/file")
    val testUploadSize = file.length()
    val testUpload = file.inputStream()
    val result = runBlocking {
        TusDescriptions.create.call(
            UploadCreationCommand(
                "file",
                false,
                "user1",
                "/home/jonas@hinchely.dk/",
                testUploadSize
            ),
            cloud
        )
    } as RESTResponse.Ok
    val location = result.response.headers["Location"]!!
    TusDescriptions.uploader(testUpload, location, testUploadSize.toInt(), cloud).start()
}