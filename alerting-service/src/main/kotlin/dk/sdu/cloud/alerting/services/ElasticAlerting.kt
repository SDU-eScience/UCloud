package dk.sdu.cloud.alerting.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient

private const val THIRTY_SEC = 30*1000L

class ElasticAlerting(
    private val elastic: RestHighLevelClient,
    private val alertService: AlertingService
) {

    private var errorYellow: Boolean = false
    private var errorRed: Boolean = false
    private var yellow: Int = 0
    private var red: Int = 0
    private var alertSent = false

    private fun checkClusterStatus() {
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
        if (clusterResponse.status.toString() == "GREEN") {
            errorYellow = false
            errorRed = false
            yellow = 0
            red = 0
            if (alertSent) {
                runBlocking {
                    alertService.createAlert(Alert("OK: Elastic is in GREEN again"))
                }
                alertSent = false
            }
        }
    }

    suspend fun start() {
        while (true) {
            if (!errorRed && !errorYellow) {
                checkClusterStatus()
                delay(THIRTY_SEC)
            }
            else {
                if (errorRed) {
                    if (red < 2) {
                        red++
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        if (!alertSent) {
                            alertService.createAlert(Alert("ALERT: Elastic has status RED"))
                            alertSent = true
                        }
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                    }
                }
                if (errorYellow) {
                    if (yellow < 10) {
                        yellow++
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        if (!alertSent) {
                            alertService.createAlert(Alert("ALERT: Elastic has status YELLOW"))
                            alertSent = true
                        }
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                    }
                }
            }
        }
    }

}
