package dk.sdu.cloud.extract.data.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joda.time.DateTimeZone
import org.joda.time.Days
import org.joda.time.LocalDateTime
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class PrometheusQueryResponseDataResult(
    val metric: @Contextual JsonNode,
    val value: List<@Contextual Any>
)
@Serializable
data class PrometheusQueryResponseData(
    val resultType: String,
    val result: List<PrometheusQueryResponseDataResult>
)
@Serializable
data class PrometheusQueryResponse(
    val status: String,
    val data: PrometheusQueryResponseData
)

//Requires local access or VPN active
fun networkUsage(startDate: LocalDateTime, endDate: LocalDateTime): Double {
    val days = Days.daysBetween(startDate, endDate).days
    val endTime = endDate.toDateTime(DateTimeZone.UTC).millis / 1000
    val url = URL("http://172.16.2.1:9090/api/v1/query?query=sum%28increase%28node_network_transmit_bytes_total%7Bdevice%3D%22bond0.200%22%7D%5B${days}d%5D%29%2Bincrease%28node_network_receive_bytes_total%7Bdevice%3D%22bond0.200%22%7D%5B${days}d%5D%29%29+%2F+1000+%2F+1000&time=${endTime}")
    return with(url.openConnection() as HttpURLConnection) {
        requestMethod = "GET"  // optional default is GET

        if(responseCode == 200) {
            var info: Double? = null
            inputStream.bufferedReader().use {
                it.lines().forEach { line ->
                    val result = try {
                        defaultMapper.decodeFromString(PrometheusQueryResponse.serializer(), line)
                    } catch (ex: Exception) {
                        return@forEach
                    }
                    info = result.data.result.first().value[1].toString().toDouble()
                }
            }
            return if (info != null) {
                info!!
            } else {
                println("No info found")
                0.0
            }
        }
        else {
            println("Did not get 200 on network request")
            0.0
        }
    }
}
