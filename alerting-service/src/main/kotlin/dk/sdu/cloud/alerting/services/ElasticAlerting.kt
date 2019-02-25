package dk.sdu.cloud.alerting.services

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
            println("Checking red")
            errorRed = true
            println(clusterResponse.status)
            return
        }
        if (clusterResponse.status.toString() == "YELLOW") {
            println("Checking yellow")
            errorYellow = true
            println(clusterResponse.status)
            return
        }
        errorYellow = false
        errorRed = false
        yellow = 0
        red = 0
    }

    fun start(time: Long) {
        while (true) {
            if (!errorRed && !errorYellow) {
                checkClusterStatus()
                Thread.sleep(time)
            }
            else {
                if (errorRed) {
                    if (red < 2) {
                        red++
                        Thread.sleep(time)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        println("SENDING SLACK")
                        Thread.sleep(ONE_MINUTE)
                    }
                }
                if (errorYellow) {
                    if (yellow < 10) {
                        yellow++
                        Thread.sleep(time)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        println("SENDING SLACK")
                        Thread.sleep(ONE_MINUTE)
                    }
                }
            }
        }
    }

}
