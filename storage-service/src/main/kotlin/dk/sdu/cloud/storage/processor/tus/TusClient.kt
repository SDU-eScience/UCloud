package dk.sdu.cloud.storage.processor.tus

import io.tus.java.client.TusClient
import io.tus.java.client.TusURLMemoryStore
import io.tus.java.client.TusUpload
import java.io.File
import java.net.URL
import java.util.*

fun main(args: Array<String>) {
    val client = TusClient()
    val urlStore = TusURLMemoryStore()
    val file = File("/tmp/file")
    val upload = TusUpload(file)
    client.enableResuming(urlStore)
    urlStore[upload.fingerprint] = URL("http://localhost:42100/api/tus/74c2dbf9-c444-4d88-9b42-e292eaf204a5")
    //urlStore[upload.fingerprint] = URL("http://localhost:8080/")
    val auth = Base64.getEncoder().encode("rods:rods".toByteArray()).toString(Charsets.UTF_8)
    client.headers = mapOf(
            "Authorization" to "Basic $auth",
            "Job-Id" to "1234"
    )
    val uploader = client.resumeUpload(upload)
    uploader.chunkSize = 1024 * 8
    uploader.requestPayloadSize = file.length().toInt() // WTF????

    val start = System.currentTimeMillis()
    do {
        // Calculate the progress using the total size of the uploading file and
        // the current offset.
        val totalBytes = upload.size
        val bytesUploaded = uploader.offset
        println("Uploaded: $bytesUploaded")
        val progress = bytesUploaded.toDouble() / totalBytes * 100
        //System.out.printf("Upload at %06.2f%%.\n", progress)
    } while (uploader.uploadChunk() > -1)

    println(System.currentTimeMillis() - start)
    uploader.finish()
}