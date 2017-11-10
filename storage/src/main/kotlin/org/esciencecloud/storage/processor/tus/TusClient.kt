package org.esciencecloud.storage.processor.tus

import io.tus.java.client.TusClient
import io.tus.java.client.TusURLMemoryStore
import io.tus.java.client.TusUpload
import java.io.File
import java.net.URL

fun main(args: Array<String>) {
    val client = TusClient()
    val urlStore = TusURLMemoryStore()
    val file = File("/tmp/temp_10GB_file")
    val upload = TusUpload(file)
    client.enableResuming(urlStore)
    urlStore[upload.fingerprint] = URL("http://localhost:42100/api/tus/transfer-0")
    //urlStore[upload.fingerprint] = URL("http://localhost:8080/")

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