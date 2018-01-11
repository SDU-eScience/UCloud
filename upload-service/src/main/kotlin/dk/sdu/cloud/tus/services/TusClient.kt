package dk.sdu.cloud.tus.services

import io.tus.java.client.TusClient
import io.tus.java.client.TusURLMemoryStore
import io.tus.java.client.TusUpload
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*

fun main(args: Array<String>) {
    if (false) {
        val file = File("output.txt")
        DataOutputStream(FileOutputStream(file)).use { os ->
            repeat(10_000) {
                os.writeShort(0xDEAD)
                os.writeShort(it)
            }
        }
        return
    }
    val client = TusClient()
    val urlStore = TusURLMemoryStore()
    val file = File("/Users/dthrane/big_buck_bunny_720p_stereo.ogg")
    if (!file.exists()) throw IllegalStateException()
    val upload = TusUpload(file)
    client.enableResuming(urlStore)
    urlStore[upload.fingerprint] = URL("http://localhost:42400/api/tus/ffd7d174-97d1-42ed-8e2e-bc43416c30d3")

    val token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0Iiwicm9sZSI6IlVTRVIiLCJuYW1lIjoiV" +
            "GVzdCBUZXN0IiwiZW1haWwiOiJ0ZXN0QHRlc3QiLCJpc3MiOiJjbG91ZC5zZHUuZGsiLCJpYXQiOjE1MTQzNjQwNTIsI" +
            "mV4cCI6MTcxNDMzNDA1Mn0.BcdqR49Zsz-77qhmaiqu4X-J0-4PUXnCULBPxFo0sR_oKJFt3o70EsVud_9hEp297-Vnvo" +
            "0jhTuRduMqzhTxkQVHBHtl28hhBskto-8AdjPwdyg_M8RCQXjftbUgerR2gC6shx3StATdXYehIht3DP6tWUqw4GkFSN9" +
            "LgO6lju7rK1W1ed_6a99-EeCF0mhREr-KQ6MFzaKcOqNFP2x6JT8BxWLolxIxEQoM94lQojZNPlYvG41zaUw-fFAXYdYA" +
            "8TJuevTeXlMnVZmRIDNoSBLXyuQMgSl9ip5uV88YlUVr_MKsp3crhyJxak8ZnUK6ND0refi6U-WCvcq3IbmUUH31ourWVmB" +
            "Vnhbdxosxlk-dUdL84g0gG0RwVW76UgeCMZgYLzZYaK2lPfnmYCRWnrXGT1F8XnUN1eqeGW-S0xJooiVcw_Bx48-vIPss5m8" +
            "RDrMyoW5Vm4VtmQ0Xbq-myEDfSmiuxcue4rcseCOh1eV0B5S87XmxTuK10OkjJWfEaGPUFdP1e8Syx889DyqT1OoAaz_V_Ue" +
            "-cdw2HRHK3gwPS5_tEuFPD4Wy3VWDbQzUsUw63_Rv92yKoMZ2pnSs-SRIbGc-yfkGm6TQNKlGxHGziLv2rfPuWlsodq-poVfN" +
            "IOGqI5nXB53hAeW41xxYf4oWuN7G-ebAD99RNi4ZobQ"

    client.headers = mapOf(
            "Authorization" to "Bearer $token",
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
        //val progress = bytesUploaded.toDouble() / totalBytes * 100
        //System.out.printf("Upload at %06.2f%%.\n", progress)
    } while (uploader.uploadChunk() > -1)

    println(System.currentTimeMillis() - start)
    uploader.finish()
}