package dk.sdu.cloud.alerting.services

import kotlinx.coroutines.delay
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import java.lang.IllegalArgumentException

private const val ONE_MINUTE = 1*60*1000L

class ElasticAlerting(
    private val elastic: RestHighLevelClient
) {

    private var errorYellow: Boolean = false
    private var errorRed: Boolean = false
    private var yellow: Int = 0
    private var red: Int = 0

    fun checkClusterStatus() {
        val clusterResponse = elastic.cluster().health(ClusterHealthRequest(), RequestOptions.DEFAULT)
        println("errorRed: $errorRed, errorYellow: $errorYellow, red = $red, yellow = $yellow")
        if (clusterResponse.status.toString() == "RED") {
            errorRed = true
            return
        }
        if (clusterResponse.status.toString() == "YELLOW") {
            errorYellow = true
            return
        }
        errorYellow = false
        errorRed = false
        yellow = 0
        red = 0
    }

    suspend fun start(time: Long) {
        while (true) {
            if (!errorRed && !errorYellow) {
                checkClusterStatus()
                delay(time)
            }
            else {
                if (errorRed) {
                    if (red < 2) {
                        red++
                        delay(time)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        println("SENDING SLACK")
                        delay(ONE_MINUTE)
                        checkClusterStatus()
                    }
                }
                if (errorYellow) {
                    if (yellow < 10) {
                        yellow++
                        delay(time)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        println("SENDING SLACK")
                        delay(ONE_MINUTE)
                        checkClusterStatus()
                    }
                }
            }
        }
    }

}
