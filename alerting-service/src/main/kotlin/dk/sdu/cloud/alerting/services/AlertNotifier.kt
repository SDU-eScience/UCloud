package dk.sdu.cloud.alerting.services


interface AlertNotifier {
    suspend fun onAlert(alert: Alert)
}
